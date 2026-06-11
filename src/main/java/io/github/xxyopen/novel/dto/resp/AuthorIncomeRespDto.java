package io.github.xxyopen.novel.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 作者月度收入 响应DTO
 *
 * @author xiongxiaoyang
 * @date 2022/5/23
 */
@Data
@Builder
public class AuthorIncomeRespDto {

    /**
     * 作家ID
     */
    private Long authorId;

    /**
     * 小说ID
     */
    private Long bookId;

    /**
     * 小说名称
     */
    private String bookName;

    /**
     * 收入月份
     */
    @JsonFormat(pattern = "yyyy-MM")
    private LocalDate incomeMonth;

    /**
     * 税前收入;单位：分
     */
    private Integer preTaxIncome;

    /**
     * 税后收入;单位：分
     */
    private Integer afterTaxIncome;

    /**
     * 支付状态;0-待支付 1-已支付
     */
    private Integer payStatus;

    /**
     * 稿费确认状态;0-待确认 1-已确认
     */
    private Integer confirmStatus;

}
