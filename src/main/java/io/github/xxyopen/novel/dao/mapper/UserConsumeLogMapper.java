package io.github.xxyopen.novel.dao.mapper;

import io.github.xxyopen.novel.dao.entity.UserConsumeLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 * 用户消费记录 Mapper 接口
 * </p>
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
public interface UserConsumeLogMapper extends BaseMapper<UserConsumeLog> {

    /**
     * CAS 原子更新退款状态（防止并发重复退款）
     *
     * @param id 消费记录ID
     * @return 影响行数（0 表示已退款或记录不存在）
     */
    @Update("UPDATE user_consume_log SET refund_status = 1, update_time = NOW() " +
            "WHERE id = #{id} AND refund_status = 0")
    int casSetRefunded(@Param("id") Long id);

}
