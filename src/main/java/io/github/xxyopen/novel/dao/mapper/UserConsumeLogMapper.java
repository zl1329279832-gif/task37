package io.github.xxyopen.novel.dao.mapper;

import io.github.xxyopen.novel.dao.entity.UserConsumeLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

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
     * 幂等插入消费记录：如果同一用户+商品+类型且未退款的记录已存在，则不插入
     *
     * @return 影响行数（0 = 已存在重复记录，1 = 插入成功）
     */
    @Insert("INSERT INTO user_consume_log " +
            "(user_id, amount, product_type, product_id, produc_name, produc_value, " +
            " refund_status, author_id, create_time, update_time) " +
            "SELECT #{userId}, #{amount}, #{productType}, #{productId}, #{producName}, " +
            " #{producValue}, 0, #{authorId}, NOW(), NOW() FROM DUAL " +
            "WHERE NOT EXISTS (SELECT 1 FROM user_consume_log " +
            " WHERE user_id = #{userId} AND product_id = #{productId} " +
            " AND product_type = #{productType} AND refund_status = 0)")
    int insertIdempotent(@Param("userId") Long userId,
                         @Param("amount") int amount,
                         @Param("productType") int productType,
                         @Param("productId") Long productId,
                         @Param("producName") String producName,
                         @Param("producValue") int producValue,
                         @Param("authorId") Long authorId);

}
