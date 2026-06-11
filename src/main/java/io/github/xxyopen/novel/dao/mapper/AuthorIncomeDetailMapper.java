package io.github.xxyopen.novel.dao.mapper;

import io.github.xxyopen.novel.dao.entity.AuthorIncomeDetail;
import io.github.xxyopen.novel.dto.AuthorBookIncomePair;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
     * UPSERT 每日收入（原子累加）
     */
    @Insert("INSERT INTO author_income_detail " +
            "(author_id, book_id, income_date, income_account, income_count, " +
            " income_number, create_time, update_time) " +
            "VALUES (#{authorId}, #{bookId}, #{incomeDate}, #{amount}, " +
            " #{countIncrement}, #{numberIncrement}, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE " +
            "income_account = income_account + #{amount}, " +
            "income_count = income_count + #{countIncrement}, " +
            "income_number = income_number + #{numberIncrement}, " +
            "update_time = NOW()")
    void upsertDailyIncome(@Param("authorId") Long authorId,
                           @Param("bookId") Long bookId,
                           @Param("incomeDate") LocalDate incomeDate,
                           @Param("amount") int amount,
                           @Param("countIncrement") int countIncrement,
                           @Param("numberIncrement") int numberIncrement);

    /**
     * 查询指定日期范围内有收入的 (authorId, bookId) 对
     */
    @Select("SELECT DISTINCT author_id AS authorId, book_id AS bookId " +
            "FROM author_income_detail " +
            "WHERE income_date BETWEEN #{startDate} AND #{endDate}")
    List<AuthorBookIncomePair> selectDistinctAuthorBookPairs(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * 汇总指定作者+作品在日期范围内的收入
     */
    @Select("SELECT COALESCE(SUM(income_account), 0) FROM author_income_detail " +
            "WHERE author_id = #{authorId} AND book_id = #{bookId} " +
            "AND income_date BETWEEN #{startDate} AND #{endDate}")
    Integer sumIncomeForMonth(@Param("authorId") Long authorId,
                              @Param("bookId") Long bookId,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate);

    /**
     * 退款时扣减日收入
     */
    @Update("UPDATE author_income_detail SET " +
            "income_account = income_account - #{amount}, " +
            "income_count = income_count - 1, " +
            "income_number = income_number - #{numberDecrement}, " +
            "update_time = NOW() " +
            "WHERE author_id = #{authorId} AND book_id = #{bookId} " +
            "AND income_date = #{incomeDate} " +
            "AND income_account >= #{amount}")
    int deductDailyIncome(@Param("authorId") Long authorId,
                          @Param("bookId") Long bookId,
                          @Param("incomeDate") LocalDate incomeDate,
                          @Param("amount") int amount,
                          @Param("numberDecrement") int numberDecrement);

    /**
     * 检查用户当天是否已购买过该作者+作品的章节
     */
    @Select("SELECT COUNT(1) FROM user_consume_log " +
            "WHERE author_id = #{authorId} " +
            "AND product_id IN (SELECT id FROM book_chapter WHERE book_id = #{bookId}) " +
            "AND user_id = #{userId} " +
            "AND DATE(create_time) = #{date} " +
            "AND refund_status = 0 LIMIT 1")
    int countUserPurchasesToday(@Param("authorId") Long authorId,
                                @Param("bookId") Long bookId,
                                @Param("userId") Long userId,
                                @Param("date") LocalDate date);

}
