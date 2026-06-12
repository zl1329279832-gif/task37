package io.github.xxyopen.novel.dao.mapper;

import io.github.xxyopen.novel.dao.entity.AuthorIncomeBreakdown;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 作者收入拆分 Mapper 接口
 */
public interface AuthorIncomeBreakdownMapper extends BaseMapper<AuthorIncomeBreakdown> {

    /**
     * 根据批次ID查询收入拆分
     */
    @Select("SELECT * FROM author_income_breakdown WHERE batch_id = #{batchId}")
    List<AuthorIncomeBreakdown> selectByBatchId(@Param("batchId") Long batchId);

}
