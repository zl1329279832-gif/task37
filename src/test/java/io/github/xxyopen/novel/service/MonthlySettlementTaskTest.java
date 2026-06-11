package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.core.config.FinanceProperties;
import io.github.xxyopen.novel.core.task.MonthlySettlementTask;
import io.github.xxyopen.novel.dao.entity.AuthorIncome;
import io.github.xxyopen.novel.dao.mapper.AuthorIncomeDetailMapper;
import io.github.xxyopen.novel.dao.mapper.AuthorIncomeMapper;
import io.github.xxyopen.novel.dto.AuthorBookIncomePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 月度结算测试
 * 场景：正确计算税前/税后收入、幂等性、多书分别结算
 */
@ExtendWith(MockitoExtension.class)
class MonthlySettlementTaskTest {

    @InjectMocks
    private MonthlySettlementTask monthlySettlementTask;

    @Mock
    private AuthorIncomeDetailMapper authorIncomeDetailMapper;

    @Mock
    private AuthorIncomeMapper authorIncomeMapper;

    @BeforeEach
    void setUp() throws Exception {
        FinanceProperties props = new FinanceProperties();
        props.setDefaultChapterPrice(10);
        props.setTaxRate(20); // 20% 税率
        var field = MonthlySettlementTask.class.getDeclaredField("financeProperties");
        field.setAccessible(true);
        field.set(monthlySettlementTask, props);
    }

    @Test
    void testMonthlySettlement_correctTaxCalculation() {
        int year = 2026;
        int month = 3;
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = LocalDate.of(year, month, 31);

        Long authorId = 5L;
        Long bookId = 10L;

        // 模拟：该作者+作品在3月有收入
        AuthorBookIncomePair pair = new AuthorBookIncomePair();
        pair.setAuthorId(authorId);
        pair.setBookId(bookId);
        when(authorIncomeDetailMapper.selectDistinctAuthorBookPairs(
                eq(monthStart), eq(monthEnd))).thenReturn(List.of(pair));

        // 无已有结算记录
        when(authorIncomeMapper.countByAuthorBookMonth(eq(authorId), eq(bookId), eq(monthStart)))
                .thenReturn(0);

        // 总收入 1500 屋币
        when(authorIncomeDetailMapper.sumIncomeForMonth(
                eq(authorId), eq(bookId), eq(monthStart), eq(monthEnd)))
                .thenReturn(1500);

        when(authorIncomeMapper.insert(any())).thenReturn(1);

        // 手动触发3月结算
        monthlySettlementTask.executeMonthlySettlementForMonth(year, month);

        // 验证插入的结算记录
        ArgumentCaptor<AuthorIncome> captor = ArgumentCaptor.forClass(AuthorIncome.class);
        verify(authorIncomeMapper).insert(captor.capture());

        AuthorIncome income = captor.getValue();
        assertEquals(authorId, income.getAuthorId());
        assertEquals(bookId, income.getBookId());
        assertEquals(monthStart, income.getIncomeMonth());
        assertEquals(1500, income.getPreTaxIncome(), "税前收入应为1500");
        assertEquals(1200, income.getAfterTaxIncome(), "税后收入应为1200（1500 * 80%）");
        assertEquals(0, income.getPayStatus(), "支付状态应为待支付");
        assertEquals(0, income.getConfirmStatus(), "确认状态应为待确认");
    }

