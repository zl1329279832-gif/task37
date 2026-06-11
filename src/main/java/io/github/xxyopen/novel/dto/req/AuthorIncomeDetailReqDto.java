package io.github.xxyopen.novel.dto.req;

import lombok.Data;

/**
 * 作家收入明细查询 请求DTO
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
@Data
public class AuthorIncomeDetailReqDto {

    /**
     * 小说ID，0或null表示全部
     */
    private Long bookId;

    /**
     * 开始日期 yyyy-MM-dd
     */
    private String startDate;

    /**
     * 结束日期 yyyy-MM-dd
     */
    private String endDate;

}
