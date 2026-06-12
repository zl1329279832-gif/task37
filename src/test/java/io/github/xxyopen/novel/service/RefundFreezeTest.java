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

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 退款冻结测试
 * 验证：新流程退款走待结算池冻结，恢复余额/配额/阅读券
 */
@ExtendWith(MockitoExtension.class)
class RefundFreezeTest {

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
    @Mock private ReadingVoucherMapper readingVoucherMapper;
    @Mock private BookChapterCacheManager bookChapterCacheManager;
    @Mock private BookInfoCacheManager bookInfoCacheManager;
    @Mock private BookContentCacheManager bookContentCacheManager;
    @Mock private RedisDistributedLockManager lockManager;
    @Mock private MembershipService membershipService;
    @Mock private SettlementService settlementService;

    private final Long userId = 1L;
    private final Long consumeLogId = 999L;
    private final Long chapterId = 100L;
    private final Long bookId = 10L;
    private final Long authorId = 5L;

    @BeforeEach
    void setUp() throws Exception {
        FinanceProperties props = new FinanceProperties();
        props.setDefaultChapterPrice(10);
        props.setTaxRate(20);
        props.setRefundWindowHours(72);
        var field = ChapterPurchaseServiceImpl.class.getDeclaredField("financeProperties");
        field.setAccessible(true);
        field.set(chapterPurchaseService, props);
    }

    @Test
    void testRefund_normalPurchase_freezesPendingAndRestoresBalance() {
        // 普通购买的退款（有待结算记录）
        UserConsumeLog log = buildConsumeLog(0, 10); // payType=0, amount=10
        when(userConsumeLogMapper.selectById(consumeLogId)).thenReturn(log);
        when(userConsumeLogMapper.casSetRefunded(consumeLogId)).thenReturn(1);

        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        // 有待结算流水
        PendingSettlement pending = new PendingSettlement();
        pending.setId(1L);
        pending.setStatus(0);
        pending.setAuthorId(authorId);
        pending.setAmount(10);
        pending.setRefundWindowEnd(LocalDateTime.now().plusHours(48));
        when(settlementService.getPendingByConsumeLogId(consumeLogId)).thenReturn(pending);
        when(settlementService.freezeForRefund(consumeLogId, userId, "用户主动退款"))
                .thenReturn(RestResp.ok());

        when(userInfoMapper.restoreBalance(userId, 10)).thenReturn(1);

        RestResp<Void> result = chapterPurchaseService.refund(userId, consumeLogId);

        assertTrue(result.isOk());

        // 验证退款状态更新
        verify(userConsumeLogMapper).casSetRefunded(consumeLogId);

        // 验证冻结待结算流水
        verify(settlementService).freezeForRefund(consumeLogId, userId, "用户主动退款");

        // 验证恢复余额
        verify(userInfoMapper).restoreBalance(userId, 10);

        // 验证不走旧流程
        verify(authorIncomeDetailMapper, never()).deductDailyIncome(
                anyLong(), anyLong(), any(), anyInt(), anyInt());
    }

    @Test
    void testRefund_memberFreeRead_restoresFreeQuota() {
        // 会员免费阅读的退款
        UserConsumeLog log = buildConsumeLog(1, 0); // payType=1(会员免费), amount=0
        when(userConsumeLogMapper.selectById(consumeLogId)).thenReturn(log);
        when(userConsumeLogMapper.casSetRefunded(consumeLogId)).thenReturn(1);

        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        PendingSettlement pending = new PendingSettlement();
        pending.setId(1L);
        pending.setStatus(0);
        pending.setAuthorId(authorId);
        pending.setAmount(0);
        pending.setRefundWindowEnd(LocalDateTime.now().plusHours(48));
        when(settlementService.getPendingByConsumeLogId(consumeLogId)).thenReturn(pending);
        when(settlementService.freezeForRefund(consumeLogId, userId, "用户主动退款"))
                .thenReturn(RestResp.ok());

        // 会员仍然有效
        UserMembership membership = new UserMembership();
        membership.setId(100L);
        membership.setStatus(0);
        when(membershipService.getActiveMembershipEntity(userId)).thenReturn(membership);

        RestResp<Void> result = chapterPurchaseService.refund(userId, consumeLogId);

        assertTrue(result.isOk());

        // 验证恢复免费配额
        verify(membershipService).restoreFreeQuota(100L);

        // 不恢复余额（实付0）
        verify(userInfoMapper, never()).restoreBalance(anyLong(), anyInt());
    }

