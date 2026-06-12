package io.github.xxyopen.novel.dto;

import lombok.Data;

/**
 * 结算聚合（按作者+作品分组后的汇总）
 */
@Data
public class SettlementAggregation {

    private Long authorId;
    private Long bookId;
    private Integer totalAmount;
    private Integer chapterPurchaseAmount;
    private Integer memberSubsidyAmount;
    private Integer platformSubsidyAmount;

}
