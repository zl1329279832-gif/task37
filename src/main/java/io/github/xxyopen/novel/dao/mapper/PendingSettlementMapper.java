package io.github.xxyopen.novel.dao.mapper;

import io.github.xxyopen.novel.dao.entity.PendingSettlement;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 待结算流水 Mapper 接口
 */
public interface PendingSettlementMapper extends BaseMapper<PendingSettlement> {

    /**
     * CAS 冻结待结算流水（退款时）
     */
    @Update("UPDATE pending_settlement SET status = 1, update_time = NOW() " +
            "WHERE id = #{id} AND status = 0")
    int casFreeze(@Param("id") Long id);

    /**
     * CAS 标记为已结算
     */
    @Update("UPDATE pending_settlement SET status = 2, settled_batch_id = #{batchId}, " +
            "update_time = NOW() WHERE id = #{id} AND status = 0")
    int casSettle(@Param("id") Long id, @Param("batchId") Long batchId);

    /**
     * CAS 取消（退款完成后）
     */
    @Update("UPDATE pending_settlement SET status = 3, update_time = NOW() " +
            "WHERE id = #{id} AND status = 1")
    int casCancel(@Param("id") Long id);

}
