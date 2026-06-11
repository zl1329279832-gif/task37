package io.github.xxyopen.novel.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 作家月度结算 响应DTO
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
@Data
@Builder
public class AuthorIncomeRespDto {

    private Long id;

    private Long bookId;

    private String bookName;

    /**
     * 收入月份
     */
    @JsonFormat(pattern = "yyyy-MM")
    private LocalDate incomeMonth;

    /**
     * 税前收入（屋币）
     */
    private Integer preTaxIncome;

    /**
     * 税后收入（屋币）
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
