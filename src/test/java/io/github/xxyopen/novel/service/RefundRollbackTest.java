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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 退款回滚测试
 * 场景：退款恢复余额、CAS防并发、消费日期扣减、月结回滚、已确认拒绝
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundRollbackTest {

    @InjectMocks
    private ChapterPurchaseServiceImpl chapterPurchaseService;

    @Mock private BookChapterMapper bookChapterMapper;
    @Mock private BookInfoMapper bookInfoMapper;
    @Mock private BookContentMapper bookContentMapper;
    @Mock private UserInfoMapper userInfoMapper;
    @Mock private UserConsumeLogMapper userConsumeLogMapper;
    @Mock private AuthorIncomeDetailMapper authorIncomeDetailMapper;
    @Mock private AuthorIncomeMapper authorIncomeMapper;
    @Mock private AuthorInfoMapper authorInfoMapper;
    @Mock private BookChapterCacheManager bookChapterCacheManager;
    @Mock private BookInfoCacheManager bookInfoCacheManager;
    @Mock private BookContentCacheManager bookContentCacheManager;
    @Mock private RedisDistributedLockManager lockManager;
    @Mock private io.github.xxyopen.novel.manager.cache.MemberInfoCacheManager memberInfoCacheManager;
    @Mock private MemberInfoMapper memberInfoMapper;
    @Mock private ReadingCouponMapper readingCouponMapper;
    @Mock private MemberBenefitsSnapshotMapper memberBenefitsSnapshotMapper;
    @Mock private SettlementService settlementService;
    @Mock private RefundFreezeMapper refundFreezeMapper;

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
        props.setTaxRate(20);
        var field = ChapterPurchaseServiceImpl.class.getDeclaredField("financeProperties");
        field.setAccessible(true);
        field.set(chapterPurchaseService, props);

        // 默认退款冻结查询返回null（无冻结记录）
        lenient().when(refundFreezeMapper.selectByConsumeLogId(anyLong())).thenReturn(null);
    }

    @Test
    void testRefund_restoresBalanceAndReversesIncome() {
        // 准备消费记录（今天购买）
        UserConsumeLog log = buildConsumeLog();
        LocalDate consumeDate = log.getCreateTime().toLocalDate();
        when(userConsumeLogMapper.selectById(consumeLogId)).thenReturn(log);

        // CAS 更新退款状态成功
        when(userConsumeLogMapper.casSetRefunded(consumeLogId)).thenReturn(1);

        // 退款恢复余额
        when(userInfoMapper.restoreBalance(userId, purchaseAmount)).thenReturn(1);

        // 查找章节获取 bookId
        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        // 无已确认月结记录
        when(authorIncomeMapper.getConfirmStatus(authorId, bookId, consumeDate.withDayOfMonth(1)))
                .thenReturn(null);

        // 当天该用户仅此一次购买
        when(authorIncomeDetailMapper.countUserPurchasesToday(authorId, bookId, userId, consumeDate))
                .thenReturn(1);

        // 扣减日收入
        when(authorIncomeDetailMapper.deductDailyIncome(
                eq(authorId), eq(bookId), eq(consumeDate), eq(purchaseAmount), eq(1)))
                .thenReturn(1);

        // 月结回滚（无记录时返回0，不影响流程）
        when(authorIncomeMapper.deductSettlement(
                eq(authorId), eq(bookId), eq(consumeDate.withDayOfMonth(1)), eq(purchaseAmount), eq(8)))
                .thenReturn(0);

        when(refundFreezeMapper.selectByConsumeLogId(anyLong())).thenReturn(null);

        // 执行退款
        RestResp<Void> result = chapterPurchaseService.refund(userId, consumeLogId);
        assertTrue(result.isOk());

        // 验证 CAS 原子更新
        verify(userConsumeLogMapper).casSetRefunded(consumeLogId);

        // 验证余额恢复
        verify(userInfoMapper).restoreBalance(userId, purchaseAmount);

        // 验证作者日收入扣减（使用消费日期）
        verify(authorIncomeDetailMapper).deductDailyIncome(
                eq(authorId), eq(bookId), eq(consumeDate), eq(purchaseAmount), eq(1));
    }

    @Test
    void testRefund_alreadyRefunded_throwsNotAllowed() {
        // 消费记录已退款
        UserConsumeLog log = buildConsumeLog();
        log.setRefundStatus(1);
        when(userConsumeLogMapper.selectById(consumeLogId)).thenReturn(log);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chapterPurchaseService.refund(userId, consumeLogId));

        assertEquals(ErrorCodeEnum.USER_REFUND_NOT_ALLOWED, ex.getErrorCodeEnum());

        // 验证未进行任何余额操作
        verify(userInfoMapper, never()).restoreBalance(anyLong(), anyInt());
        verify(userConsumeLogMapper, never()).casSetRefunded(anyLong());
    }

    @Test
    void testRefund_wrongUser_throwsParamError() {
        UserConsumeLog log = buildConsumeLog();
        log.setUserId(999L);
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
    void testRefund_concurrentCAS_secondRefundFails() {
        // 两次调用：第一次 CAS 成功，第二次 CAS 返回 0
        UserConsumeLog log = buildConsumeLog();
        LocalDate consumeDate = log.getCreateTime().toLocalDate();
        when(userConsumeLogMapper.selectById(consumeLogId)).thenReturn(log);

        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        when(authorIncomeMapper.getConfirmStatus(anyLong(), anyLong(), any())).thenReturn(null);

        // 第一次 CAS 成功，第二次 CAS 失败（被第一次抢先）
        when(userConsumeLogMapper.casSetRefunded(consumeLogId))
                .thenReturn(1)
                .thenReturn(0);

        when(userInfoMapper.restoreBalance(userId, purchaseAmount)).thenReturn(1);
        when(authorIncomeDetailMapper.countUserPurchasesToday(anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(1);
        when(authorIncomeDetailMapper.deductDailyIncome(anyLong(), anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(1);
        when(authorIncomeMapper.deductSettlement(anyLong(), anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(0);

        // 第一次退款成功
        RestResp<Void> result1 = chapterPurchaseService.refund(userId, consumeLogId);
        assertTrue(result1.isOk());

        // 第二次退款失败（CAS 返回0）
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chapterPurchaseService.refund(userId, consumeLogId));
        assertEquals(ErrorCodeEnum.USER_REFUND_NOT_ALLOWED, ex.getErrorCodeEnum());

        // 余额只恢复一次
        verify(userInfoMapper, times(1)).restoreBalance(userId, purchaseAmount);
    }

    @Test
    void testRefund_settlementConfirmed_throwsSettlementConfirmed() {
        // 消费记录所在月的结算已被作者确认
        UserConsumeLog log = buildConsumeLog();
        LocalDate consumeDate = log.getCreateTime().toLocalDate();
        when(userConsumeLogMapper.selectById(consumeLogId)).thenReturn(log);

        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        // 结算已确认（confirmStatus=1）
        when(authorIncomeMapper.getConfirmStatus(authorId, bookId, consumeDate.withDayOfMonth(1)))
                .thenReturn(1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chapterPurchaseService.refund(userId, consumeLogId));
        assertEquals(ErrorCodeEnum.USER_SETTLEMENT_CONFIRMED, ex.getErrorCodeEnum());

        // 验证未进行任何财务操作
        verify(userConsumeLogMapper, never()).casSetRefunded(anyLong());
        verify(userInfoMapper, never()).restoreBalance(anyLong(), anyInt());
    }

    @Test
    void testRefund_postSettlement_rollsBackSettlementAmount() {
        // 退款发生在月结之后（未确认），应同步扣减月结金额
        UserConsumeLog log = buildConsumeLog();
        LocalDate consumeDate = log.getCreateTime().toLocalDate();
        LocalDate incomeMonth = consumeDate.withDayOfMonth(1);
        when(userConsumeLogMapper.selectById(consumeLogId)).thenReturn(log);

        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        // 月结存在但未确认（confirmStatus=0）
        when(authorIncomeMapper.getConfirmStatus(authorId, bookId, incomeMonth)).thenReturn(0);

        when(userConsumeLogMapper.casSetRefunded(consumeLogId)).thenReturn(1);
        when(userInfoMapper.restoreBalance(userId, purchaseAmount)).thenReturn(1);
        when(authorIncomeDetailMapper.countUserPurchasesToday(anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(1);
        when(authorIncomeDetailMapper.deductDailyIncome(anyLong(), anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(1);
        // 月结回滚成功
        when(authorIncomeMapper.deductSettlement(
                eq(authorId), eq(bookId), eq(incomeMonth), eq(purchaseAmount), eq(8)))
                .thenReturn(1);

        RestResp<Void> result = chapterPurchaseService.refund(userId, consumeLogId);
        assertTrue(result.isOk());

        // 验证月结被回滚：税前扣10，税后扣8（10 * 80%）
        verify(authorIncomeMapper).deductSettlement(
                eq(authorId), eq(bookId), eq(incomeMonth), eq(purchaseAmount), eq(8));
    }

    @Test
    void testRefund_usesConsumeDate_notCurrentDate() {
        // 消费发生在3天前，退款应使用3天前的日期扣减日收入
        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
        LocalDate consumeDate = threeDaysAgo.toLocalDate();

        UserConsumeLog log = buildConsumeLog();
        log.setCreateTime(threeDaysAgo);
        when(userConsumeLogMapper.selectById(consumeLogId)).thenReturn(log);

        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        when(authorIncomeMapper.getConfirmStatus(anyLong(), anyLong(), any())).thenReturn(null);
        when(userConsumeLogMapper.casSetRefunded(consumeLogId)).thenReturn(1);
        when(userInfoMapper.restoreBalance(userId, purchaseAmount)).thenReturn(1);
        when(authorIncomeDetailMapper.countUserPurchasesToday(
                eq(authorId), eq(bookId), eq(userId), eq(consumeDate))).thenReturn(1);
        when(authorIncomeDetailMapper.deductDailyIncome(
                eq(authorId), eq(bookId), eq(consumeDate), eq(purchaseAmount), eq(1)))
                .thenReturn(1);
        when(authorIncomeMapper.deductSettlement(anyLong(), anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(0);

        RestResp<Void> result = chapterPurchaseService.refund(userId, consumeLogId);
        assertTrue(result.isOk());

        // 验证使用消费日期而非当天日期
        verify(authorIncomeDetailMapper).deductDailyIncome(
                eq(authorId), eq(bookId), eq(consumeDate), eq(purchaseAmount), eq(1));
        verify(authorIncomeDetailMapper).countUserPurchasesToday(
                eq(authorId), eq(bookId), eq(userId), eq(consumeDate));
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
