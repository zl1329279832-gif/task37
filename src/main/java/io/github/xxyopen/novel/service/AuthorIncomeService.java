package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.dto.resp.AuthorIncomeDetailRespDto;
import io.github.xxyopen.novel.dto.resp.AuthorIncomeRespDto;

import java.time.LocalDate;
import java.util.List;

/**
 * 作家收入 服务接口
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
public interface AuthorIncomeService {

    /**
     * 查询作者每日收入明细（按书籍、按日期）
     *
     * @param authorId  作家ID
     * @param bookId    小说ID（null 或 0 表示全部）
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 每日收入明细列表
     */
    RestResp<List<AuthorIncomeDetailRespDto>> listDailyIncomeDetails(
            Long authorId, Long bookId, LocalDate startDate, LocalDate endDate);

    /**
     * 查询作者月度结算记录
     *
     * @param authorId 作家ID
     * @param year     年份（null 表示全部）
     * @return 月度结算列表
     */
    RestResp<List<AuthorIncomeRespDto>> listMonthlySettlements(Long authorId, Integer year);

    /**
     * 确认稿费
     *
     * @param authorId 作家ID
     * @param incomeId 结算记录ID
     * @return 操作结果
     */
    RestResp<Void> confirmIncome(Long authorId, Long incomeId);

}
