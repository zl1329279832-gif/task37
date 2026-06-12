package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.dao.entity.PendingSettlement;
import io.github.xxyopen.novel.dto.resp.PendingSettlementRespDto;
import io.github.xxyopen.novel.dto.resp.SettlementBatchRespDto;

import java.util.List;

/**
 * 延迟结算服务接口
 */
public interface SettlementService {

    /**
     * 创建待结算流水（购买章节后调用）
     *
     * @param consumeLogId      消费记录ID
     * @param userId            用户ID
     * @param authorId          作者ID
     * @param bookId            小说ID
     * @param chapterId         章节ID
     * @param amount            用户实付金额
     * @param payType           支付方式 0-普通 1-会员免费 2-会员折扣 3-阅读券
     * @param membershipSubsidy 会员补贴金额
     * @param platformSubsidy   平台活动补贴金额
     * @return 待结算记录
     */
    PendingSettlement createPendingSettlement(Long consumeLogId, Long userId, Long authorId,
                                             Long bookId, Long chapterId, int amount,
                                             int payType, int membershipSubsidy, int platformSubsidy);

    /**
     * 退款冻结（退款窗口内的退款操作）
     *
     * @param consumeLogId 消费记录ID
     * @param userId       用户ID
     * @param reason       冻结原因
     * @return 操作结果
     */
    RestResp<Void> freezeForRefund(Long consumeLogId, Long userId, String reason);

    /**
     * 执行结算批处理（退款窗口结束后，将待结算流水入账）
     */
    void executeBatchSettlement();

    /**
     * 查询作者的待结算流水
     *
     * @param authorId 作者ID
     * @param bookId   小说ID（null表示全部）
     * @return 待结算列表
     */
    RestResp<List<PendingSettlementRespDto>> listPendingSettlements(Long authorId, Long bookId);

    /**
     * 查询结算批次列表
     *
     * @param authorId 作者ID
     * @return 批次列表
     */
    RestResp<List<SettlementBatchRespDto>> listSettlementBatches(Long authorId);

    /**
     * 根据消费记录ID查询待结算流水
     *
     * @param consumeLogId 消费记录ID
     * @return 待结算实体
     */
    PendingSettlement getPendingByConsumeLogId(Long consumeLogId);

}
