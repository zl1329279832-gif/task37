package io.github.xxyopen.novel.dao.mapper;

import io.github.xxyopen.novel.dao.entity.AuthorIncome;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

/**
 * <p>
 * 稿费收入统计 Mapper 接口
 * </p>
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
public interface AuthorIncomeMapper extends BaseMapper<AuthorIncome> {

    /**
     * 结算回滚：扣减月度结算金额（仅 payStatus=0 时可操作）
     */
    @Update("UPDATE author_income SET " +
            "pre_tax_income = pre_tax_income - #{amount}, " +
            "after_tax_income = after_tax_income - #{afterTaxAmount}, " +
            "update_time = NOW() " +
            "WHERE author_id = #{authorId} AND book_id = #{bookId} " +
            "AND income_month = #{incomeMonth} AND pay_status = 0")
    int deductSettlement(@Param("authorId") Long authorId,
                         @Param("bookId") Long bookId,
                         @Param("incomeMonth") LocalDate incomeMonth,
                         @Param("amount") int amount,
                         @Param("afterTaxAmount") int afterTaxAmount);

}
