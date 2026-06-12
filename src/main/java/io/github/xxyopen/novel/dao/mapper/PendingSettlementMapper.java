package io.github.xxyopen.novel.dao.mapper;

import io.github.xxyopen.novel.dao.entity.PendingSettlement;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 待结算流水 Mapper 接口
 */
public interface PendingSettlementMapper extends BaseMapper<PendingSettlement> {

    /**
     * 查询可结算的待结算记录
     */
    @Select("SELECT * FROM pending_settlement " +
            "WHERE status = 0 AND freeze_end_time <= NOW() " +
            "ORDER BY id ASC LIMIT #{batchSize}")
    List<PendingSettlement> selectSettleable(@Param("batchSize") int batchSize);

    /**
     * CAS 标记已结算
     */
    @Update("UPDATE pending_settlement SET status = 1, batch_id = #{batchId}, " +
            "update_time = NOW() WHERE id = #{id} AND status = 0")
    int casMarkSettled(@Param("id") Long id, @Param("batchId") Long batchId);

    /**
     * CAS 标记已取消（退款时）
     */
    @Update("UPDATE pending_settlement SET status = 2, update_time = NOW() " +
            "WHERE id = #{id} AND status = 0")
    int casMarkCancelled(@Param("id") Long id);

    /**
     * 根据消费记录ID查询待结算
     */
    @Select("SELECT * FROM pending_settlement " +
            "WHERE consume_log_id = #{consumeLogId} AND status = 0 LIMIT 1")
    PendingSettlement selectByConsumeLogId(@Param("consumeLogId") Long consumeLogId);

}
