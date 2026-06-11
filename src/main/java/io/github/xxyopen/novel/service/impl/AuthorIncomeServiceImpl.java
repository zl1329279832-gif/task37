package io.github.xxyopen.novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.xxyopen.novel.core.common.constant.ErrorCodeEnum;
import io.github.xxyopen.novel.core.common.exception.BusinessException;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.constant.DatabaseConsts;
import io.github.xxyopen.novel.dao.entity.AuthorIncome;
import io.github.xxyopen.novel.dao.entity.AuthorIncomeDetail;
import io.github.xxyopen.novel.dao.entity.BookInfo;
import io.github.xxyopen.novel.dao.mapper.AuthorIncomeDetailMapper;
import io.github.xxyopen.novel.dao.mapper.AuthorIncomeMapper;
import io.github.xxyopen.novel.dao.mapper.BookInfoMapper;
import io.github.xxyopen.novel.dto.resp.AuthorIncomeDetailRespDto;
import io.github.xxyopen.novel.dto.resp.AuthorIncomeRespDto;
import io.github.xxyopen.novel.service.AuthorIncomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 作家收入 服务实现类
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
@Service
@RequiredArgsConstructor
public class AuthorIncomeServiceImpl implements AuthorIncomeService {

    private final AuthorIncomeDetailMapper authorIncomeDetailMapper;

    private final AuthorIncomeMapper authorIncomeMapper;

    private final BookInfoMapper bookInfoMapper;

    @Override
    public RestResp<List<AuthorIncomeDetailRespDto>> listDailyIncomeDetails(
            Long authorId, Long bookId, LocalDate startDate, LocalDate endDate) {
        QueryWrapper<AuthorIncomeDetail> qw = new QueryWrapper<>();
        qw.eq(DatabaseConsts.AuthorIncomeDetailTable.COLUMN_AUTHOR_ID, authorId);
        if (bookId != null && bookId > 0) {
            qw.eq(DatabaseConsts.AuthorIncomeDetailTable.COLUMN_BOOK_ID, bookId);
        }
        if (startDate != null) {
            qw.ge(DatabaseConsts.AuthorIncomeDetailTable.COLUMN_INCOME_DATE, startDate);
        }
        if (endDate != null) {
            qw.le(DatabaseConsts.AuthorIncomeDetailTable.COLUMN_INCOME_DATE, endDate);
        }
        qw.orderByDesc(DatabaseConsts.AuthorIncomeDetailTable.COLUMN_INCOME_DATE);

        List<AuthorIncomeDetail> details = authorIncomeDetailMapper.selectList(qw);
        List<AuthorIncomeDetailRespDto> respList = details.stream().map(detail -> {
            String bookName = null;
            if (detail.getBookId() != null && detail.getBookId() > 0) {
                BookInfo bookInfo = bookInfoMapper.selectById(detail.getBookId());
                if (bookInfo != null) {
                    bookName = bookInfo.getBookName();
                }
            }
            return AuthorIncomeDetailRespDto.builder()
                    .bookId(detail.getBookId())
                    .bookName(bookName)
                    .incomeDate(detail.getIncomeDate())
                    .incomeAccount(detail.getIncomeAccount())
                    .incomeCount(detail.getIncomeCount())
                    .incomeNumber(detail.getIncomeNumber())
                    .build();
        }).toList();

        return RestResp.ok(respList);
    }

    @Override
    public RestResp<List<AuthorIncomeRespDto>> listMonthlySettlements(Long authorId, Integer year) {
        QueryWrapper<AuthorIncome> qw = new QueryWrapper<>();
        qw.eq(DatabaseConsts.AuthorIncomeTable.COLUMN_AUTHOR_ID, authorId);
        if (year != null) {
            qw.ge(DatabaseConsts.AuthorIncomeTable.COLUMN_INCOME_MONTH, LocalDate.of(year, 1, 1));
            qw.le(DatabaseConsts.AuthorIncomeTable.COLUMN_INCOME_MONTH, LocalDate.of(year, 12, 31));
        }
        qw.orderByDesc(DatabaseConsts.AuthorIncomeTable.COLUMN_INCOME_MONTH);

        List<AuthorIncome> incomes = authorIncomeMapper.selectList(qw);
        List<AuthorIncomeRespDto> respList = incomes.stream().map(income -> {
            String bookName = null;
            if (income.getBookId() != null && income.getBookId() > 0) {
                BookInfo bookInfo = bookInfoMapper.selectById(income.getBookId());
                if (bookInfo != null) {
                    bookName = bookInfo.getBookName();
                }
            }
            return AuthorIncomeRespDto.builder()
                    .id(income.getId())
                    .bookId(income.getBookId())
                    .bookName(bookName)
                    .incomeMonth(income.getIncomeMonth())
                    .preTaxIncome(income.getPreTaxIncome())
                    .afterTaxIncome(income.getAfterTaxIncome())
                    .payStatus(income.getPayStatus())
                    .confirmStatus(income.getConfirmStatus())
                    .build();
        }).toList();

        return RestResp.ok(respList);
    }

    @Override
    public RestResp<Void> confirmIncome(Long authorId, Long incomeId) {
        QueryWrapper<AuthorIncome> qw = new QueryWrapper<>();
        qw.eq(DatabaseConsts.CommonColumnEnum.ID.getName(), incomeId)
                .eq(DatabaseConsts.AuthorIncomeTable.COLUMN_AUTHOR_ID, authorId)
                .eq(DatabaseConsts.AuthorIncomeTable.COLUMN_CONFIRM_STATUS, 0);
        AuthorIncome income = authorIncomeMapper.selectOne(qw);
        if (income == null) {
            throw new BusinessException(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
        }
        income.setConfirmStatus(1);
        income.setUpdateTime(LocalDateTime.now());
        authorIncomeMapper.updateById(income);
        return RestResp.ok();
    }

}
