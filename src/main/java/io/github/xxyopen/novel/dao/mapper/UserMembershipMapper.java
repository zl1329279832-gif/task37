package io.github.xxyopen.novel.dao.mapper;

import io.github.xxyopen.novel.dao.entity.UserMembership;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户会员 Mapper 接口
 */
public interface UserMembershipMapper extends BaseMapper<UserMembership> {

    /**
     * 原子递增已用免费章节数
     */
    @Update("UPDATE user_membership SET free_chapter_used = free_chapter_used + 1, " +
            "update_time = NOW() WHERE id = #{id} AND free_chapter_used < free_chapter_quota " +
            "AND status = 0")
    int incrementFreeChapterUsed(@Param("id") Long id);

    /**
     * 原子递减已用免费章节数（退款时）
     */
    @Update("UPDATE user_membership SET free_chapter_used = free_chapter_used - 1, " +
            "update_time = NOW() WHERE id = #{id} AND free_chapter_used > 0")
    int decrementFreeChapterUsed(@Param("id") Long id);

    /**
     * 每月重置免费章节已用数
     */
    @Update("UPDATE user_membership SET free_chapter_used = 0, update_time = NOW() " +
            "WHERE status = 0")
    int resetMonthlyFreeChapterUsed();

    /**
     * 将过期会员状态标记为已过期
     */
    @Update("UPDATE user_membership SET status = 1, update_time = NOW() " +
            "WHERE status = 0 AND expire_time < NOW()")
    int expireOverdueMemberships();

}
