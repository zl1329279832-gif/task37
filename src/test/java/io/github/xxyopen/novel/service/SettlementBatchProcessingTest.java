package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.core.config.FinanceProperties;
import io.github.xxyopen.novel.dao.entity.PendingSettlement;
import io.github.xxyopen.novel.dao.entity.SettlementBatch;
import io.github.xxyopen.novel.dao.mapper.*;
import io.github.xxyopen.novel.service.impl.SettlementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 结算批处理测试
 */
@ExtendWith(MockitoExtension.class)
class SettlementBatchProcessingTest {

    @InjectMocks
    private SettlementServiceImpl settlementService;

    @Mock private PendingSettlementMapper pendingSettlementMapper;
    @Mock private SettlementBatchMapper settlementBatchMapper;
    @Mock private RefundFreezeMapper refundFreezeMapper;
    @Mock private AuthorIncomeDetailMapper authorIncomeDetailMapper;
    @Mock private AuthorIncomeMapper authorIncomeMapper;

    @BeforeEach
    void setUp() throws Exception {
        FinanceProperties props = new FinanceProperties();
        props.setRefundWindowHours(72);
        props.setTaxRate(20);
        var field = SettlementServiceImpl.class.getDeclaredField("financeProperties");
        field.setAccessible(true);
        field.set(settlementService, props);
    }

    @Test
    void testBatchSettlement_settlesExpiredPendingRecords() {
        PendingSettlement ps1 = buildPending(1L, 101L, 5L, 10L, 10, 0, 0, 10);
        PendingSettlement ps2 = buildPending(2L, 102L, 5L, 10L, 8, 1, 0, 9);

        when(pendingSettlementMapper.selectList(any())).thenReturn(Arrays.asList(ps1, ps2));

        // Simulate auto-id on batch insert
        doAnswer(inv -> {
            SettlementBatch batch = inv.getArgument(0);
            batch.setId(999L);
            return 1;
        }).when(settlementBatchMapper).insert(any(SettlementBatch.class));

        when(pendingSettlementMapper.casSettle(eq(1L), eq(999L))).thenReturn(1);
        when(pendingSettlementMapper.casSettle(eq(2L), eq(999L))).thenReturn(1);

        settlementService.executeBatchSettlement();

        verify(settlementBatchMapper).insert(any(SettlementBatch.class));
        verify(pendingSettlementMapper).casSettle(eq(1L), eq(999L));
        verify(pendingSettlementMapper).casSettle(eq(2L), eq(999L));

        // Both settled: authorShare = 10 + 9 = 19, count = 2
        verify(authorIncomeDetailMapper).upsertDailyIncome(
                eq(5L), eq(10L), any(), eq(19), eq(2), eq(0));

        // Batch updated with totals
        verify(settlementBatchMapper).updateById(argThat(batch ->
                batch.getRecordCount() == 2 && batch.getStatus() == 1));
    }

