package io.github.xxyopen.novel.dao.mapper;

import io.github.xxyopen.novel.dao.entity.AuthorIncomeDetail;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * <p>
 * 稿费收入明细统计 Mapper 接口
 * </p>
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
public interface AuthorIncomeDetailMapper extends BaseMapper<AuthorIncomeDetail> {

    /**
     * 查询作者每日收入明细
     * @param authorId 作者ID
     * @param bookId 小说ID（0或null表示全部）
     * @param startDate 起始日期
     * @param endDate 截止日期
     * @return 收入明细列表
     */
    List<AuthorIncomeDetail> selectDailyIncomeByAuthorId(
            @Param("authorId") Long authorId,
            @Param("bookId") Long bookId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

}
