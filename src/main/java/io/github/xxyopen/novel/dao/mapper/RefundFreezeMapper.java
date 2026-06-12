package io.github.xxyopen.novel.dao.mapper;

import io.github.xxyopen.novel.dao.entity.RefundFreeze;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 退款冻结 Mapper 接口
 */
public interface RefundFreezeMapper extends BaseMapper<RefundFreeze> {

    /**
     * CAS 将冻结状态更新为已退款
     */
    @Update("UPDATE refund_freeze SET status = 1, update_time = NOW() " +
            "WHERE id = #{id} AND status = 0")
    int casRefunded(@Param("id") Long id);

    /**
     * CAS 释放冻结（退款窗口过期自动释放）
     */
    @Update("UPDATE refund_freeze SET status = 2, update_time = NOW() " +
            "WHERE id = #{id} AND status = 0")
    int casRelease(@Param("id") Long id);

}
