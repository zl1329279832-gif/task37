package io.github.xxyopen.novel.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待结算流水 响应DTO
 */
@Data
@Builder
public class PendingSettlementRespDto {

    private Long id;

    private Long consumeLogId;

    private Long bookId;

    private Long chapterId;

    /**
     * 用户实付金额
     */
    private Integer amount;

    /**
     * 支付方式;0-普通 1-会员免费 2-会员折扣 3-阅读券
     */
    private Integer payType;

    /**
     * 会员补贴
     */
    private Integer membershipSubsidy;

    /**
     * 平台活动补贴
     */
    private Integer platformSubsidy;

    /**
     * 作者应得份额
     */
    private Integer authorShare;

    /**
     * 状态;0-待结算 1-退款冻结 2-已结算 3-已取消
     */
    private Integer status;

    private LocalDateTime refundWindowEnd;

    private LocalDateTime createTime;

}
