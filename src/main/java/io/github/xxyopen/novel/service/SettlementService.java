package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.dao.entity.PendingSettlement;

/**
 * 结算服务接口
 */
public interface SettlementService {

    /**
     * 创建待结算记录
     */
    PendingSettlement createPendingSettlement(
            Long userId, Long authorId, Long bookId, Long chapterId,
            Long consumeLogId, Integer settlementType,
            Integer originalAmount, Integer discountAmount,
            Integer couponAmount, Integer actualAmount);

    /**
     * 执行批量结算
     */
    void executeBatchSettlement();

    /**
     * 取消待结算记录（退款时）
     */
    void cancelPendingSettlement(Long consumeLogId);

}
