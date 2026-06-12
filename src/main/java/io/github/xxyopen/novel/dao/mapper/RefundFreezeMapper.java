package io.github.xxyopen.novel.dao.mapper;

import io.github.xxyopen.novel.dao.entity.RefundFreeze;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 退款冻结 Mapper 接口
 */
public interface RefundFreezeMapper extends BaseMapper<RefundFreeze> {

    /**
     * 根据消费记录ID查询冻结记录
     */
    @Select("SELECT * FROM refund_freeze WHERE consume_log_id = #{consumeLogId} LIMIT 1")
    RefundFreeze selectByConsumeLogId(@Param("consumeLogId") Long consumeLogId);

    /**
     * CAS 标记已退款
     */
    @Update("UPDATE refund_freeze SET status = 2, update_time = NOW() " +
            "WHERE consume_log_id = #{consumeLogId} AND status = 0")
    int casMarkRefunded(@Param("consumeLogId") Long consumeLogId);

    /**
     * CAS 标记已解冻（冻结到期自动解冻）
     */
    @Update("UPDATE refund_freeze SET status = 1, update_time = NOW() " +
            "WHERE consume_log_id = #{consumeLogId} AND status = 0 " +
            "AND freeze_end_time <= NOW()")
    int casMarkUnfrozen(@Param("consumeLogId") Long consumeLogId);

}
