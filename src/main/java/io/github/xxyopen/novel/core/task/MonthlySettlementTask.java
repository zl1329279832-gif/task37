package io.github.xxyopen.novel.core.task;

import io.github.xxyopen.novel.core.config.FinanceProperties;
import io.github.xxyopen.novel.dao.entity.AuthorIncome;
import io.github.xxyopen.novel.dao.mapper.AuthorIncomeDetailMapper;
import io.github.xxyopen.novel.dao.mapper.AuthorIncomeMapper;
import io.github.xxyopen.novel.dto.AuthorBookIncomePair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 月度结算定时任务
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MonthlySettlementTask {

    private final AuthorIncomeDetailMapper authorIncomeDetailMapper;

    private final AuthorIncomeMapper authorIncomeMapper;

    private final FinanceProperties financeProperties;

    /**
     * 每月1日凌晨1点执行上月结算
     */
    @Scheduled(cron = "0 0 1 1 * ?")
    public void executeMonthlySettlement() {
        // 计算上月的起止日期
        LocalDate today = LocalDate.now();
        LocalDate lastMonthStart = today.withDayOfMonth(1).minusMonths(1);
        LocalDate lastMonthEnd = lastMonthStart.withDayOfMonth(lastMonthStart.lengthOfMonth());

        log.info("开始执行月度结算, 结算月份: {} ~ {}", lastMonthStart, lastMonthEnd);

        // 1. 查询上月有收入的所有 (authorId, bookId) 对
        List<AuthorBookIncomePair> pairs = authorIncomeDetailMapper
                .selectDistinctAuthorBookPairs(lastMonthStart, lastMonthEnd);

        int successCount = 0;
        int failCount = 0;

        for (AuthorBookIncomePair pair : pairs) {
            try {
                settleOneAuthorBook(pair, lastMonthStart, lastMonthEnd);
                successCount++;
            } catch (Exception e) {
                log.error("结算失败: authorId={}, bookId={}", pair.getAuthorId(), pair.getBookId(), e);
                failCount++;
            }
        }

        log.info("月度结算完成: 成功={}, 失败={}", successCount, failCount);
    }

    /**
     * 手动触发月度结算（用于测试或补结算）
     */
    public void executeMonthlySettlementForMonth(int year, int month) {
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());

        log.info("手动触发月度结算, 结算月份: {} ~ {}", monthStart, monthEnd);

        List<AuthorBookIncomePair> pairs = authorIncomeDetailMapper
                .selectDistinctAuthorBookPairs(monthStart, monthEnd);

        for (AuthorBookIncomePair pair : pairs) {
            settleOneAuthorBook(pair, monthStart, monthEnd);
        }

        log.info("手动月度结算完成");
    }

    @Transactional(rollbackFor = Exception.class)
    protected void settleOneAuthorBook(AuthorBookIncomePair pair,
                                       LocalDate monthStart, LocalDate monthEnd) {
        // 0. 幂等检查：是否已存在该月度结算记录
        int existing = authorIncomeMapper.countByAuthorBookMonth(
                pair.getAuthorId(), pair.getBookId(), monthStart);
        if (existing > 0) {
            log.info("结算记录已存在，跳过: authorId={}, bookId={}, month={}",
                    pair.getAuthorId(), pair.getBookId(), monthStart);
            return;
        }

        // 1. 汇总该作者+作品在月度内的日收入
        Integer totalIncome = authorIncomeDetailMapper.sumIncomeForMonth(
                pair.getAuthorId(), pair.getBookId(), monthStart, monthEnd);

        if (totalIncome == null || totalIncome <= 0) {
            return;
        }

        // 2. 计算税前/税后收入
        int preTaxIncome = totalIncome;
        int afterTaxIncome = preTaxIncome * (100 - financeProperties.getTaxRate()) / 100;

        // 3. 插入结算记录
        AuthorIncome income = new AuthorIncome();
        income.setAuthorId(pair.getAuthorId());
        income.setBookId(pair.getBookId());
        income.setIncomeMonth(monthStart);
        income.setPreTaxIncome(preTaxIncome);
        income.setAfterTaxIncome(afterTaxIncome);
        income.setPayStatus(0);      // 待支付
        income.setConfirmStatus(0);  // 待确认
        income.setCreateTime(LocalDateTime.now());
        income.setUpdateTime(LocalDateTime.now());
        authorIncomeMapper.insert(income);

        log.info("结算成功: authorId={}, bookId={}, 税前={}, 税后={}",
                pair.getAuthorId(), pair.getBookId(), preTaxIncome, afterTaxIncome);
    }

}
