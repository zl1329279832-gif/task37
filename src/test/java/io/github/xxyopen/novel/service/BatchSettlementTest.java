package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.core.config.FinanceProperties;
import io.github.xxyopen.novel.dao.entity.*;
import io.github.xxyopen.novel.dao.mapper.*;
import io.github.xxyopen.novel.service.impl.SettlementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 批量结算测试
 * 场景：正确拆分、创建日收入、标记已结算、空列表不操作、并发取消跳过、旧记录兼容
 */
@ExtendWith(MockitoExtension.class)
class BatchSettlementTest {

    @InjectMocks
    private SettlementServiceImpl settlementService;

    @Mock private PendingSettlementMapper pendingSettlementMapper;
    @Mock private SettlementBatchMapper settlementBatchMapper;
    @Mock private RefundFreezeMapper refundFreezeMapper;
    @Mock private AuthorIncomeBreakdownMapper authorIncomeBreakdownMapper;
    @Mock private AuthorIncomeDetailMapper authorIncomeDetailMapper;

    @BeforeEach
    void setUp() throws Exception {
        FinanceProperties props = new FinanceProperties();
        props.setRefundFreezeDays(7);
        props.setSettlementBatchSize(500);
        var field = SettlementServiceImpl.class.getDeclaredField("financeProperties");
        field.setAccessible(true);
        field.set(settlementService, props);
    }

    @Test
    void testBatch_correctSplit() {
        // 准备3条待结算记录：1条章节购买、1条会员免费读、1条平台活动
        PendingSettlement ps1 = buildPendingSettlement(1L, 5L, 10L, 100L, 0, 10);
        PendingSettlement ps2 = buildPendingSettlement(2L, 5L, 10L, 101L, 1, 5);
        PendingSettlement ps3 = buildPendingSettlement(3L, 5L, 10L, 102L, 2, 3);
        List<PendingSettlement> settleable = List.of(ps1, ps2, ps3);

        when(pendingSettlementMapper.selectSettleable(500)).thenReturn(settleable);
        when(pendingSettlementMapper.casMarkSettled(anyLong(), anyLong())).thenReturn(1);
        when(settlementBatchMapper.insert(any())).thenAnswer(invocation -> {
            SettlementBatch batch = invocation.getArgument(0);
            batch.setId(1L);
            return 1;
        });
        when(settlementBatchMapper.updateById(any())).thenReturn(1);
        when(authorIncomeBreakdownMapper.insert(any())).thenReturn(1);
        doNothing().when(authorIncomeDetailMapper).upsertDailyIncome(
                anyLong(), anyLong(), any(), anyInt(), anyInt(), anyInt());

        settlementService.executeBatchSettlement();

        // 验证创建了3条收入拆分
        verify(authorIncomeBreakdownMapper, times(3)).insert(any(AuthorIncomeBreakdown.class));

        // 验证批次总金额
        ArgumentCaptor<SettlementBatch> batchCaptor = ArgumentCaptor.forClass(SettlementBatch.class);
        verify(settlementBatchMapper).updateById(batchCaptor.capture());
        SettlementBatch updatedBatch = batchCaptor.getValue();
        assertEquals(18, updatedBatch.getTotalAmount());
        assertEquals(10, updatedBatch.getChapterPurchaseAmount());
        assertEquals(5, updatedBatch.getMemberSubsidyAmount());
        assertEquals(3, updatedBatch.getPlatformSubsidyAmount());
        assertEquals(1, updatedBatch.getStatus()); // 已完成
    }

    @Test
    void testBatch_createsDailyIncome() {
        PendingSettlement ps1 = buildPendingSettlement(1L, 5L, 10L, 100L, 0, 10);
        when(pendingSettlementMapper.selectSettleable(500)).thenReturn(List.of(ps1));
        when(pendingSettlementMapper.casMarkSettled(anyLong(), anyLong())).thenReturn(1);
        when(settlementBatchMapper.insert(any())).thenAnswer(inv -> {
            SettlementBatch b = inv.getArgument(0);
            b.setId(1L);
            return 1;
        });
        when(settlementBatchMapper.updateById(any())).thenReturn(1);
        when(authorIncomeBreakdownMapper.insert(any())).thenReturn(1);

        settlementService.executeBatchSettlement();

        // 验证 upsertDailyIncome 被调用
        verify(authorIncomeDetailMapper).upsertDailyIncome(
                eq(5L), eq(10L), any(), eq(10), eq(1), eq(1));
    }

