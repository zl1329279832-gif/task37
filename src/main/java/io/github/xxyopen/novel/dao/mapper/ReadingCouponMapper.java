package io.github.xxyopen.novel.dao.mapper;

import io.github.xxyopen.novel.dao.entity.ReadingCoupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 阅读券 Mapper 接口
 */
public interface ReadingCouponMapper extends BaseMapper<ReadingCoupon> {

    /**
     * 查询用户可用的阅读券列表
     */
    @Select("SELECT * FROM reading_coupon WHERE user_id = #{userId} " +
            "AND use_status = 0 AND expire_time > NOW() ORDER BY expire_time ASC")
    List<ReadingCoupon> selectAvailableByUserId(@Param("userId") Long userId);

    /**
     * CAS 使用阅读券
     */
    @Update("UPDATE reading_coupon SET use_status = 1, used_time = NOW(), " +
            "consume_log_id = #{consumeLogId}, update_time = NOW() " +
            "WHERE id = #{couponId} AND use_status = 0 AND expire_time > NOW()")
    int casUseCoupon(@Param("couponId") Long couponId, @Param("consumeLogId") Long consumeLogId);

    /**
     * 恢复阅读券（退款时）
     */
    @Update("UPDATE reading_coupon SET use_status = 0, used_time = NULL, " +
            "consume_log_id = NULL, update_time = NOW() " +
            "WHERE id = #{couponId} AND use_status = 1")
    int restoreCoupon(@Param("couponId") Long couponId);

    /**
     * 批量过期阅读券
     */
    @Update("UPDATE reading_coupon SET use_status = 2, update_time = NOW() " +
            "WHERE expire_time <= NOW() AND use_status = 0")
    int expireCoupons();

}
