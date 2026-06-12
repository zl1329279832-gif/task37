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
import io.github.xxyopen.novel.manager.redis.RedisDistributedLockManager;
import io.github.xxyopen.novel.service.ChapterPurchaseService;
import io.github.xxyopen.novel.service.MembershipService;
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
 * 增强：会员权益叠加 + 作者分账延迟结算
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
    private final MembershipService membershipService;
    private final SettlementService settlementService;
    private final ReadingVoucherMapper readingVoucherMapper;

    private static final int PREVIEW_WORD_COUNT = 200;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RestResp<BookContentAboutRespDto> purchaseChapter(Long userId, Long chapterId) {
        // === Phase 1: 锁外预检 ===

        // 1. 校验章节存在
        BookChapter bookChapter = bookChapterMapper.selectById(chapterId);
        if (bookChapter == null) {
            throw new BusinessException(ErrorCodeEnum.USER_CHAPTER_NOT_EXIST);
        }

        // 2. 免费章节直接返回
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
        UserMembership membership = membershipService.getActiveMembershipEntity(userId);

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

            // 重查幂等
            if (hasPurchased(userId, chapterId)) {
                throw new BusinessException(ErrorCodeEnum.USER_CHAPTER_ALREADY_PURCHASED);
            }

            // === Phase 4: 确定支付方式和价格 ===
            int payType;
            int actualPrice;
            int membershipSubsidy = 0;

            if (membership != null && membershipService.canFreeRead(membership)) {
                // 会员免费阅读
                boolean consumed = membershipService.consumeFreeQuota(membership.getId());
                if (consumed) {
                    payType = 1; // 会员免费
                    actualPrice = 0;
                    membershipSubsidy = originalPrice; // 平台全额补贴
                } else {
                    // 配额已被并发消耗，降级到折扣
                    payType = 2;
                    actualPrice = membershipService.calculateDiscountPrice(originalPrice, membership);
                    membershipSubsidy = calculateMembershipSubsidy(originalPrice, actualPrice);
                }
            } else if (membership != null) {
                // 会员折扣购买
                payType = 2;
                actualPrice = membershipService.calculateDiscountPrice(originalPrice, membership);
                membershipSubsidy = calculateMembershipSubsidy(originalPrice, actualPrice);
            } else {
                // 普通购买
                payType = 0;
                actualPrice = originalPrice;
            }

            // === Phase 5: 财务操作 ===

            // 5a. 扣减余额（仅当 actualPrice > 0）
            if (actualPrice > 0) {
                UserInfo userInfo = userInfoMapper.selectById(userId);
                if (userInfo.getAccountBalance() == null || userInfo.getAccountBalance() < actualPrice) {
                    // 如果已消耗免费配额，需要回滚
                    if (payType == 1) {
                        membershipService.restoreFreeQuota(membership.getId());
                    }
                    throw new BusinessException(ErrorCodeEnum.USER_BALANCE_INSUFFICIENT);
                }
                int affected = userInfoMapper.deductBalance(userId, actualPrice);
                if (affected == 0) {
                    if (payType == 1) {
                        membershipService.restoreFreeQuota(membership.getId());
                    }
                    throw new BusinessException(ErrorCodeEnum.USER_BALANCE_INSUFFICIENT);
                }
            }

            // 5b. 创建消费记录
            UserConsumeLog consumeLog = new UserConsumeLog();
            consumeLog.setUserId(userId);
            consumeLog.setAuthorId(bookInfoEntity.getAuthorId());
            consumeLog.setAmount(actualPrice);
            consumeLog.setProductType(0); // VIP章节
            consumeLog.setProductId(chapterId);
            consumeLog.setProducName(bookChapter.getChapterName());
            consumeLog.setProducValue(1);
            consumeLog.setPayType(payType);
            consumeLog.setRefundStatus(0);
            consumeLog.setCreateTime(LocalDateTime.now());
            consumeLog.setUpdateTime(LocalDateTime.now());
            userConsumeLogMapper.insert(consumeLog);

            // 5c. 创建待结算流水（延迟结算，不再立即累计作者收入）
            settlementService.createPendingSettlement(
                    consumeLog.getId(),
                    userId,
                    bookInfoEntity.getAuthorId(),
                    bookChapter.getBookId(),
                    chapterId,
                    actualPrice,
                    payType,
                    membershipSubsidy,
                    0 // platformSubsidy，活动补贴为0
            );

            log.info("用户 {} 购买章节 {} 成功，支付方式={}, 实付={}, 会员补贴={}",
                    userId, chapterId, payType, actualPrice, membershipSubsidy);

            return buildFullContent(bookChapter);

        } finally {
            lockManager.releaseLock(lockKey, lockValue);
        }
    }

    /**
     * 使用阅读券购买章节
     */
    @Transactional(rollbackFor = Exception.class)
    public RestResp<BookContentAboutRespDto> purchaseChapterWithVoucher(Long userId, Long chapterId, Long voucherId) {
        // 1. 校验章节
        BookChapter bookChapter = bookChapterMapper.selectById(chapterId);
        if (bookChapter == null) {
            throw new BusinessException(ErrorCodeEnum.USER_CHAPTER_NOT_EXIST);
        }
        if (!Objects.equals(bookChapter.getIsVip(), 1)) {
            return buildFullContent(bookChapter);
        }
        if (isFreeLimit(bookChapter)) {
            return buildFullContent(bookChapter);
        }
        if (hasPurchased(userId, chapterId)) {
            throw new BusinessException(ErrorCodeEnum.USER_CHAPTER_ALREADY_PURCHASED);
        }

        BookInfo bookInfoEntity = bookInfoMapper.selectById(bookChapter.getBookId());
        if (bookInfoEntity == null) {
            throw new BusinessException(ErrorCodeEnum.USER_BOOK_NOT_EXIST);
        }
        preventSelfPurchase(userId, bookInfoEntity.getAuthorId());

        int originalPrice = determineChapterPrice(bookChapter);

        // 2. 使用阅读券（CAS原子操作）
        ReadingVoucher voucher = membershipService.useVoucher(voucherId, chapterId);

        // 全免券：实付0，平台补贴全额
        int actualPrice = 0;
        int membershipSubsidy = originalPrice;
        int payType = 3; // 阅读券

        // 3. 创建消费记录
        UserConsumeLog consumeLog = new UserConsumeLog();
        consumeLog.setUserId(userId);
        consumeLog.setAuthorId(bookInfoEntity.getAuthorId());
        consumeLog.setAmount(actualPrice);
        consumeLog.setProductType(0);
        consumeLog.setProductId(chapterId);
        consumeLog.setProducName(bookChapter.getChapterName());
        consumeLog.setProducValue(1);
        consumeLog.setPayType(payType);
        consumeLog.setRefundStatus(0);
        consumeLog.setCreateTime(LocalDateTime.now());
        consumeLog.setUpdateTime(LocalDateTime.now());
        userConsumeLogMapper.insert(consumeLog);

        // 4. 创建待结算流水
        settlementService.createPendingSettlement(
                consumeLog.getId(), userId, bookInfoEntity.getAuthorId(),
                bookChapter.getBookId(), chapterId, actualPrice, payType,
                membershipSubsidy, 0);

        log.info("用户 {} 使用阅读券 {} 购买章节 {} 成功", userId, voucherId, chapterId);

        return buildFullContent(bookChapter);
    }

    @Override
    public RestResp<BookContentAboutRespDto> getChapterContentWithAccessControl(Long userId, Long chapterId) {
        BookChapter bookChapter = bookChapterMapper.selectById(chapterId);
        if (bookChapter == null) {
            throw new BusinessException(ErrorCodeEnum.USER_CHAPTER_NOT_EXIST);
        }

        // 免费章节
        if (!Objects.equals(bookChapter.getIsVip(), 1)) {
            return buildFullContent(bookChapter);
        }

        // 限免
        if (isFreeLimit(bookChapter)) {
            return buildFullContentWithFreeLimitFlag(bookChapter);
        }

        // 已购买
        if (userId != null && hasPurchased(userId, chapterId)) {
            return buildFullContentWithPurchasedFlag(bookChapter);
        }

        // 未购买 — 构建包含会员权益信息的预览
        return buildPreviewContentWithMembership(bookChapter, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RestResp<Void> refund(Long userId, Long consumeLogId) {
        // 1. 查找消费记录
        UserConsumeLog consumeLog = userConsumeLogMapper.selectById(consumeLogId);
        if (consumeLog == null || !Objects.equals(consumeLog.getUserId(), userId)) {
            throw new BusinessException(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
        }

        // 2. 预检是否已退款
        if (Objects.equals(consumeLog.getRefundStatus(), 1)) {
            throw new BusinessException(ErrorCodeEnum.USER_REFUND_NOT_ALLOWED);
        }

        BookChapter bookChapter = bookChapterMapper.selectById(consumeLog.getProductId());
        LocalDate consumeDate = consumeLog.getCreateTime().toLocalDate();
        LocalDate incomeMonth = consumeDate.withDayOfMonth(1);

        // 3. 检查待结算流水（新流程）
        PendingSettlement pending = settlementService.getPendingByConsumeLogId(consumeLogId);

        if (pending != null) {
            // === 新流程：通过待结算池退款 ===
            return refundViaPendingSettlement(userId, consumeLogId, consumeLog, pending, bookChapter);
        }

        // === 旧流程兼容：直接退款（无待结算记录的历史订单） ===
        return refundLegacy(userId, consumeLogId, consumeLog, bookChapter, consumeDate, incomeMonth);
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
                .payType(v.getPayType())
                .refundStatus(v.getRefundStatus())
                .createTime(v.getCreateTime())
                .build()).toList();
        return RestResp.ok(respList);
    }

    // ======================== 新流程退款 ========================

    private RestResp<Void> refundViaPendingSettlement(Long userId, Long consumeLogId,
                                                      UserConsumeLog consumeLog,
                                                      PendingSettlement pending,
                                                      BookChapter bookChapter) {
        // CAS 更新退款状态
        int affected = userConsumeLogMapper.casSetRefunded(consumeLogId);
        if (affected == 0) {
            throw new BusinessException(ErrorCodeEnum.USER_REFUND_NOT_ALLOWED);
        }

        // 冻结待结算流水
        if (Objects.equals(pending.getStatus(), 0)) {
            settlementService.freezeForRefund(consumeLogId, userId, "用户主动退款");
        }

        // 恢复用户余额（仅当实际支付了金额时）
        if (consumeLog.getAmount() > 0) {
            userInfoMapper.restoreBalance(userId, consumeLog.getAmount());
        }

        // 恢复会员免费配额或阅读券
        Integer payType = consumeLog.getPayType();
        if (payType != null) {
            if (payType == 1) {
                // 会员免费，恢复配额
                UserMembership membership = membershipService.getActiveMembershipEntity(userId);
                if (membership != null) {
                    membershipService.restoreFreeQuota(membership.getId());
                }
            } else if (payType == 3 && bookChapter != null) {
                // 阅读券，恢复券
                restoreVoucherForChapter(userId, bookChapter.getId());
            }
        }

        log.info("用户 {} 退款成功（新流程），消费记录ID {}，退还 {} 屋币",
                userId, consumeLogId, consumeLog.getAmount());
        return RestResp.ok();
    }

    // ======================== 旧流程兼容退款 ========================

    private RestResp<Void> refundLegacy(Long userId, Long consumeLogId,
                                        UserConsumeLog consumeLog, BookChapter bookChapter,
                                        LocalDate consumeDate, LocalDate incomeMonth) {
        // 检查月度结算确认状态
        if (bookChapter != null && consumeLog.getAuthorId() != null) {
            Integer confirmStatus = authorIncomeMapper.getConfirmStatus(
                    consumeLog.getAuthorId(), bookChapter.getBookId(), incomeMonth);
            if (confirmStatus != null && confirmStatus == 1) {
                throw new BusinessException(ErrorCodeEnum.USER_SETTLEMENT_CONFIRMED);
            }
        }

        // CAS 原子更新退款状态
        int affected = userConsumeLogMapper.casSetRefunded(consumeLogId);
        if (affected == 0) {
            throw new BusinessException(ErrorCodeEnum.USER_REFUND_NOT_ALLOWED);
        }

        // 恢复用户余额
        userInfoMapper.restoreBalance(userId, consumeLog.getAmount());

        // 扣减作者日收入
        if (bookChapter != null && consumeLog.getAuthorId() != null) {
            int remainingPurchases = authorIncomeDetailMapper.countUserPurchasesToday(
                    consumeLog.getAuthorId(), bookChapter.getBookId(), userId, consumeDate);
            int numberDecrement = (remainingPurchases <= 1) ? 1 : 0;

            authorIncomeDetailMapper.deductDailyIncome(
                    consumeLog.getAuthorId(), bookChapter.getBookId(), consumeDate,
                    consumeLog.getAmount(), numberDecrement);

            int afterTaxDeduct = consumeLog.getAmount()
                    * (100 - financeProperties.getTaxRate()) / 100;
            authorIncomeMapper.deductSettlement(
                    consumeLog.getAuthorId(), bookChapter.getBookId(), incomeMonth,
                    consumeLog.getAmount(), afterTaxDeduct);
        }

        log.info("用户 {} 退款成功（旧流程），消费记录ID {}，退还 {} 屋币",
                userId, consumeLogId, consumeLog.getAmount());
        return RestResp.ok();
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

    /**
     * 计算会员补贴金额（平台承担的折扣差价部分）
     */
    private int calculateMembershipSubsidy(int originalPrice, int discountPrice) {
        int discount = originalPrice - discountPrice;
        return discount * financeProperties.getMembershipSubsidyRate() / 100;
    }

    private void restoreVoucherForChapter(Long userId, Long chapterId) {
        QueryWrapper<ReadingVoucher> qw = new QueryWrapper<>();
        qw.eq("user_id", userId)
                .eq("used_chapter_id", chapterId)
                .eq("status", 1)
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        ReadingVoucher voucher = readingVoucherMapper.selectOne(qw);
        if (voucher != null) {
            membershipService.restoreVoucher(voucher.getId());
        }
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

    /**
     * 构建包含会员权益信息的预览内容
     */
    private RestResp<BookContentAboutRespDto> buildPreviewContentWithMembership(
            BookChapter bookChapter, Long userId) {
        String fullContent = bookContentCacheManager.getBookContent(bookChapter.getId());
        BookChapterRespDto chapterRespDto = bookChapterCacheManager.getChapter(bookChapter.getId());
        BookInfoRespDto bookInfo = bookInfoCacheManager.getBookInfo(bookChapter.getBookId());

        int price = determineChapterPrice(bookChapter);
        String preview = fullContent.length() > PREVIEW_WORD_COUNT
                ? fullContent.substring(0, PREVIEW_WORD_COUNT) + "..."
                : fullContent;

        BookContentAboutRespDto.BookContentAboutRespDtoBuilder builder = BookContentAboutRespDto.builder()
                .bookInfo(bookInfo)
                .chapterInfo(chapterRespDto)
                .bookContent(null)
                .needPurchase(true)
                .chapterPrice(price)
                .isPurchased(false)
                .isFreeLimit(false)
                .previewContent(preview);

        // 添加会员权益信息
        if (userId != null) {
            UserMembership membership = membershipService.getActiveMembershipEntity(userId);
            if (membership != null) {
                if (membershipService.canFreeRead(membership)) {
                    builder.isMembershipFree(true);
                }
                int discountPrice = membershipService.calculateDiscountPrice(price, membership);
                if (discountPrice < price) {
                    builder.membershipDiscountPrice(discountPrice);
                }
            }
            int voucherCount = membershipService.countAvailableVouchers(userId);
            if (voucherCount > 0) {
                builder.availableVoucherCount(voucherCount);
            }
        }

        return RestResp.ok(builder.build());
    }

}
