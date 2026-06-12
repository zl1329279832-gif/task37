package io.github.xxyopen.novel.dto.resp;

import lombok.Builder;
import lombok.Data;

/**
 * 待结算汇总 响应DTO
 */
@Data
@Builder
public class PendingSettlementSummaryRespDto {

    /** 待结算总额 */
    private Integer pendingTotalAmount;

    /** 待结算笔数 */
    private Integer pendingCount;

    /** 章节购买金额 */
    private Integer chapterPurchaseAmount;

    /** 会员补贴金额 */
    private Integer memberSubsidyAmount;

    /** 平台补贴金额 */
    private Integer platformSubsidyAmount;

}
