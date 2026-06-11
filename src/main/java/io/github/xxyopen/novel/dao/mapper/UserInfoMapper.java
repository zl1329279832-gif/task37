package io.github.xxyopen.novel.dao.mapper;

import io.github.xxyopen.novel.dao.entity.UserInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 * 用户信息 Mapper 接口
 * </p>
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
public interface UserInfoMapper extends BaseMapper<UserInfo> {

    /**
     * 扣减余额（原子操作，防止超扣）
     *
     * @param userId 用户ID
     * @param amount 扣减金额（屋币）
     * @return 影响行数（0 表示余额不足）
     */
    @Update("UPDATE user_info SET account_balance = account_balance - #{amount}, " +
            "update_time = NOW() WHERE id = #{userId} AND account_balance >= #{amount}")
    int deductBalance(@Param("userId") Long userId, @Param("amount") int amount);

    /**
     * 恢复余额（退款用）
     *
     * @param userId 用户ID
     * @param amount 恢复金额（屋币）
     * @return 影响行数
     */
    @Update("UPDATE user_info SET account_balance = account_balance + #{amount}, " +
            "update_time = NOW() WHERE id = #{userId}")
    int restoreBalance(@Param("userId") Long userId, @Param("amount") int amount);

}
