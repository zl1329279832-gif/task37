package io.github.xxyopen.novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.xxyopen.novel.core.common.constant.ErrorCodeEnum;
import io.github.xxyopen.novel.core.common.exception.BusinessException;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.config.FinanceProperties;
import io.github.xxyopen.novel.core.constant.DatabaseConsts;
import io.github.xxyopen.novel.dao.entity.*;
import io.github.xxyopen.novel.dao.mapper.*;
import io.github.xxyopen.novel.dto.resp.*;
import io.github.xxyopen.novel.manager.cache.BookChapterCacheManager;
import io.github.xxyopen.novel.manager.cache.BookContentCacheManager;
import io.github.xxyopen.novel.manager.cache.BookInfoCacheManager;
import io.github.xxyopen.novel.manager.cache.MemberInfoCacheManager;
import io.github.xxyopen.novel.manager.redis.RedisDistributedLockManager;
import io.github.xxyopen.novel.service.ChapterPurchaseService;
import io.github.xxyopen.novel.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 章节购买 服务实现类
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChapterPurchaseServiceImpl implements ChapterPurchaseService {

    private final BookChapterMapper bookChapterMapper;

    private final BookInfoMapper bookInfoMapper;

    private final BookContentMapper bookContentMapper;

    private final UserInfoMapper userInfoMapper;

    private final UserConsumeLogMapper userConsumeLogMapper;

    private final AuthorIncomeDetailMapper authorIncomeDetailMapper;

    private final AuthorIncomeMapper authorIncomeMapper;

    private final AuthorInfoMapper authorInfoMapper;

    private final BookChapterCacheManager bookChapterCacheManager;

    private final BookInfoCacheManager bookInfoCacheManager;

    private final BookContentCacheManager bookContentCacheManager;

    private final RedisDistributedLockManager lockManager;

    private final FinanceProperties financeProperties;

    // === 新增依赖 ===
    private final MemberInfoCacheManager memberInfoCacheManager;
    private final MemberInfoMapper memberInfoMapper;
    private final ReadingCouponMapper readingCouponMapper;
    private final MemberBenefitsSnapshotMapper memberBenefitsSnapshotMapper;
    private final SettlementService settlementService;
    private final RefundFreezeMapper refundFreezeMapper;

    /**
     * VIP 章节内容预览字数
     */
    private static final int PREVIEW_WORD_COUNT = 200;

    @Override
    public RestResp<BookContentAboutRespDto> purchaseChapter(Long userId, Long chapterId) {
        // 向后兼容：委托给增强版（不使用阅读券）
        return purchaseChapterWithCoupon(userId, chapterId, null);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RestResp<BookContentAboutRespDto> purchaseChapterWithCoupon(
            Long userId, Long chapterId, Long couponId) {

        // === Phase 1: 锁外预检 ===

        // 1. 校验章节存在
        BookChapter bookChapter = bookChapterMapper.selectById(chapterId);
        if (bookChapter == null) {
            throw new BusinessException(ErrorCodeEnum.USER_CHAPTER_NOT_EXIST);
        }

        // 2. 校验是否 VIP 章节
        if (!Objects.equals(bookChapter.getIsVip(), 1)) {
            return buildFullContent(bookChapter);
        }

        // 3. 检查限免状态
        if (isFreeLimit(bookChapter)) {
            return buildFullContent(bookChapter);
        }

        // 4. 检查是否已购买（幂等）
        if (hasPurchased(userId, chapterId)) {
            throw new BusinessException(ErrorCodeEnum.USER_CHAPTER_ALREADY_PURCHASED);
        }

        // 5. 防止作者自购
        BookInfo bookInfoEntity = bookInfoMapper.selectById(bookChapter.getBookId());
        if (bookInfoEntity == null) {
            throw new BusinessException(ErrorCodeEnum.USER_BOOK_NOT_EXIST);
        }
        preventSelfPurchase(userId, bookInfoEntity.getAuthorId());

        // 6. 确定原始价格
        int originalPrice = determineChapterPrice(bookChapter);

        // 7. 检查会员权益
        MemberInfo memberInfo = memberInfoCacheManager.getActiveMemberInfo(userId);
        boolean useMemberFreeRead = false;
        int discountRate = 100;
        int discountedPrice = originalPrice;

        if (memberInfo != null) {
            // 检查是否可以免费阅读
            if (memberInfo.getUsedFreeRead() < memberInfo.getFreeReadQuota()) {
                useMemberFreeRead = true;
            } else {
                // 使用会员折扣
                discountRate = memberInfo.getDiscountRate();
                discountedPrice = originalPrice * discountRate / 100;
            }
        }

        // 8. 检查阅读券
        ReadingCoupon coupon = null;
        int couponAmount = 0;
        if (couponId != null) {
            coupon = validateCoupon(couponId, userId, discountedPrice);
            couponAmount = coupon.getDiscountAmount();
        }

        // 9. 计算最终价格
        int finalPrice;
        if (useMemberFreeRead) {
            finalPrice = 0;
        } else {
            finalPrice = Math.max(0, discountedPrice - couponAmount);
        }

        // === Phase 2: 分布式锁 ===
        String lockKey = String.format("purchase:lock:%d:%d", userId, chapterId);
        String lockValue = UUID.randomUUID().toString();
        try {
            if (!lockManager.tryLock(lockKey, lockValue, 10)) {
                throw new BusinessException(ErrorCodeEnum.SYSTEM_PURCHASE_LOCK_FAILED);
            }

            // === Phase 3: 锁内双重检查 ===
            BookChapter freshChapter = bookChapterMapper.selectById(chapterId);
            if (freshChapter != null && isFreeLimit(freshChapter)) {
                return buildFullContent(freshChapter);
            }

            if (hasPurchased(userId, chapterId)) {
                throw new BusinessException(ErrorCodeEnum.USER_CHAPTER_ALREADY_PURCHASED);
            }

            // 重新从DB读取会员信息（防止缓存过期）
            MemberInfo freshMember = memberInfoMapper.selectActiveByUserId(userId);
            boolean freshUseMemberFreeRead = false;
            int freshDiscountRate = 100;
            int freshDiscountedPrice = originalPrice;

            if (freshMember != null) {
                if (freshMember.getUsedFreeRead() < freshMember.getFreeReadQuota()) {
                    freshUseMemberFreeRead = true;
                } else {
                    freshDiscountRate = freshMember.getDiscountRate();
                    freshDiscountedPrice = originalPrice * freshDiscountRate / 100;
                }
            }

            // 重新验证阅读券
            int freshCouponAmount = 0;
            ReadingCoupon freshCoupon = null;
            if (couponId != null) {
                freshCoupon = readingCouponMapper.selectById(couponId);
                if (freshCoupon != null && Objects.equals(freshCoupon.getUseStatus(), 0)
                        && freshCoupon.getExpireTime().isAfter(LocalDateTime.now())
                        && freshDiscountedPrice >= freshCoupon.getMinPurchaseAmount()) {
                    freshCouponAmount = freshCoupon.getDiscountAmount();
                } else {
                    freshCoupon = null;
                    freshCouponAmount = 0;
                }
            }

            int freshFinalPrice;
            if (freshUseMemberFreeRead) {
                freshFinalPrice = 0;
            } else {
                freshFinalPrice = Math.max(0, freshDiscountedPrice - freshCouponAmount);
            }

            LocalDateTime now = LocalDateTime.now();

            // === Phase 4: 分支执行 ===

            if (freshUseMemberFreeRead) {
                // === Branch A: 会员免费阅读 ===
                int quotaResult = memberInfoMapper.deductFreeReadQuota(userId);
                if (quotaResult == 0) {
                    // 配额在锁等待期间耗尽，回退到折扣购买
                    freshUseMemberFreeRead = false;
                    // 重新计算折扣价（免费读路径未计算折扣）
                    if (freshMember != null) {
                        freshDiscountRate = freshMember.getDiscountRate();
                        freshDiscountedPrice = originalPrice * freshDiscountRate / 100;
                    }
                    freshFinalPrice = Math.max(0, freshDiscountedPrice - freshCouponAmount);
                    // 继续到 Branch B
                } else {
                    // 免费读成功
                    executeMemberFreeRead(userId, bookInfoEntity, bookChapter, freshMember,
                            originalPrice, now);
                    memberInfoCacheManager.evictMemberInfo(userId);
                    log.info("用户 {} 会员免费阅读章节 {} ", userId, chapterId);
                    return buildFullContentWithMemberFlag(bookChapter);
                }
            }

            // === Branch B: 标准/折扣购买 ===
            // 检查余额
            UserInfo userInfo = userInfoMapper.selectById(userId);
            if (userInfo.getAccountBalance() == null || userInfo.getAccountBalance() < freshFinalPrice) {
                throw new BusinessException(ErrorCodeEnum.USER_BALANCE_INSUFFICIENT);
            }

            // 原子扣减余额
            if (freshFinalPrice > 0) {
                int affected = userInfoMapper.deductBalance(userId, freshFinalPrice);
                if (affected == 0) {
                    throw new BusinessException(ErrorCodeEnum.USER_BALANCE_INSUFFICIENT);
                }
            }

            // 使用阅读券（CAS）
            if (freshCoupon != null) {
                int couponResult = readingCouponMapper.casUseCoupon(freshCoupon.getId(), 0L);
                if (couponResult == 0) {
                    // 阅读券已被使用，重新计算不含券价格
                    freshCouponAmount = 0;
                    freshCoupon = null;
                    freshFinalPrice = freshDiscountedPrice;
                    if (freshFinalPrice > 0) {
                        int reAffected = userInfoMapper.deductBalance(userId, freshFinalPrice);
                        if (reAffected == 0) {
                            throw new BusinessException(ErrorCodeEnum.USER_BALANCE_INSUFFICIENT);
                        }
                    }
                }
            }

            // 创建消费记录
            UserConsumeLog consumeLog = new UserConsumeLog();
            consumeLog.setUserId(userId);
            consumeLog.setAuthorId(bookInfoEntity.getAuthorId());
            consumeLog.setAmount(freshFinalPrice);
            consumeLog.setProductType(0); // 0 = VIP章节
            consumeLog.setProductId(chapterId);
            consumeLog.setProducName(bookChapter.getChapterName());
            consumeLog.setProducValue(1);
            consumeLog.setRefundStatus(0);
            consumeLog.setCreateTime(now);
            consumeLog.setUpdateTime(now);
            userConsumeLogMapper.insert(consumeLog);

            // 更新阅读券关联的消费记录ID
            if (freshCoupon != null) {
                readingCouponMapper.casUseCoupon(freshCoupon.getId(), consumeLog.getId());
            }

            // 创建待结算记录（替代立即累计作者收入）
            int discountAmount = originalPrice - freshDiscountedPrice;
            settlementService.createPendingSettlement(
                    userId, bookInfoEntity.getAuthorId(),
                    bookChapter.getBookId(), chapterId,
                    consumeLog.getId(), 0,
                    originalPrice, discountAmount,
                    freshCouponAmount, freshFinalPrice);

            // 保存会员权益快照
            if (freshMember != null && freshDiscountRate < 100) {
                saveBenefitsSnapshot(userId, freshMember, originalPrice,
                        freshFinalPrice, 1, freshDiscountRate, chapterId,
                        freshMember.getExpireTime(), now);
            }

            log.info("用户 {} 购买章节 {} 成功，消费 {} 屋币（原价={}, 折扣={}, 券={}）",
                    userId, chapterId, freshFinalPrice, originalPrice,
                    discountAmount, freshCouponAmount);

            return buildFullContent(bookChapter);

        } finally {
            lockManager.releaseLock(lockKey, lockValue);
        }
    }

    @Override
    public RestResp<BookContentAboutRespDto> getChapterContentWithAccessControl(Long userId, Long chapterId) {
        // 1. 查询章节信息
        BookChapter bookChapter = bookChapterMapper.selectById(chapterId);
        if (bookChapter == null) {
            throw new BusinessException(ErrorCodeEnum.USER_CHAPTER_NOT_EXIST);
        }

        // 2. 判断是否 VIP 章节
        if (!Objects.equals(bookChapter.getIsVip(), 1)) {
            return buildFullContent(bookChapter);
        }

        // 3. 检查限免
        if (isFreeLimit(bookChapter)) {
            return buildFullContentWithFreeLimitFlag(bookChapter);
        }

        // 4. 检查是否已购买
        if (userId != null && hasPurchased(userId, chapterId)) {
            return buildFullContentWithPurchasedFlag(bookChapter);
        }

        // 5. 检查会员免费阅读权限
        if (userId != null) {
            MemberInfo member = memberInfoCacheManager.getActiveMemberInfo(userId);
            if (member != null && member.getUsedFreeRead() < member.getFreeReadQuota()) {
                return buildFullContentWithMemberFlag(bookChapter);
            }
        }

        // 6. 未购买 — 返回预览 + 购买信息
        return buildPreviewContent(bookChapter, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RestResp<Void> refund(Long userId, Long consumeLogId) {
        // 1. 查找消费记录
        UserConsumeLog consumeLog = userConsumeLogMapper.selectById(consumeLogId);
        if (consumeLog == null || !Objects.equals(consumeLog.getUserId(), userId)) {
            throw new BusinessException(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
        }

        // 2. 快速预检是否已退款
        if (Objects.equals(consumeLog.getRefundStatus(), 1)) {
            throw new BusinessException(ErrorCodeEnum.USER_REFUND_NOT_ALLOWED);
        }

        // 3. 检查退款冻结
        RefundFreeze freeze = refundFreezeMapper.selectByConsumeLogId(consumeLogId);
        if (freeze != null && Objects.equals(freeze.getStatus(), 0)
                && freeze.getFreezeEndTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCodeEnum.REFUND_FROZEN);
        }

        // 4. 如果已有月度结算，检查是否已确认
        BookChapter bookChapter = bookChapterMapper.selectById(consumeLog.getProductId());
        LocalDate consumeDate = consumeLog.getCreateTime().toLocalDate();
        LocalDate incomeMonth = consumeDate.withDayOfMonth(1);

        if (bookChapter != null && consumeLog.getAuthorId() != null) {
            Integer confirmStatus = authorIncomeMapper.getConfirmStatus(
                    consumeLog.getAuthorId(), bookChapter.getBookId(), incomeMonth);
            if (confirmStatus != null && confirmStatus == 1) {
                throw new BusinessException(ErrorCodeEnum.USER_SETTLEMENT_CONFIRMED);
            }
        }

        // 5. CAS 原子更新退款状态
        int affected = userConsumeLogMapper.casSetRefunded(consumeLogId);
        if (affected == 0) {
            throw new BusinessException(ErrorCodeEnum.USER_REFUND_NOT_ALLOWED);
        }

        // 6. 恢复用户余额
        userInfoMapper.restoreBalance(userId, consumeLog.getAmount());

        // 7. 取消待结算记录
        settlementService.cancelPendingSettlement(consumeLogId);

        // 8. 恢复阅读券
        QueryWrapper<ReadingCoupon> couponQw = new QueryWrapper<>();
        couponQw.eq("consume_log_id", consumeLogId).eq("use_status", 1);
        ReadingCoupon usedCoupon = readingCouponMapper.selectOne(couponQw);
        if (usedCoupon != null) {
            readingCouponMapper.restoreCoupon(usedCoupon.getId());
        }

        // 9. 扣减作者日收入（使用消费记录的日期）
        if (bookChapter != null && consumeLog.getAuthorId() != null) {
            int remainingPurchases = authorIncomeDetailMapper.countUserPurchasesToday(
                    consumeLog.getAuthorId(), bookChapter.getBookId(), userId, consumeDate);
            int numberDecrement = (remainingPurchases <= 1) ? 1 : 0;

            authorIncomeDetailMapper.deductDailyIncome(
                    consumeLog.getAuthorId(),
                    bookChapter.getBookId(),
                    consumeDate,
                    consumeLog.getAmount(),
                    numberDecrement
            );

            // 10. 如果已存在月度结算记录（未确认），同步回滚结算金额
            int afterTaxDeduct = consumeLog.getAmount()
                    * (100 - financeProperties.getTaxRate()) / 100;
            authorIncomeMapper.deductSettlement(
                    consumeLog.getAuthorId(),
                    bookChapter.getBookId(),
                    incomeMonth,
                    consumeLog.getAmount(),
                    afterTaxDeduct
            );
        }

        log.info("用户 {} 退款成功，消费记录ID {}，退还 {} 屋币", userId, consumeLogId, consumeLog.getAmount());
        return RestResp.ok();
    }

    @Override
    public RestResp<List<UserConsumeLogRespDto>> listConsumeLogs(Long userId, Long bookId) {
        QueryWrapper<UserConsumeLog> qw = new QueryWrapper<>();
        qw.eq(DatabaseConsts.UserConsumeLogTable.COLUMN_USER_ID, userId);
        if (bookId != null && bookId > 0) {
            QueryWrapper<BookChapter> chapterQw = new QueryWrapper<>();
            chapterQw.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId);
            List<Long> chapterIds = bookChapterMapper.selectList(chapterQw)
                    .stream().map(BookChapter::getId).toList();
            if (!chapterIds.isEmpty()) {
                qw.in(DatabaseConsts.UserConsumeLogTable.COLUMN_PRODUCT_ID, chapterIds);
            }
        }
        qw.orderByDesc(DatabaseConsts.CommonColumnEnum.CREATE_TIME.getName());
        List<UserConsumeLog> logs = userConsumeLogMapper.selectList(qw);
        List<UserConsumeLogRespDto> respList = logs.stream().map(v -> UserConsumeLogRespDto.builder()
                .id(v.getId())
                .productId(v.getProductId())
                .producName(v.getProducName())
                .amount(v.getAmount())
                .refundStatus(v.getRefundStatus())
                .createTime(v.getCreateTime())
                .build()).toList();
        return RestResp.ok(respList);
    }

    // ======================== 私有辅助方法 ========================

    private boolean isFreeLimit(BookChapter bookChapter) {
        if (Objects.equals(bookChapter.getIsFreeLimit(), 1)) {
            return true;
        }
        BookInfo bookInfo = bookInfoMapper.selectById(bookChapter.getBookId());
        return bookInfo != null && Objects.equals(bookInfo.getIsFreeLimit(), 1);
    }

    private boolean hasPurchased(Long userId, Long chapterId) {
        QueryWrapper<UserConsumeLog> qw = new QueryWrapper<>();
        qw.eq(DatabaseConsts.UserConsumeLogTable.COLUMN_USER_ID, userId)
                .eq(DatabaseConsts.UserConsumeLogTable.COLUMN_PRODUCT_ID, chapterId)
                .eq(DatabaseConsts.UserConsumeLogTable.COLUMN_PRODUCT_TYPE, 0)
                .eq(DatabaseConsts.UserConsumeLogTable.COLUMN_REFUND_STATUS, 0);
        return userConsumeLogMapper.selectCount(qw) > 0;
    }

    private void preventSelfPurchase(Long userId, Long authorId) {
        if (authorId == null) {
            return;
        }
        QueryWrapper<AuthorInfo> qw = new QueryWrapper<>();
        qw.eq(DatabaseConsts.AuthorInfoTable.COLUMN_USER_ID, userId);
        AuthorInfo authorInfo = authorInfoMapper.selectOne(qw);
        if (authorInfo != null && Objects.equals(authorInfo.getId(), authorId)) {
            throw new BusinessException(ErrorCodeEnum.USER_PURCHASE_OWN_CHAPTER);
        }
    }

    private int determineChapterPrice(BookChapter bookChapter) {
        return (bookChapter.getChapterPrice() != null && bookChapter.getChapterPrice() > 0)
                ? bookChapter.getChapterPrice()
                : financeProperties.getDefaultChapterPrice();
    }

    private ReadingCoupon validateCoupon(Long couponId, Long userId, int purchaseAmount) {
        ReadingCoupon coupon = readingCouponMapper.selectById(couponId);
        if (coupon == null || !Objects.equals(coupon.getUserId(), userId)
                || !Objects.equals(coupon.getUseStatus(), 0)) {
            throw new BusinessException(ErrorCodeEnum.COUPON_NOT_AVAILABLE);
        }
        if (coupon.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCodeEnum.COUPON_EXPIRED);
        }
        if (purchaseAmount < coupon.getMinPurchaseAmount()) {
            throw new BusinessException(ErrorCodeEnum.COUPON_MIN_PURCHASE_NOT_MET);
        }
        return coupon;
    }

    private void executeMemberFreeRead(Long userId, BookInfo bookInfoEntity,
                                        BookChapter bookChapter, MemberInfo memberInfo,
                                        int originalPrice, LocalDateTime now) {
        // 创建消费记录（金额为0）
        UserConsumeLog consumeLog = new UserConsumeLog();
        consumeLog.setUserId(userId);
        consumeLog.setAuthorId(bookInfoEntity.getAuthorId());
        consumeLog.setAmount(0);
        consumeLog.setProductType(0);
        consumeLog.setProductId(bookChapter.getId());
        consumeLog.setProducName(bookChapter.getChapterName());
        consumeLog.setProducValue(1);
        consumeLog.setRefundStatus(0);
        consumeLog.setCreateTime(now);
        consumeLog.setUpdateTime(now);
        userConsumeLogMapper.insert(consumeLog);

        // 保存权益快照
        saveBenefitsSnapshot(userId, memberInfo, originalPrice, 0,
                0, memberInfo.getFreeReadQuota(), bookChapter.getId(),
                memberInfo.getExpireTime(), now);

        // 创建待结算（会员免费读，平台补贴）
        int platformSubsidy = originalPrice * financeProperties.getMemberSubsidyRate() / 100;
        settlementService.createPendingSettlement(
                userId, bookInfoEntity.getAuthorId(),
                bookChapter.getBookId(), bookChapter.getId(),
                consumeLog.getId(), 1,
                originalPrice, originalPrice, 0, platformSubsidy);
    }

    private void saveBenefitsSnapshot(Long userId, MemberInfo memberInfo,
                                       int originalPrice, int actualPrice,
                                       int benefitType, int benefitValue,
                                       Long chapterId, LocalDateTime expireTime,
                                       LocalDateTime now) {
        MemberBenefitsSnapshot snapshot = new MemberBenefitsSnapshot();
        snapshot.setUserId(userId);
        snapshot.setMemberLevel(memberInfo.getMemberLevel());
        snapshot.setOriginalPrice(originalPrice);
        snapshot.setActualPrice(actualPrice);
        snapshot.setBenefitType(benefitType);
        snapshot.setBenefitValue(benefitValue);
        snapshot.setChapterId(chapterId);
        snapshot.setExpireTime(expireTime);
        snapshot.setCreateTime(now);
        snapshot.setUpdateTime(now);
        memberBenefitsSnapshotMapper.insert(snapshot);
    }

    private RestResp<BookContentAboutRespDto> buildFullContent(BookChapter bookChapter) {
        String content = bookContentCacheManager.getBookContent(bookChapter.getId());
        BookChapterRespDto chapterRespDto = bookChapterCacheManager.getChapter(bookChapter.getId());
        BookInfoRespDto bookInfo = bookInfoCacheManager.getBookInfo(bookChapter.getBookId());

        return RestResp.ok(BookContentAboutRespDto.builder()
                .bookInfo(bookInfo)
                .chapterInfo(chapterRespDto)
                .bookContent(content)
                .needPurchase(false)
                .build());
    }

    private RestResp<BookContentAboutRespDto> buildFullContentWithFreeLimitFlag(BookChapter bookChapter) {
        String content = bookContentCacheManager.getBookContent(bookChapter.getId());
        BookChapterRespDto chapterRespDto = bookChapterCacheManager.getChapter(bookChapter.getId());
        BookInfoRespDto bookInfo = bookInfoCacheManager.getBookInfo(bookChapter.getBookId());

        return RestResp.ok(BookContentAboutRespDto.builder()
                .bookInfo(bookInfo)
                .chapterInfo(chapterRespDto)
                .bookContent(content)
                .needPurchase(false)
                .isFreeLimit(true)
                .build());
    }

    private RestResp<BookContentAboutRespDto> buildFullContentWithPurchasedFlag(BookChapter bookChapter) {
        String content = bookContentCacheManager.getBookContent(bookChapter.getId());
        BookChapterRespDto chapterRespDto = bookChapterCacheManager.getChapter(bookChapter.getId());
        BookInfoRespDto bookInfo = bookInfoCacheManager.getBookInfo(bookChapter.getBookId());

        return RestResp.ok(BookContentAboutRespDto.builder()
                .bookInfo(bookInfo)
                .chapterInfo(chapterRespDto)
                .bookContent(content)
                .needPurchase(false)
                .isPurchased(true)
                .build());
    }

    private RestResp<BookContentAboutRespDto> buildFullContentWithMemberFlag(BookChapter bookChapter) {
        String content = bookContentCacheManager.getBookContent(bookChapter.getId());
        BookChapterRespDto chapterRespDto = bookChapterCacheManager.getChapter(bookChapter.getId());
        BookInfoRespDto bookInfo = bookInfoCacheManager.getBookInfo(bookChapter.getBookId());

        return RestResp.ok(BookContentAboutRespDto.builder()
                .bookInfo(bookInfo)
                .chapterInfo(chapterRespDto)
                .bookContent(content)
                .needPurchase(false)
                .isMemberFreeRead(true)
                .build());
    }

    private RestResp<BookContentAboutRespDto> buildPreviewContent(BookChapter bookChapter, Long userId) {
        String fullContent = bookContentCacheManager.getBookContent(bookChapter.getId());
        BookChapterRespDto chapterRespDto = bookChapterCacheManager.getChapter(bookChapter.getId());
        BookInfoRespDto bookInfo = bookInfoCacheManager.getBookInfo(bookChapter.getBookId());

        int price = determineChapterPrice(bookChapter);

        // 计算会员折扣价
        Integer memberDiscountPrice = null;
        if (userId != null) {
            MemberInfo member = memberInfoCacheManager.getActiveMemberInfo(userId);
            if (member != null && member.getDiscountRate() < 100) {
                memberDiscountPrice = price * member.getDiscountRate() / 100;
            }
        }

        String preview = fullContent.length() > PREVIEW_WORD_COUNT
                ? fullContent.substring(0, PREVIEW_WORD_COUNT) + "..."
                : fullContent;

        return RestResp.ok(BookContentAboutRespDto.builder()
                .bookInfo(bookInfo)
                .chapterInfo(chapterRespDto)
                .bookContent(null)
                .needPurchase(true)
                .chapterPrice(price)
                .isPurchased(false)
                .isFreeLimit(false)
                .previewContent(preview)
                .memberDiscountPrice(memberDiscountPrice)
                .build());
    }

}
