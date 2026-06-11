package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.core.common.constant.ErrorCodeEnum;
import io.github.xxyopen.novel.core.common.exception.BusinessException;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.config.FinanceProperties;
import io.github.xxyopen.novel.dao.entity.*;
import io.github.xxyopen.novel.dao.mapper.*;
import io.github.xxyopen.novel.manager.cache.BookChapterCacheManager;
import io.github.xxyopen.novel.manager.cache.BookContentCacheManager;
import io.github.xxyopen.novel.manager.cache.BookInfoCacheManager;
import io.github.xxyopen.novel.manager.redis.RedisDistributedLockManager;
import io.github.xxyopen.novel.service.impl.ChapterPurchaseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 退款回滚测试
 * 场景：退款恢复余额、标记消费记录、扣减作者收入、重复退款拒绝
 */
@ExtendWith(MockitoExtension.class)
class RefundRollbackTest {

    @InjectMocks
    private ChapterPurchaseServiceImpl chapterPurchaseService;

    @Mock private BookChapterMapper bookChapterMapper;
    @Mock private BookInfoMapper bookInfoMapper;
    @Mock private BookContentMapper bookContentMapper;
    @Mock private UserInfoMapper userInfoMapper;
    @Mock private UserConsumeLogMapper userConsumeLogMapper;
    @Mock private AuthorIncomeDetailMapper authorIncomeDetailMapper;
    @Mock private AuthorInfoMapper authorInfoMapper;
    @Mock private BookChapterCacheManager bookChapterCacheManager;
    @Mock private BookInfoCacheManager bookInfoCacheManager;
    @Mock private BookContentCacheManager bookContentCacheManager;
    @Mock private RedisDistributedLockManager lockManager;

    private final Long userId = 1L;
    private final Long consumeLogId = 999L;
    private final Long chapterId = 100L;
    private final Long bookId = 10L;
    private final Long authorId = 5L;
    private final int purchaseAmount = 10;

    @BeforeEach
    void setUp() throws Exception {
        FinanceProperties props = new FinanceProperties();
        props.setDefaultChapterPrice(10);
        var field = ChapterPurchaseServiceImpl.class.getDeclaredField("financeProperties");
        field.setAccessible(true);
        field.set(chapterPurchaseService, props);
    }

    @Test
    void testRefund_restoresBalanceAndReversesIncome() {
        // 准备消费记录
        UserConsumeLog log = buildConsumeLog();
        when(userConsumeLogMapper.selectById(consumeLogId)).thenReturn(log);

        // 退款恢复余额
        when(userInfoMapper.restoreBalance(userId, purchaseAmount)).thenReturn(1);

        // 更新消费记录
        when(userConsumeLogMapper.updateById(any(UserConsumeLog.class))).thenReturn(1);

        // 查找章节获取 bookId
        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        // 当天该用户无其他购买
        when(authorIncomeDetailMapper.countUserPurchasesToday(authorId, bookId, userId, LocalDate.now()))
                .thenReturn(1); // 退款后只剩这1次（即将被退的这次），所以 numberDecrement=1

        // 扣减日收入
        when(authorIncomeDetailMapper.deductDailyIncome(
                eq(authorId), eq(bookId), eq(LocalDate.now()), eq(purchaseAmount), eq(1)))
                .thenReturn(1);

        // 执行退款
        RestResp<Void> result = chapterPurchaseService.refund(userId, consumeLogId);

        assertTrue(result.isOk());

        // 验证余额恢复
        verify(userInfoMapper).restoreBalance(userId, purchaseAmount);

        // 验证消费记录标记为已退款
        verify(userConsumeLogMapper).updateById(argThat(cl -> {
            return cl.getRefundStatus() == 1;
        }));

        // 验证作者日收入扣减
        verify(authorIncomeDetailMapper).deductDailyIncome(
                eq(authorId), eq(bookId), eq(LocalDate.now()), eq(purchaseAmount), eq(1));
    }

    @Test
    void testRefund_alreadyRefunded_throwsNotAllowed() {
        // 消费记录已退款
        UserConsumeLog log = buildConsumeLog();
        log.setRefundStatus(1); // 已退款
        when(userConsumeLogMapper.selectById(consumeLogId)).thenReturn(log);

        // 执行退款应抛异常
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chapterPurchaseService.refund(userId, consumeLogId));

        assertEquals(ErrorCodeEnum.USER_REFUND_NOT_ALLOWED, ex.getErrorCodeEnum());

        // 验证未进行任何余额操作
        verify(userInfoMapper, never()).restoreBalance(anyLong(), anyInt());
    }

    @Test
    void testRefund_wrongUser_throwsParamError() {
        // 消费记录属于其他用户
        UserConsumeLog log = buildConsumeLog();
        log.setUserId(999L); // 其他用户
        when(userConsumeLogMapper.selectById(consumeLogId)).thenReturn(log);

        Long otherUserId = 2L;
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chapterPurchaseService.refund(otherUserId, consumeLogId));

        assertEquals(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR, ex.getErrorCodeEnum());
    }

    @Test
    void testRefund_consumeLogNotFound_throwsParamError() {
        when(userConsumeLogMapper.selectById(consumeLogId)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chapterPurchaseService.refund(userId, consumeLogId));

        assertEquals(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR, ex.getErrorCodeEnum());
    }

    @Test
    void testRefund_multipleRefundsOnlyFirstSucceeds() {
        UserConsumeLog log = buildConsumeLog();
        when(userConsumeLogMapper.selectById(consumeLogId))
                .thenReturn(log)        // 第一次调用：未退款
                .thenReturn(log);       // 第二次调用：模拟已退款（实际中 updateById 会更新 DB）

        when(userInfoMapper.restoreBalance(userId, purchaseAmount)).thenReturn(1);
        when(userConsumeLogMapper.updateById(any())).thenReturn(1);

        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);
        when(authorIncomeDetailMapper.countUserPurchasesToday(anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(1);
        when(authorIncomeDetailMapper.deductDailyIncome(anyLong(), anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(1);

        // 第一次退款成功
        RestResp<Void> result1 = chapterPurchaseService.refund(userId, consumeLogId);
        assertTrue(result1.isOk());

        // 模拟 DB 中 refundStatus 已更新
        log.setRefundStatus(1);

        // 第二次退款应失败
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chapterPurchaseService.refund(userId, consumeLogId));

        assertEquals(ErrorCodeEnum.USER_REFUND_NOT_ALLOWED, ex.getErrorCodeEnum());
    }

    private UserConsumeLog buildConsumeLog() {
        UserConsumeLog log = new UserConsumeLog();
        log.setId(consumeLogId);
        log.setUserId(userId);
        log.setAuthorId(authorId);
        log.setAmount(purchaseAmount);
        log.setProductType(0);
        log.setProductId(chapterId);
        log.setProducName("VIP测试章节");
        log.setProducValue(1);
        log.setRefundStatus(0);
        log.setCreateTime(LocalDateTime.now());
        log.setUpdateTime(LocalDateTime.now());
        return log;
    }
}