    @Test
    void testRefund_voucherPurchase_restoresVoucher() {
        // 阅读券购买的退款
        UserConsumeLog log = buildConsumeLog(3, 0); // payType=3(阅读券), amount=0
        when(userConsumeLogMapper.selectById(consumeLogId)).thenReturn(log);
        when(userConsumeLogMapper.casSetRefunded(consumeLogId)).thenReturn(1);

        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        PendingSettlement pending = new PendingSettlement();
        pending.setId(1L);
        pending.setStatus(0);
        pending.setAuthorId(authorId);
        pending.setAmount(0);
        pending.setRefundWindowEnd(LocalDateTime.now().plusHours(48));
        when(settlementService.getPendingByConsumeLogId(consumeLogId)).thenReturn(pending);
        when(settlementService.freezeForRefund(consumeLogId, userId, "用户主动退款"))
                .thenReturn(RestResp.ok());

        // 找到对应的已使用阅读券
        ReadingVoucher usedVoucher = new ReadingVoucher();
        usedVoucher.setId(200L);
        usedVoucher.setStatus(1);
        usedVoucher.setUsedChapterId(chapterId);
        when(readingVoucherMapper.selectOne(any())).thenReturn(usedVoucher);

        RestResp<Void> result = chapterPurchaseService.refund(userId, consumeLogId);

        assertTrue(result.isOk());

        // 验证恢复阅读券
        verify(membershipService).restoreVoucher(200L);
    }

    @Test
    void testRefund_alreadyRefunded_throws() {
        UserConsumeLog log = buildConsumeLog(0, 10);
        log.setRefundStatus(1); // 已退款
        when(userConsumeLogMapper.selectById(consumeLogId)).thenReturn(log);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chapterPurchaseService.refund(userId, consumeLogId));

        assertEquals(ErrorCodeEnum.USER_REFUND_NOT_ALLOWED, ex.getErrorCodeEnum());
        verify(settlementService, never()).freezeForRefund(anyLong(), anyLong(), anyString());
    }

    @Test
    void testRefund_legacyOrder_withoutPendingSettlement_usesOldFlow() {
        // 旧订单没有待结算记录，走旧流程
        UserConsumeLog log = buildConsumeLog(null, 10);
        when(userConsumeLogMapper.selectById(consumeLogId)).thenReturn(log);

        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        // 无待结算记录
        when(settlementService.getPendingByConsumeLogId(consumeLogId)).thenReturn(null);

        // 无已确认月结
        when(authorIncomeMapper.getConfirmStatus(anyLong(), anyLong(), any())).thenReturn(null);

        when(userConsumeLogMapper.casSetRefunded(consumeLogId)).thenReturn(1);
        when(userInfoMapper.restoreBalance(userId, 10)).thenReturn(1);
        when(authorIncomeDetailMapper.countUserPurchasesToday(anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(1);
        when(authorIncomeDetailMapper.deductDailyIncome(anyLong(), anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(1);
        when(authorIncomeMapper.deductSettlement(anyLong(), anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(0);

        RestResp<Void> result = chapterPurchaseService.refund(userId, consumeLogId);

        assertTrue(result.isOk());

        // 验证走旧流程
        verify(authorIncomeDetailMapper).deductDailyIncome(
                eq(authorId), eq(bookId), any(), eq(10), eq(1));
        verify(userInfoMapper).restoreBalance(userId, 10);

        // 不走新流程
        verify(settlementService, never()).freezeForRefund(anyLong(), anyLong(), anyString());
    }

    private UserConsumeLog buildConsumeLog(Integer payType, int amount) {
        UserConsumeLog log = new UserConsumeLog();
        log.setId(consumeLogId);
        log.setUserId(userId);
        log.setAuthorId(authorId);
        log.setAmount(amount);
        log.setProductType(0);
        log.setProductId(chapterId);
        log.setProducName("VIP测试章节");
        log.setProducValue(1);
        log.setPayType(payType);
        log.setRefundStatus(0);
        log.setCreateTime(LocalDateTime.now());
        log.setUpdateTime(LocalDateTime.now());
        return log;
    }
}
