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

    private final AuthorInfoMapper authorInfoMapper;

    private final BookChapterCacheManager bookChapterCacheManager;

    private final BookInfoCacheManager bookInfoCacheManager;

    private final BookContentCacheManager bookContentCacheManager;

    private final RedisDistributedLockManager lockManager;

    private final FinanceProperties financeProperties;

    /**
     * VIP 章节内容预览字数
     */
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

        // 2. 校验是否 VIP 章节
        if (!Objects.equals(bookChapter.getIsVip(), 1)) {
            // 免费章节，直接返回内容
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

        // 6. 确定价格
        int price = determineChapterPrice(bookChapter);

        // === Phase 2: 分布式锁 ===
        String lockKey = String.format("purchase:lock:%d:%d", userId, chapterId);
        String lockValue = UUID.randomUUID().toString();
        try {
            if (!lockManager.tryLock(lockKey, lockValue, 10)) {
                throw new BusinessException(ErrorCodeEnum.SYSTEM_PURCHASE_LOCK_FAILED);
            }

            // === Phase 3: 锁内双重检查 ===
            // 重查余额
            UserInfo userInfo = userInfoMapper.selectById(userId);
            if (userInfo.getAccountBalance() == null || userInfo.getAccountBalance() < price) {
                throw new BusinessException(ErrorCodeEnum.USER_BALANCE_INSUFFICIENT);
            }

            // 重查幂等
            if (hasPurchased(userId, chapterId)) {
                throw new BusinessException(ErrorCodeEnum.USER_CHAPTER_ALREADY_PURCHASED);
            }

            // === Phase 4: 财务操作（全部在 @Transactional 内） ===

            // 4a. 原子扣减余额
            int affected = userInfoMapper.deductBalance(userId, price);
            if (affected == 0) {
                throw new BusinessException(ErrorCodeEnum.USER_BALANCE_INSUFFICIENT);
            }

            // 4b. 创建消费记录
            UserConsumeLog consumeLog = new UserConsumeLog();
            consumeLog.setUserId(userId);
            consumeLog.setAuthorId(bookInfoEntity.getAuthorId());
            consumeLog.setAmount(price);
            consumeLog.setProductType(0); // 0 = VIP章节
            consumeLog.setProductId(chapterId);
            consumeLog.setProducName(bookChapter.getChapterName());
            consumeLog.setProducValue(1);
            consumeLog.setRefundStatus(0);
            consumeLog.setCreateTime(LocalDateTime.now());
            consumeLog.setUpdateTime(LocalDateTime.now());
            userConsumeLogMapper.insert(consumeLog);

            // 4c. 累计作者日收入
            accumulateAuthorIncomeDetail(
                    bookInfoEntity.getAuthorId(),
                    bookChapter.getBookId(),
                    userId,
                    price
            );

            log.info("用户 {} 购买章节 {} 成功，消费 {} 屋币", userId, chapterId, price);

            // 返回完整内容
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
            // 免费章节，直接返回完整内容
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

        // 5. 未购买 — 返回预览 + 购买信息
        return buildPreviewContent(bookChapter);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RestResp<Void> refund(Long userId, Long consumeLogId) {
        // 1. 查找消费记录
        UserConsumeLog consumeLog = userConsumeLogMapper.selectById(consumeLogId);
        if (consumeLog == null || !Objects.equals(consumeLog.getUserId(), userId)) {
            throw new BusinessException(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
        }

        // 2. 检查是否已退款
        if (Objects.equals(consumeLog.getRefundStatus(), 1)) {
            throw new BusinessException(ErrorCodeEnum.USER_REFUND_NOT_ALLOWED);
        }

        // 3. 恢复用户余额
        userInfoMapper.restoreBalance(userId, consumeLog.getAmount());

        // 4. 标记消费记录为已退款
        consumeLog.setRefundStatus(1);
        consumeLog.setUpdateTime(LocalDateTime.now());
        userConsumeLogMapper.updateById(consumeLog);

        // 5. 扣减作者日收入
        BookChapter bookChapter = bookChapterMapper.selectById(consumeLog.getProductId());
        if (bookChapter != null && consumeLog.getAuthorId() != null) {
            // 检查该用户当天是否还有其他购买，决定是否扣减 incomeNumber
            int remainingPurchases = authorIncomeDetailMapper.countUserPurchasesToday(
                    consumeLog.getAuthorId(), bookChapter.getBookId(), userId, LocalDate.now());
            int numberDecrement = (remainingPurchases <= 1) ? 1 : 0;

            authorIncomeDetailMapper.deductDailyIncome(
                    consumeLog.getAuthorId(),
                    bookChapter.getBookId(),
                    LocalDate.now(),
                    consumeLog.getAmount(),
                    numberDecrement
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
            // 查询该小说所有章节ID
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

    /**
     * 检查章节是否处于限免状态（章节级或全书级）
     */
    private boolean isFreeLimit(BookChapter bookChapter) {
        if (Objects.equals(bookChapter.getIsFreeLimit(), 1)) {
            return true;
        }
        // 检查全书限免
        BookInfo bookInfo = bookInfoMapper.selectById(bookChapter.getBookId());
        return bookInfo != null && Objects.equals(bookInfo.getIsFreeLimit(), 1);
    }

    /**
     * 检查用户是否已购买该章节
     */
    private boolean hasPurchased(Long userId, Long chapterId) {
        QueryWrapper<UserConsumeLog> qw = new QueryWrapper<>();
        qw.eq(DatabaseConsts.UserConsumeLogTable.COLUMN_USER_ID, userId)
                .eq(DatabaseConsts.UserConsumeLogTable.COLUMN_PRODUCT_ID, chapterId)
                .eq(DatabaseConsts.UserConsumeLogTable.COLUMN_PRODUCT_TYPE, 0)
                .eq(DatabaseConsts.UserConsumeLogTable.COLUMN_REFUND_STATUS, 0);
        return userConsumeLogMapper.selectCount(qw) > 0;
    }

    /**
     * 防止作者购买自己的章节
     */
    private void preventSelfPurchase(Long userId, Long authorId) {
        if (authorId == null) {
            return;
        }
        // 查询作者对应的 userId
        QueryWrapper<AuthorInfo> qw = new QueryWrapper<>();
        qw.eq(DatabaseConsts.AuthorInfoTable.COLUMN_USER_ID, userId);
        AuthorInfo authorInfo = authorInfoMapper.selectOne(qw);
        if (authorInfo != null && Objects.equals(authorInfo.getId(), authorId)) {
            throw new BusinessException(ErrorCodeEnum.USER_PURCHASE_OWN_CHAPTER);
        }
    }

    /**
     * 确定章节价格
     */
    private int determineChapterPrice(BookChapter bookChapter) {
        return (bookChapter.getChapterPrice() != null && bookChapter.getChapterPrice() > 0)
                ? bookChapter.getChapterPrice()
                : financeProperties.getDefaultChapterPrice();
    }

    /**
     * 累计作者日收入（INSERT ON DUPLICATE KEY UPDATE）
     */
    private void accumulateAuthorIncomeDetail(Long authorId, Long bookId, Long userId, int amount) {
        LocalDate today = LocalDate.now();
        // 检查该用户当天是否已购买过该作者+作品的章节（决定是否新增 incomeNumber）
        int existingPurchases = authorIncomeDetailMapper.countUserPurchasesToday(
                authorId, bookId, userId, today);
        int numberIncrement = (existingPurchases > 0) ? 0 : 1;

        authorIncomeDetailMapper.upsertDailyIncome(
                authorId, bookId, today, amount, 1, numberIncrement);
    }

    /**
     * 构建完整章节内容响应（免费章节或已购买）
     */
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

    /**
     * 构建完整章节内容响应（限免标记）
     */
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

    /**
     * 构建完整章节内容响应（已购买标记）
     */
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
     * 构建预览内容响应（未购买 VIP 章节）
     */
    private RestResp<BookContentAboutRespDto> buildPreviewContent(BookChapter bookChapter) {
        String fullContent = bookContentCacheManager.getBookContent(bookChapter.getId());
        BookChapterRespDto chapterRespDto = bookChapterCacheManager.getChapter(bookChapter.getId());
        BookInfoRespDto bookInfo = bookInfoCacheManager.getBookInfo(bookChapter.getBookId());

        int price = determineChapterPrice(bookChapter);
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
                .build());
    }

}
