package io.github.xxyopen.novel.dao.mapper;

import io.github.xxyopen.novel.dao.entity.MemberInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

/**
 * 会员信息 Mapper 接口
 */
public interface MemberInfoMapper extends BaseMapper<MemberInfo> {

    /**
     * 查询用户的有效会员信息
     */
    @Select("SELECT * FROM member_info WHERE user_id = #{userId} " +
            "AND status = 0 AND expire_time > NOW() LIMIT 1")
    MemberInfo selectActiveByUserId(@Param("userId") Long userId);

    /**
     * 原子扣减免费阅读配额
     */
    @Update("UPDATE member_info SET used_free_read = used_free_read + 1, " +
            "update_time = NOW() WHERE user_id = #{userId} " +
            "AND status = 0 AND expire_time > NOW() " +
            "AND used_free_read < free_read_quota")
    int deductFreeReadQuota(@Param("userId") Long userId);

    /**
     * 批量过期会员
     */
    @Update("UPDATE member_info SET status = 1, update_time = NOW() " +
            "WHERE expire_time <= NOW() AND status = 0")
    int expireMemberships();

    /**
     * 重置月度配额
     */
    @Update("UPDATE member_info SET used_free_read = 0, " +
            "quota_reset_date = #{resetDate}, update_time = NOW() " +
            "WHERE status = 0 AND expire_time > NOW() " +
            "AND quota_reset_date < #{resetDate}")
    int resetMonthlyQuota(@Param("resetDate") LocalDate resetDate);

}
