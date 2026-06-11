package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.dto.req.AuthorRegisterReqDto;
import io.github.xxyopen.novel.dto.resp.AuthorIncomeDetailRespDto;
import io.github.xxyopen.novel.dto.resp.AuthorIncomeRespDto;

import java.time.LocalDate;
import java.util.List;

/**
 * 作家模块 业务服务类
 *
 * @author xiongxiaoyang
 * @date 2022/5/23
 */
public interface AuthorService {

    /**
     * 作家注册
     *
     * @param dto 注册参数
     * @return void
     */
    RestResp<Void> register(AuthorRegisterReqDto dto);

    /**
     * 查询作者每日收入明细
     *
     * @param authorId  作者ID
     * @param bookId    小说ID（0或null表示全部）
     * @param startDate 起始日期
     * @param endDate   截止日期
     * @return 收入明细列表
     */
    RestResp<List<AuthorIncomeDetailRespDto>> listDailyIncome(Long authorId, Long bookId,
                                                              LocalDate startDate, LocalDate endDate);

    /**
     * 查询作者月度结算列表
     *
     * @param authorId 作者ID
     * @return 月度结算列表
     */
    RestResp<List<AuthorIncomeRespDto>> listMonthlyIncome(Long authorId);
}