    @Test
    void testBatchSettlement_noPendingRecords_noop() {
        when(pendingSettlementMapper.selectList(any())).thenReturn(Collections.emptyList());

        settlementService.executeBatchSettlement();

        verify(settlementBatchMapper, never()).insert(any());
        verify(authorIncomeDetailMapper, never()).upsertDailyIncome(
                anyLong(), anyLong(), any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void testBatchSettlement_multipleAuthors_groupedCorrectly() {
        PendingSettlement ps1 = buildPending(1L, 101L, 5L, 10L, 10, 0, 0, 10);
        PendingSettlement ps2 = buildPending(2L, 102L, 8L, 20L, 15, 0, 0, 15);

        when(pendingSettlementMapper.selectList(any())).thenReturn(Arrays.asList(ps1, ps2));

        doAnswer(inv -> {
            SettlementBatch batch = inv.getArgument(0);
            batch.setId(999L);
            return 1;
        }).when(settlementBatchMapper).insert(any(SettlementBatch.class));

        when(pendingSettlementMapper.casSettle(anyLong(), eq(999L))).thenReturn(1);

        settlementService.executeBatchSettlement();

        // Two separate groups, each with 1 record
        verify(authorIncomeDetailMapper).upsertDailyIncome(
                eq(5L), eq(10L), any(), eq(10), eq(1), eq(0));
        verify(authorIncomeDetailMapper).upsertDailyIncome(
                eq(8L), eq(20L), any(), eq(15), eq(1), eq(0));
    }

    @Test
    void testBatchSettlement_frozenRecord_skipped() {
        PendingSettlement ps1 = buildPending(1L, 101L, 5L, 10L, 10, 0, 0, 10);
        PendingSettlement ps2 = buildPending(2L, 102L, 5L, 10L, 8, 1, 0, 9);

        when(pendingSettlementMapper.selectList(any())).thenReturn(Arrays.asList(ps1, ps2));

        doAnswer(inv -> {
            SettlementBatch batch = inv.getArgument(0);
            batch.setId(999L);
            return 1;
        }).when(settlementBatchMapper).insert(any(SettlementBatch.class));

        when(pendingSettlementMapper.casSettle(eq(1L), eq(999L))).thenReturn(1);
        when(pendingSettlementMapper.casSettle(eq(2L), eq(999L))).thenReturn(0); // frozen

        settlementService.executeBatchSettlement();

        // Only ps1 settled: authorShare=10, count=1
        verify(authorIncomeDetailMapper).upsertDailyIncome(
                eq(5L), eq(10L), any(), eq(10), eq(1), eq(0));

        verify(settlementBatchMapper).updateById(argThat(batch ->
                batch.getRecordCount() == 1));
    }

    @Test
    void testBatchSettlement_withMembershipSubsidy_includedInAuthorShare() {
        // 会员折扣：实付8，补贴1，作者得9
        PendingSettlement ps = buildPending(1L, 101L, 5L, 10L, 8, 1, 0, 9);

        when(pendingSettlementMapper.selectList(any())).thenReturn(List.of(ps));

        doAnswer(inv -> {
            SettlementBatch batch = inv.getArgument(0);
            batch.setId(999L);
            return 1;
        }).when(settlementBatchMapper).insert(any(SettlementBatch.class));

        when(pendingSettlementMapper.casSettle(eq(1L), eq(999L))).thenReturn(1);

        settlementService.executeBatchSettlement();

        // Author gets authorShare=9 (user paid 8 + platform subsidy 1)
        verify(authorIncomeDetailMapper).upsertDailyIncome(
                eq(5L), eq(10L), any(), eq(9), eq(1), eq(0));
    }

    @Test
    void testCreatePendingSettlement_calculatesAuthorShareCorrectly() {
        PendingSettlement result = settlementService.createPendingSettlement(
                1L, 1L, 5L, 10L, 100L,
                8, 2, 1, 2);

        assertEquals(11, result.getAuthorShare()); // 8 + 1 + 2
        assertEquals(0, result.getStatus());
        assertNotNull(result.getRefundWindowEnd());
    }

    private PendingSettlement buildPending(Long id, Long consumeLogId, Long authorId,
                                           Long bookId, int amount, int membershipSubsidy,
                                           int platformSubsidy, int authorShare) {
        PendingSettlement ps = new PendingSettlement();
        ps.setId(id);
        ps.setConsumeLogId(consumeLogId);
        ps.setUserId(1L);
        ps.setAuthorId(authorId);
        ps.setBookId(bookId);
        ps.setChapterId(100L + id);
        ps.setAmount(amount);
        ps.setPayType(0);
        ps.setMembershipSubsidy(membershipSubsidy);
        ps.setPlatformSubsidy(platformSubsidy);
        ps.setAuthorShare(authorShare);
        ps.setStatus(0);
        ps.setRefundWindowEnd(LocalDateTime.now().minusHours(1));
        ps.setCreateTime(LocalDateTime.now().minusDays(4));
        ps.setUpdateTime(LocalDateTime.now().minusDays(4));
        return ps;
    }
}