    @Test
    void testMonthlySettlement_multipleBooks_separateRecords() {
        int year = 2026;
        int month = 3;
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = LocalDate.of(year, month, 31);

        Long authorId = 5L;

        // 作者有2本书
        AuthorBookIncomePair pair1 = new AuthorBookIncomePair();
        pair1.setAuthorId(authorId);
        pair1.setBookId(10L);

        AuthorBookIncomePair pair2 = new AuthorBookIncomePair();
        pair2.setAuthorId(authorId);
        pair2.setBookId(20L);

        when(authorIncomeDetailMapper.selectDistinctAuthorBookPairs(
                eq(monthStart), eq(monthEnd))).thenReturn(List.of(pair1, pair2));

        // 无已有结算记录
        when(authorIncomeMapper.countByAuthorBookMonth(eq(authorId), eq(10L), eq(monthStart)))
                .thenReturn(0);
        when(authorIncomeMapper.countByAuthorBookMonth(eq(authorId), eq(20L), eq(monthStart)))
                .thenReturn(0);

        // 书1收入 1000，书2收入 500
        when(authorIncomeDetailMapper.sumIncomeForMonth(
                eq(authorId), eq(10L), eq(monthStart), eq(monthEnd))).thenReturn(1000);
        when(authorIncomeDetailMapper.sumIncomeForMonth(
                eq(authorId), eq(20L), eq(monthStart), eq(monthEnd))).thenReturn(500);

        when(authorIncomeMapper.insert(any())).thenReturn(1);

        monthlySettlementTask.executeMonthlySettlementForMonth(year, month);

        // 验证生成了2条结算记录
        ArgumentCaptor<AuthorIncome> captor = ArgumentCaptor.forClass(AuthorIncome.class);
        verify(authorIncomeMapper, times(2)).insert(captor.capture());

        List<AuthorIncome> incomes = captor.getAllValues();
        assertEquals(2, incomes.size());

        // 验证书1
        AuthorIncome income1 = incomes.stream()
                .filter(i -> i.getBookId().equals(10L)).findFirst().orElseThrow();
        assertEquals(1000, income1.getPreTaxIncome());
        assertEquals(800, income1.getAfterTaxIncome());

        // 验证书2
        AuthorIncome income2 = incomes.stream()
                .filter(i -> i.getBookId().equals(20L)).findFirst().orElseThrow();
        assertEquals(500, income2.getPreTaxIncome());
        assertEquals(400, income2.getAfterTaxIncome());
    }

    @Test
    void testMonthlySettlement_zeroIncome_noRecord() {
        int year = 2026;
        int month = 3;
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = LocalDate.of(year, month, 31);

        // 有 (authorId, bookId) 对，但收入为0
        AuthorBookIncomePair pair = new AuthorBookIncomePair();
        pair.setAuthorId(5L);
        pair.setBookId(10L);
        when(authorIncomeDetailMapper.selectDistinctAuthorBookPairs(
                eq(monthStart), eq(monthEnd))).thenReturn(List.of(pair));

        when(authorIncomeMapper.countByAuthorBookMonth(eq(5L), eq(10L), eq(monthStart)))
                .thenReturn(0);

        when(authorIncomeDetailMapper.sumIncomeForMonth(
                anyLong(), anyLong(), any(), any())).thenReturn(0);

        monthlySettlementTask.executeMonthlySettlementForMonth(year, month);

        // 收入为0不应插入结算记录
        verify(authorIncomeMapper, never()).insert(any());
    }

    @Test
    void testMonthlySettlement_noAuthors_noop() {
        int year = 2026;
        int month = 3;
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = LocalDate.of(year, month, 31);

        // 上月无任何收入记录
        when(authorIncomeDetailMapper.selectDistinctAuthorBookPairs(
                eq(monthStart), eq(monthEnd))).thenReturn(List.of());

        monthlySettlementTask.executeMonthlySettlementForMonth(year, month);

        // 不应有任何结算操作
        verify(authorIncomeMapper, never()).insert(any());
        verify(authorIncomeDetailMapper, never()).sumIncomeForMonth(anyLong(), anyLong(), any(), any());
    }

