package io.github.xxyopen.novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.github.xxyopen.novel.core.common.constant.CommonConsts;
import io.github.xxyopen.novel.core.common.constant.ErrorCodeEnum;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.constant.DatabaseConsts;
import io.github.xxyopen.novel.dao.entity.*;
import io.github.xxyopen.novel.dao.mapper.*;
import io.github.xxyopen.novel.service.BookPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 付费章节订阅 服务实现类
 *
 * @author xiongxiaoyang
 * @date 2022/5/23
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookPayServiceImpl implements BookPayService {

    private final BookChapterMapper bookChapterMapper;

    private final BookInfoMapper bookInfoMapper;

    private final UserInfoMapper userInfoMapper;

    private final UserConsumeLogMapper userConsumeLogMapper;

    private final AuthorIncomeDetailMapper authorIncomeDetailMapper;

    private final AuthorIncomeMapper authorIncomeMapper;

    /**
     * VIP章节消费商品类型
     */
    private static final int PRODUCT_TYPE_VIP_CHAPTER = 0;

    /**
     * 作者分成比例（70%）
     */
    public static final int AUTHOR_SHARE_PERCENT = 70;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RestResp<Void> buyChapter(Long userId, Long chapterId) {
        // 1. 查询章节信息（直接查DB，避免缓存中限免状态滞后）
        BookChapter chapter = bookChapterMapper.selectById(chapterId);
        if (Objects.isNull(chapter)) {
            return RestResp.fail(ErrorCodeEnum.USER_CHAPTER_NOT_EXIST);
        }

        // 2. 校验是否VIP章节
        if (!Objects.equals(chapter.getIsVip(), CommonConsts.YES)) {
            return RestResp.fail(ErrorCodeEnum.USER_CHAPTER_NOT_VIP);
        }

        // 3. 校验限免状态
        if (Objects.equals(chapter.getIsFree(), CommonConsts.YES)) {
            return RestResp.fail(ErrorCodeEnum.USER_CHAPTER_IS_FREE_TRIAL);
        }

        // 4. 幂等校验：检查是否已购买
        QueryWrapper<UserConsumeLog> consumeQueryWrapper = new QueryWrapper<>();
        consumeQueryWrapper.eq(DatabaseConsts.UserConsumeLogTable.COLUMN_USER_ID, userId)
                .eq(DatabaseConsts.UserConsumeLogTable.COLUMN_PRODUCT_ID, chapterId)
                .eq(DatabaseConsts.UserConsumeLogTable.COLUMN_PRODUCT_TYPE, PRODUCT_TYPE_VIP_CHAPTER);
        if (userConsumeLogMapper.selectCount(consumeQueryWrapper) > 0) {
            return RestResp.fail(ErrorCodeEnum.USER_CHAPTER_ALREADY_BOUGHT);
        }

        // 5. 获取章节价格
        Integer chapterPrice = chapter.getChapterPrice();
        if (Objects.isNull(chapterPrice) || chapterPrice <= 0) {
            chapterPrice = 0;
        }

        // 6. 扣减余额（行级并发安全，影响行数为0表示余额不足）
        int rows = userInfoMapper.deductBalance(userId, chapterPrice);
        if (rows == 0) {
            return RestResp.fail(ErrorCodeEnum.USER_BALANCE_NOT_ENOUGH);
        }

        // 7. 写消费记录
        UserConsumeLog consumeLog = new UserConsumeLog();
        consumeLog.setUserId(userId);
        consumeLog.setAmount(chapterPrice);
        consumeLog.setProductType(PRODUCT_TYPE_VIP_CHAPTER);
        consumeLog.setProductId(chapterId);
        consumeLog.setProducName(chapter.getChapterName());
        consumeLog.setProducValue(1);
        consumeLog.setCreateTime(LocalDateTime.now());
        consumeLog.setUpdateTime(LocalDateTime.now());
        userConsumeLogMapper.insert(consumeLog);

        // 8. 累计作者收入
        BookInfo bookInfo = bookInfoMapper.selectById(chapter.getBookId());
        if (Objects.nonNull(bookInfo) && Objects.nonNull(bookInfo.getAuthorId())) {
            accumulateAuthorIncome(bookInfo.getAuthorId(), chapter.getBookId(), chapterPrice);
        }

        return RestResp.ok();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RestResp<Void> refundChapter(Long userId, Long chapterId) {
        // 1. 查询消费记录
        QueryWrapper<UserConsumeLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.UserConsumeLogTable.COLUMN_USER_ID, userId)
                .eq(DatabaseConsts.UserConsumeLogTable.COLUMN_PRODUCT_ID, chapterId)
                .eq(DatabaseConsts.UserConsumeLogTable.COLUMN_PRODUCT_TYPE, PRODUCT_TYPE_VIP_CHAPTER);
        UserConsumeLog consumeLog = userConsumeLogMapper.selectOne(queryWrapper);
        if (Objects.isNull(consumeLog)) {
            return RestResp.fail(ErrorCodeEnum.USER_CONSUME_NOT_EXIST);
        }

        // 2. 删除消费记录
        userConsumeLogMapper.deleteById(consumeLog.getId());

        // 3. 恢复用户余额
        userInfoMapper.addBalance(userId, consumeLog.getAmount());

        // 4. 扣减作者收入
        BookChapter chapter = bookChapterMapper.selectById(chapterId);
        if (Objects.nonNull(chapter)) {
            BookInfo bookInfo = bookInfoMapper.selectById(chapter.getBookId());
            if (Objects.nonNull(bookInfo) && Objects.nonNull(bookInfo.getAuthorId())) {
                deductAuthorIncome(bookInfo.getAuthorId(), chapter.getBookId(),
                        consumeLog.getAmount(), consumeLog.getCreateTime().toLocalDate());
            }
        }

        return RestResp.ok();
    }

    @Override
    public RestResp<Integer> isChapterBought(Long userId, Long chapterId) {
        QueryWrapper<UserConsumeLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.UserConsumeLogTable.COLUMN_USER_ID, userId)
                .eq(DatabaseConsts.UserConsumeLogTable.COLUMN_PRODUCT_ID, chapterId)
                .eq(DatabaseConsts.UserConsumeLogTable.COLUMN_PRODUCT_TYPE, PRODUCT_TYPE_VIP_CHAPTER);
        return RestResp.ok(userConsumeLogMapper.selectCount(queryWrapper) > 0
                ? CommonConsts.YES : CommonConsts.NO);
    }

    @Override
    public RestResp<List<Long>> listBoughtChapterIds(Long userId, Long bookId) {
        // 先查该书所有章节ID
        QueryWrapper<BookChapter> chapterQueryWrapper = new QueryWrapper<>();
        chapterQueryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .select(DatabaseConsts.CommonColumnEnum.ID.getName());
        List<Long> chapterIds = bookChapterMapper.selectList(chapterQueryWrapper)
                .stream().map(BookChapter::getId).toList();

        if (chapterIds.isEmpty()) {
            return RestResp.ok(List.of());
        }

        // 查已购记录
        QueryWrapper<UserConsumeLog> consumeQueryWrapper = new QueryWrapper<>();
        consumeQueryWrapper.eq(DatabaseConsts.UserConsumeLogTable.COLUMN_USER_ID, userId)
                .in(DatabaseConsts.UserConsumeLogTable.COLUMN_PRODUCT_ID, chapterIds)
                .eq(DatabaseConsts.UserConsumeLogTable.COLUMN_PRODUCT_TYPE, PRODUCT_TYPE_VIP_CHAPTER);
        List<Long> boughtIds = userConsumeLogMapper.selectList(consumeQueryWrapper)
                .stream().map(UserConsumeLog::getProductId).toList();

        return RestResp.ok(boughtIds);
    }

    /**
     * 累计作者收入明细
     */
    private void accumulateAuthorIncome(Long authorId, Long bookId, Integer amount) {
        LocalDate today = LocalDate.now();

        // 按作品维度累计
        upsertIncomeDetail(authorId, bookId, today, amount);

        // 全部作品汇总（bookId=0）
        upsertIncomeDetail(authorId, 0L, today, amount);
    }

    /**
     * 插入或更新作者收入明细
     */
    private void upsertIncomeDetail(Long authorId, Long bookId, LocalDate date, Integer amount) {
        QueryWrapper<AuthorIncomeDetail> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.AuthorIncomeDetailTable.COLUMN_AUTHOR_ID, authorId)
                .eq(DatabaseConsts.AuthorIncomeDetailTable.COLUMN_BOOK_ID, bookId)
                .eq(DatabaseConsts.AuthorIncomeDetailTable.COLUMN_INCOME_DATE, date);
        AuthorIncomeDetail detail = authorIncomeDetailMapper.selectOne(queryWrapper);

        if (Objects.nonNull(detail)) {
            // 更新
            AuthorIncomeDetail update = new AuthorIncomeDetail();
            update.setId(detail.getId());
            update.setIncomeAccount(detail.getIncomeAccount() + amount);
            update.setIncomeCount(detail.getIncomeCount() + 1);
            update.setIncomeNumber(detail.getIncomeNumber() + 1);
            update.setUpdateTime(LocalDateTime.now());
            authorIncomeDetailMapper.updateById(update);
        } else {
            // 新建
            AuthorIncomeDetail newDetail = new AuthorIncomeDetail();
            newDetail.setAuthorId(authorId);
            newDetail.setBookId(bookId);
            newDetail.setIncomeDate(date);
            newDetail.setIncomeAccount(amount);
            newDetail.setIncomeCount(1);
            newDetail.setIncomeNumber(1);
            newDetail.setCreateTime(LocalDateTime.now());
            newDetail.setUpdateTime(LocalDateTime.now());
            authorIncomeDetailMapper.insert(newDetail);
        }
    }

    /**
     * 扣减作者收入（退款用）
     */
    private void deductAuthorIncome(Long authorId, Long bookId, Integer amount, LocalDate date) {
        // 扣减按作品维度
        deductIncomeDetail(authorId, bookId, date, amount);

        // 扣减全部作品汇总
        deductIncomeDetail(authorId, 0L, date, amount);

        // 如已生成月结单，扣减月结单金额
        LocalDate monthStart = date.withDayOfMonth(1);
        QueryWrapper<AuthorIncome> incomeQueryWrapper = new QueryWrapper<>();
        incomeQueryWrapper.eq(DatabaseConsts.AuthorIncomeTable.COLUMN_AUTHOR_ID, authorId)
                .eq(DatabaseConsts.AuthorIncomeTable.COLUMN_BOOK_ID, bookId)
                .eq(DatabaseConsts.AuthorIncomeTable.COLUMN_INCOME_MONTH, monthStart);
        AuthorIncome income = authorIncomeMapper.selectOne(incomeQueryWrapper);
        if (Objects.nonNull(income)) {
            int preTaxDeduct = amount;
            int afterTaxDeduct = amount * AUTHOR_SHARE_PERCENT / 100;
            AuthorIncome update = new AuthorIncome();
            update.setId(income.getId());
            update.setPreTaxIncome(Math.max(0, income.getPreTaxIncome() - preTaxDeduct));
            update.setAfterTaxIncome(Math.max(0, income.getAfterTaxIncome() - afterTaxDeduct));
            update.setUpdateTime(LocalDateTime.now());
            authorIncomeMapper.updateById(update);
        }
    }

    /**
     * 扣减收入明细
     */
    private void deductIncomeDetail(Long authorId, Long bookId, LocalDate date, Integer amount) {
        QueryWrapper<AuthorIncomeDetail> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.AuthorIncomeDetailTable.COLUMN_AUTHOR_ID, authorId)
                .eq(DatabaseConsts.AuthorIncomeDetailTable.COLUMN_BOOK_ID, bookId)
                .eq(DatabaseConsts.AuthorIncomeDetailTable.COLUMN_INCOME_DATE, date);
        AuthorIncomeDetail detail = authorIncomeDetailMapper.selectOne(queryWrapper);
        if (Objects.nonNull(detail)) {
            AuthorIncomeDetail update = new AuthorIncomeDetail();
            update.setId(detail.getId());
            update.setIncomeAccount(Math.max(0, detail.getIncomeAccount() - amount));
            update.setIncomeCount(Math.max(0, detail.getIncomeCount() - 1));
            update.setIncomeNumber(Math.max(0, detail.getIncomeNumber() - 1));
            update.setUpdateTime(LocalDateTime.now());
            authorIncomeDetailMapper.updateById(update);
        }
    }
}
