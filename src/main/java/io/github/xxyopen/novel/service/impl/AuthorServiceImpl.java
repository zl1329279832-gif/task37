package io.github.xxyopen.novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.constant.DatabaseConsts;
import io.github.xxyopen.novel.dao.entity.AuthorIncome;
import io.github.xxyopen.novel.dao.entity.AuthorIncomeDetail;
import io.github.xxyopen.novel.dao.entity.AuthorInfo;
import io.github.xxyopen.novel.dao.entity.BookInfo;
import io.github.xxyopen.novel.dao.mapper.AuthorIncomeDetailMapper;
import io.github.xxyopen.novel.dao.mapper.AuthorIncomeMapper;
import io.github.xxyopen.novel.dao.mapper.AuthorInfoMapper;
import io.github.xxyopen.novel.dao.mapper.BookInfoMapper;
import io.github.xxyopen.novel.dto.AuthorInfoDto;
import io.github.xxyopen.novel.dto.req.AuthorRegisterReqDto;
import io.github.xxyopen.novel.dto.resp.AuthorIncomeDetailRespDto;
import io.github.xxyopen.novel.dto.resp.AuthorIncomeRespDto;
import io.github.xxyopen.novel.manager.cache.AuthorInfoCacheManager;
import io.github.xxyopen.novel.service.AuthorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 作家模块 服务实现类
 *
 * @author xiongxiaoyang
 * @date 2022/5/23
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorServiceImpl implements AuthorService {

    private final AuthorInfoCacheManager authorInfoCacheManager;

    private final AuthorInfoMapper authorInfoMapper;

    private final AuthorIncomeDetailMapper authorIncomeDetailMapper;

    private final AuthorIncomeMapper authorIncomeMapper;

    private final BookInfoMapper bookInfoMapper;

    @Override
    public RestResp<Void> register(AuthorRegisterReqDto dto) {
        // 校验该用户是否已注册为作家
        AuthorInfoDto author = authorInfoCacheManager.getAuthor(dto.getUserId());
        if (Objects.nonNull(author)) {
            // 该用户已经是作家，直接返回
            return RestResp.ok();
        }
        // 保存作家注册信息
        AuthorInfo authorInfo = new AuthorInfo();
        authorInfo.setUserId(dto.getUserId());
        authorInfo.setChatAccount(dto.getChatAccount());
        authorInfo.setEmail(dto.getEmail());
        authorInfo.setInviteCode("0");
        authorInfo.setTelPhone(dto.getTelPhone());
        authorInfo.setPenName(dto.getPenName());
        authorInfo.setWorkDirection(dto.getWorkDirection());
        authorInfo.setCreateTime(LocalDateTime.now());
        authorInfo.setUpdateTime(LocalDateTime.now());
        authorInfoMapper.insert(authorInfo);
        // 清除作家缓存
        authorInfoCacheManager.evictAuthorCache();
        return RestResp.ok();
    }

    @Override
    public RestResp<List<AuthorIncomeDetailRespDto>> listDailyIncome(Long authorId, Long bookId,
                                                                     LocalDate startDate, LocalDate endDate) {
        List<AuthorIncomeDetail> details = authorIncomeDetailMapper
                .selectDailyIncomeByAuthorId(authorId, bookId, startDate, endDate);

        // 收集bookId列表，查询书名
        Set<Long> bookIds = details.stream()
                .map(AuthorIncomeDetail::getBookId)
                .filter(id -> id != 0)
                .collect(Collectors.toSet());
        Map<Long, String> bookNameMap = new HashMap<>();
        if (!bookIds.isEmpty()) {
            QueryWrapper<BookInfo> bookQuery = new QueryWrapper<>();
            bookQuery.in(DatabaseConsts.CommonColumnEnum.ID.getName(), bookIds);
            bookInfoMapper.selectList(bookQuery)
                    .forEach(b -> bookNameMap.put(b.getId(), b.getBookName()));
        }

        List<AuthorIncomeDetailRespDto> result = details.stream()
                .map(d -> AuthorIncomeDetailRespDto.builder()
                        .bookId(d.getBookId())
                        .bookName(d.getBookId() == 0 ? "全部作品" : bookNameMap.getOrDefault(d.getBookId(), ""))
                        .incomeDate(d.getIncomeDate())
                        .incomeAccount(d.getIncomeAccount())
                        .incomeCount(d.getIncomeCount())
                        .incomeNumber(d.getIncomeNumber())
                        .build())
                .toList();

        return RestResp.ok(result);
    }

    @Override
    public RestResp<List<AuthorIncomeRespDto>> listMonthlyIncome(Long authorId) {
        QueryWrapper<AuthorIncome> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.AuthorIncomeTable.COLUMN_AUTHOR_ID, authorId)
                .orderByDesc(DatabaseConsts.AuthorIncomeTable.COLUMN_INCOME_MONTH);
        List<AuthorIncome> incomes = authorIncomeMapper.selectList(queryWrapper);

        // 收集bookId列表，查询书名
        Set<Long> bookIds = incomes.stream()
                .map(AuthorIncome::getBookId)
                .filter(id -> id != 0)
                .collect(Collectors.toSet());
        Map<Long, String> bookNameMap = new HashMap<>();
        if (!bookIds.isEmpty()) {
            QueryWrapper<BookInfo> bookQuery = new QueryWrapper<>();
            bookQuery.in(DatabaseConsts.CommonColumnEnum.ID.getName(), bookIds);
            bookInfoMapper.selectList(bookQuery)
                    .forEach(b -> bookNameMap.put(b.getId(), b.getBookName()));
        }

        List<AuthorIncomeRespDto> result = incomes.stream()
                .map(i -> AuthorIncomeRespDto.builder()
                        .authorId(i.getAuthorId())
                        .bookId(i.getBookId())
                        .bookName(i.getBookId() == 0 ? "全部作品" : bookNameMap.getOrDefault(i.getBookId(), ""))
                        .incomeMonth(i.getIncomeMonth())
                        .preTaxIncome(i.getPreTaxIncome())
                        .afterTaxIncome(i.getAfterTaxIncome())
                        .payStatus(i.getPayStatus())
                        .confirmStatus(i.getConfirmStatus())
                        .build())
                .toList();

        return RestResp.ok(result);
    }
}
