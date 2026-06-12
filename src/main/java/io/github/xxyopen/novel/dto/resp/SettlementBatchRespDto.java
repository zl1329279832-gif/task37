package io.github.xxyopen.novel.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 结算批次 响应DTO
 */
@Data
@Builder
public class SettlementBatchRespDto {

    private Long id;

    private String batchNo;

    private LocalDate settlementMonth;

    private Integer totalAmount;

    private Integer totalAuthorShare;

    private Integer totalMembershipSubsidy;

    private Integer totalPlatformSubsidy;

    private Integer recordCount;

    /**
     * 状态;0-处理中 1-已完成 2-失败
     */
    private Integer status;

    private LocalDateTime createTime;

}