    @Test
    void testTaxRate_variousRates() throws Exception {
        // 测试不同税率
        int[][] testCases = {
                {1000, 0, 1000},    // 0% 税率
                {1000, 10, 900},    // 10% 税率
                {1000, 20, 800},    // 20% 税率
                {1000, 50, 500},    // 50% 税率
                {1500, 20, 1200},   // 20% 税率，1500屋币
        };

        for (int[] tc : testCases) {
            int totalIncome = tc[0];
            int taxRate = tc[1];
            int expectedAfterTax = tc[2];

            // 重设税率
            FinanceProperties props = new FinanceProperties();
            props.setTaxRate(taxRate);
            var field = MonthlySettlementTask.class.getDeclaredField("financeProperties");
            field.setAccessible(true);
            field.set(monthlySettlementTask, props);

            LocalDate monthStart = LocalDate.of(2026, 1, 1);
            LocalDate monthEnd = LocalDate.of(2026, 1, 31);

            AuthorBookIncomePair pair = new AuthorBookIncomePair();
            pair.setAuthorId(1L);
            pair.setBookId(1L);
            when(authorIncomeDetailMapper.selectDistinctAuthorBookPairs(
                    eq(monthStart), eq(monthEnd))).thenReturn(List.of(pair));
            when(authorIncomeDetailMapper.sumIncomeForMonth(
                    eq(1L), eq(1L), eq(monthStart), eq(monthEnd))).thenReturn(totalIncome);
            when(authorIncomeMapper.countByAuthorBookMonth(eq(1L), eq(1L), eq(monthStart)))
                    .thenReturn(0);
            when(authorIncomeMapper.insert(any())).thenReturn(1);

            monthlySettlementTask.executeMonthlySettlementForMonth(2026, 1);

            ArgumentCaptor<AuthorIncome> captor = ArgumentCaptor.forClass(AuthorIncome.class);
            verify(authorIncomeMapper, atLeastOnce()).insert(captor.capture());

            AuthorIncome lastIncome = captor.getValue();
            assertEquals(expectedAfterTax, lastIncome.getAfterTaxIncome(),
                    String.format("收入%d，税率%d%%，税后应为%d", totalIncome, taxRate, expectedAfterTax));

            // 重置 mock 用于下次循环
            reset(authorIncomeDetailMapper, authorIncomeMapper);
        }
    }

    @Test
    void testMonthlySettlement_idempotent_skipExistingRecords() {
        int year = 2026;
        int month = 3;
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = LocalDate.of(year, month, 31);

        Long authorId = 5L;
        Long bookId = 10L;

        AuthorBookIncomePair pair = new AuthorBookIncomePair();
        pair.setAuthorId(authorId);
        pair.setBookId(bookId);
        when(authorIncomeDetailMapper.selectDistinctAuthorBookPairs(
                eq(monthStart), eq(monthEnd))).thenReturn(List.of(pair));

        // 已存在结算记录
        when(authorIncomeMapper.countByAuthorBookMonth(eq(authorId), eq(bookId), eq(monthStart)))
                .thenReturn(1);

        monthlySettlementTask.executeMonthlySettlementForMonth(year, month);

        // 不应查询日收入也不应插入新记录
        verify(authorIncomeDetailMapper, never()).sumIncomeForMonth(anyLong(), anyLong(), any(), any());
        verify(authorIncomeMapper, never()).insert(any());
    }

    @Test
    void testMonthlySettlement_negativeIncome_afterRefunds_noRecord() {
        int year = 2026;
        int month = 3;
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = LocalDate.of(year, month, 31);

        AuthorBookIncomePair pair = new AuthorBookIncomePair();
        pair.setAuthorId(5L);
        pair.setBookId(10L);
        when(authorIncomeDetailMapper.selectDistinctAuthorBookPairs(
                eq(monthStart), eq(monthEnd))).thenReturn(List.of(pair));
        when(authorIncomeMapper.countByAuthorBookMonth(anyLong(), anyLong(), any())).thenReturn(0);

        // 全部退款后日收入为负
        when(authorIncomeDetailMapper.sumIncomeForMonth(
                anyLong(), anyLong(), any(), any())).thenReturn(-10);

        monthlySettlementTask.executeMonthlySettlementForMonth(year, month);

        // 负收入不应插入结算记录
        verify(authorIncomeMapper, never()).insert(any());
    }
}
