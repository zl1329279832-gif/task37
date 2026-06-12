package io.github.xxyopen.novel.dao.mapper;

import io.github.xxyopen.novel.dao.entity.ReadingVoucher;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 阅读券 Mapper 接口
 */
public interface ReadingVoucherMapper extends BaseMapper<ReadingVoucher> {

    /**
     * CAS 使用阅读券（防止并发重复使用）
     */
    @Update("UPDATE reading_voucher SET status = 1, used_chapter_id = #{chapterId}, " +
            "used_time = NOW(), update_time = NOW() " +
            "WHERE id = #{voucherId} AND status = 0 AND expire_time > NOW()")
    int casUseVoucher(@Param("voucherId") Long voucherId, @Param("chapterId") Long chapterId);

    /**
     * 退款时恢复阅读券为未使用
     */
    @Update("UPDATE reading_voucher SET status = 0, used_chapter_id = NULL, " +
            "used_time = NULL, update_time = NOW() " +
            "WHERE id = #{voucherId} AND status = 1")
    int restoreVoucher(@Param("voucherId") Long voucherId);

    /**
     * 将过期未使用的阅读券标记为已过期
     */
    @Update("UPDATE reading_voucher SET status = 2, update_time = NOW() " +
            "WHERE status = 0 AND expire_time < NOW()")
    int expireOverdueVouchers();

}