    @Test
    void testBatch_marksSettled() {
        PendingSettlement ps1 = buildPendingSettlement(1L, 5L, 10L, 100L, 0, 10);
        when(pendingSettlementMapper.selectSettleable(500)).thenReturn(List.of(ps1));
        when(pendingSettlementMapper.casMarkSettled(anyLong(), anyLong())).thenReturn(1);
        when(settlementBatchMapper.insert(any())).thenAnswer(inv -> {
            SettlementBatch b = inv.getArgument(0);
            b.setId(1L);
            return 1;
        });
        when(settlementBatchMapper.updateById(any())).thenReturn(1);
        when(authorIncomeBreakdownMapper.insert(any())).thenReturn(1);

        settlementService.executeBatchSettlement();

        verify(pendingSettlementMapper).casMarkSettled(eq(1L), eq(1L));
    }

    @Test
    void testBatch_emptyList_noop() {
        when(pendingSettlementMapper.selectSettleable(500)).thenReturn(Collections.emptyList());

        settlementService.executeBatchSettlement();

        // 不创建批次
        verify(settlementBatchMapper, never()).insert(any());
        verify(authorIncomeBreakdownMapper, never()).insert(any());
    }

    @Test
    void testBatch_concurrentCancel() {
        PendingSettlement ps1 = buildPendingSettlement(1L, 5L, 10L, 100L, 0, 10);
        when(pendingSettlementMapper.selectSettleable(500)).thenReturn(List.of(ps1));
        // CAS 失败（已被退款取消）
        when(pendingSettlementMapper.casMarkSettled(anyLong(), anyLong())).thenReturn(0);
        when(settlementBatchMapper.insert(any())).thenAnswer(inv -> {
            SettlementBatch b = inv.getArgument(0);
            b.setId(1L);
            return 1;
        });
        when(settlementBatchMapper.updateById(any())).thenReturn(1);

        settlementService.executeBatchSettlement();

        // 验证跳过，不创建收入拆分
        verify(authorIncomeBreakdownMapper, never()).insert(any());
    }

    @Test
    void testCancel_legacyRecord_noop() {
        // 旧记录无待结算
        when(pendingSettlementMapper.selectByConsumeLogId(anyLong())).thenReturn(null);

        settlementService.cancelPendingSettlement(999L);

        verify(pendingSettlementMapper, never()).casMarkCancelled(anyLong());
        verify(refundFreezeMapper, never()).casMarkRefunded(anyLong());
    }

    @Test
    void testCancel_success() {
        PendingSettlement ps = buildPendingSettlement(1L, 5L, 10L, 100L, 0, 10);
        ps.setConsumeLogId(999L);
        when(pendingSettlementMapper.selectByConsumeLogId(999L)).thenReturn(ps);
        when(pendingSettlementMapper.casMarkCancelled(1L)).thenReturn(1);
        when(refundFreezeMapper.casMarkRefunded(999L)).thenReturn(1);

        settlementService.cancelPendingSettlement(999L);

        verify(pendingSettlementMapper).casMarkCancelled(1L);
        verify(refundFreezeMapper).casMarkRefunded(999L);
    }

    @Test
    void testCreatePendingSettlement_withConsumeLog() {
        when(pendingSettlementMapper.insert(any())).thenReturn(1);
        when(refundFreezeMapper.insert(any())).thenReturn(1);

        PendingSettlement result = settlementService.createPendingSettlement(
                1L, 5L, 10L, 100L, 999L, 0, 10, 2, 1, 7);

        assertNotNull(result);
        assertEquals(0, result.getStatus());
        verify(pendingSettlementMapper).insert(any(PendingSettlement.class));
        verify(refundFreezeMapper).insert(any(RefundFreeze.class));
    }

    @Test
    void testCreatePendingSettlement_noConsumeLog() {
        when(pendingSettlementMapper.insert(any())).thenReturn(1);

        PendingSettlement result = settlementService.createPendingSettlement(
                1L, 5L, 10L, 100L, null, 1, 10, 10, 0, 5);

        assertNotNull(result);
        verify(pendingSettlementMapper).insert(any(PendingSettlement.class));
        // 无消费记录不创建退款冻结
        verify(refundFreezeMapper, never()).insert(any());
    }

    private PendingSettlement buildPendingSettlement(
            Long id, Long authorId, Long bookId, Long chapterId, int type, int amount) {
        PendingSettlement ps = new PendingSettlement();
        ps.setId(id);
        ps.setUserId(1L);
        ps.setAuthorId(authorId);
        ps.setBookId(bookId);
        ps.setChapterId(chapterId);
        ps.setConsumeLogId(id * 10);
        ps.setSettlementType(type);
        ps.setOriginalAmount(amount);
        ps.setDiscountAmount(0);
        ps.setCouponAmount(0);
        ps.setActualAmount(amount);
        ps.setStatus(0);
        ps.setFreezeEndTime(LocalDateTime.now().minusHours(1));
        ps.setCreateTime(LocalDateTime.now());
        ps.setUpdateTime(LocalDateTime.now());
        return ps;
    }

}
