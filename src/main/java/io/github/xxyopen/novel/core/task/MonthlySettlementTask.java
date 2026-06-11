package io.github.xxyopen.novel.core.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.xxyopen.novel.core.constant.DatabaseConsts;
import io.github.xxyopen.novel.dao.entity.AuthorIncome;
import io.github.xxyopen.novel.dao.entity.AuthorIncomeDetail;
import io.github.xxyopen.novel.dao.mapper.AuthorIncomeDetailMapper;
import io.github.xxyopen.novel.dao.mapper.AuthorIncomeMapper;
import io.github.xxyopen.novel.service.impl.BookPayServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 月度结算定时任务
 * 每月1号凌晨2点执行，生成上月作者收入结算单
 *
 * @author xiongxiaoyang
 * @date 2022/5/23
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MonthlySettlementTask {

    private final AuthorIncomeDetailMapper authorIncomeDetailMapper;

    private final AuthorIncomeMapper authorIncomeMapper;

    /**
     * 每月1号凌晨2点执行月度结算
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    @Transactional(rollbackFor = Exception.class)
    public void executeMonthlySettlement() {
        LocalDate lastMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        doSettlement(lastMonth);
    }

    /**
     * 执行指定月份的结算（可用于手动触发补结算）
     *
     * @param month 结算月份（取月份的第1天）
     */
    public void doSettlement(LocalDate month) {
        LocalDate monthStart = month.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);

        log.info("开始执行月度结算，结算月份：{}", monthStart);

        // 查询该月所有收入明细（排除bookId=0的汇总记录）
        QueryWrapper<AuthorIncomeDetail> detailQuery = new QueryWrapper<>();
        detailQuery.ge(DatabaseConsts.AuthorIncomeDetailTable.COLUMN_INCOME_DATE, monthStart)
                .le(DatabaseConsts.AuthorIncomeDetailTable.COLUMN_INCOME_DATE, monthEnd)
                .ne(DatabaseConsts.AuthorIncomeDetailTable.COLUMN_BOOK_ID, 0);
        List<AuthorIncomeDetail> details = authorIncomeDetailMapper.selectList(detailQuery);

        if (details.isEmpty()) {
            log.info("结算月份 {} 无收入明细，跳过", monthStart);
            return;
        }

        // 按 authorId + bookId 分组汇总
        Map<String, List<AuthorIncomeDetail>> grouped = details.stream()
                .collect(Collectors.groupingBy(d -> d.getAuthorId() + "_" + d.getBookId()));

        for (Map.Entry<String, List<AuthorIncomeDetail>> entry : grouped.entrySet()) {
            List<AuthorIncomeDetail> group = entry.getValue();
            Long authorId = group.get(0).getAuthorId();
            Long bookId = group.get(0).getBookId();

            // 汇总订阅总额
            int totalIncome = group.stream()
                    .mapToInt(AuthorIncomeDetail::getIncomeAccount)
                    .sum();

            // 幂等：检查该月结算记录是否已存在
            QueryWrapper<AuthorIncome> incomeQuery = new QueryWrapper<>();
            incomeQuery.eq(DatabaseConsts.AuthorIncomeTable.COLUMN_AUTHOR_ID, authorId)
                    .eq(DatabaseConsts.AuthorIncomeTable.COLUMN_BOOK_ID, bookId)
                    .eq(DatabaseConsts.AuthorIncomeTable.COLUMN_INCOME_MONTH, monthStart);
            AuthorIncome existing = authorIncomeMapper.selectOne(incomeQuery);

            if (Objects.nonNull(existing)) {
                log.info("作者 {} 小说 {} 月份 {} 结算单已存在，跳过", authorId, bookId, monthStart);
                continue;
            }

            // 计算税前/税后收入（作者分成）
            int preTaxIncome = totalIncome;
            int afterTaxIncome = totalIncome * BookPayServiceImpl.AUTHOR_SHARE_PERCENT / 100;

            // 插入月结记录
            AuthorIncome income = new AuthorIncome();
            income.setAuthorId(authorId);
            income.setBookId(bookId);
            income.setIncomeMonth(monthStart);
            income.setPreTaxIncome(preTaxIncome);
            income.setAfterTaxIncome(afterTaxIncome);
            income.setPayStatus(0);  // 待支付
            income.setConfirmStatus(0);  // 待确认
            income.setCreateTime(LocalDateTime.now());
            income.setUpdateTime(LocalDateTime.now());
            authorIncomeMapper.insert(income);

            log.info("生成结算单：作者={}, 小说={}, 月份={}, 税前={}, 税后={}",
                    authorId, bookId, monthStart, preTaxIncome, afterTaxIncome);
        }

        log.info("月度结算完成，结算月份：{}", monthStart);
    }
}
