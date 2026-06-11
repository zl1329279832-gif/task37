package io.github.xxyopen.novel.dao.mapper;

import io.github.xxyopen.novel.dao.entity.UserInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

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
     * 扣减用户余额（行级并发安全）
     * @param userId 用户ID
     * @param amount 扣减金额
     * @return 影响行数，0表示余额不足
     */
    int deductBalance(@Param("userId") Long userId, @Param("amount") Integer amount);

    /**
     * 增加用户余额（退款用）
     * @param userId 用户ID
     * @param amount 增加金额
     * @return 影响行数
     */
    int addBalance(@Param("userId") Long userId, @Param("amount") Integer amount);

}
