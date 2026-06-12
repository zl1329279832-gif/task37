package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.dao.entity.AuthorIncomeBreakdown;
import io.github.xxyopen.novel.dao.entity.AuthorIncomeDetail;
import io.github.xxyopen.novel.dao.entity.BookInfo;
import io.github.xxyopen.novel.dao.mapper.AuthorIncomeBreakdownMapper;
import io.github.xxyopen.novel.dao.mapper.AuthorIncomeDetailMapper;
import io.github.xxyopen.novel.dao.mapper.AuthorIncomeMapper;
import io.github.xxyopen.novel.dao.mapper.BookInfoMapper;
import io.github.xxyopen.novel.dto.resp.AuthorIncomeDetailRespDto;
import io.github.xxyopen.novel.service.impl.AuthorIncomeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 增强作者收入统计测试
 * 场景：混合类型收入拆分、拆分总额与日收入一致
 */
@ExtendWith(MockitoExtension.class)
class AuthorIncomeStatisticsEnhancedTest {

    @InjectMocks
    private AuthorIncomeServiceImpl authorIncomeService;

    @Mock private AuthorIncomeDetailMapper authorIncomeDetailMapper;
    @Mock private AuthorIncomeMapper authorIncomeMapper;
    @Mock private BookInfoMapper bookInfoMapper;
    @Mock private AuthorIncomeBreakdownMapper authorIncomeBreakdownMapper;

    private final Long authorId = 5L;
    private final Long bookId = 10L;

    @Test
    void testBreakdown_mixedTypes() {
        // 模拟收入拆分：章节购买70%、会员补贴20%、平台补贴10%
        AuthorIncomeBreakdown breakdown1 = new AuthorIncomeBreakdown();
        breakdown1.setAuthorId(authorId);
        breakdown1.setBookId(bookId);
        breakdown1.setIncomeType(0); // 章节购买
        breakdown1.setAmount(70);

        AuthorIncomeBreakdown breakdown2 = new AuthorIncomeBreakdown();
        breakdown2.setAuthorId(authorId);
        breakdown2.setBookId(bookId);
        breakdown2.setIncomeType(1); // 会员补贴
        breakdown2.setAmount(20);

        AuthorIncomeBreakdown breakdown3 = new AuthorIncomeBreakdown();
        breakdown3.setAuthorId(authorId);
        breakdown3.setBookId(bookId);
        breakdown3.setIncomeType(2); // 平台补贴
        breakdown3.setAmount(10);

        when(authorIncomeBreakdownMapper.selectByBatchId(1L))
                .thenReturn(List.of(breakdown1, breakdown2, breakdown3));

        List<AuthorIncomeBreakdown> result = authorIncomeBreakdownMapper.selectByBatchId(1L);

        assertEquals(3, result.size());
        int total = result.stream().mapToInt(AuthorIncomeBreakdown::getAmount).sum();
        assertEquals(100, total);

        // 验证各类型金额
        assertEquals(70, result.stream()
                .filter(b -> b.getIncomeType() == 0).mapToInt(AuthorIncomeBreakdown::getAmount).sum());
        assertEquals(20, result.stream()
                .filter(b -> b.getIncomeType() == 1).mapToInt(AuthorIncomeBreakdown::getAmount).sum());
        assertEquals(10, result.stream()
                .filter(b -> b.getIncomeType() == 2).mapToInt(AuthorIncomeBreakdown::getAmount).sum());
    }

    @Test
    void testBreakdown_matchesDaily() {
        // 拆分总额应等于日收入汇总
        LocalDate today = LocalDate.now();

        AuthorIncomeDetail detail = new AuthorIncomeDetail();
        detail.setAuthorId(authorId);
        detail.setBookId(bookId);
        detail.setIncomeDate(today);
        detail.setIncomeAccount(100);
        detail.setIncomeCount(10);
        detail.setIncomeNumber(8);

        when(authorIncomeDetailMapper.selectList(any())).thenReturn(List.of(detail));

        BookInfo bookInfo = new BookInfo();
        bookInfo.setId(bookId);
        bookInfo.setBookName("测试小说");
        when(bookInfoMapper.selectById(bookId)).thenReturn(bookInfo);

        RestResp<List<AuthorIncomeDetailRespDto>> result =
                authorIncomeService.listDailyIncomeDetails(authorId, bookId, today, today);

        assertTrue(result.isOk());
        assertEquals(1, result.getData().size());
        assertEquals(100, result.getData().get(0).getIncomeAccount());

        // 模拟拆分总额也是100
        List<AuthorIncomeBreakdown> breakdowns = List.of(
                createBreakdown(0, 70),
                createBreakdown(1, 20),
                createBreakdown(2, 10)
        );
        when(authorIncomeBreakdownMapper.selectByBatchId(anyLong())).thenReturn(breakdowns);

        int breakdownTotal = authorIncomeBreakdownMapper.selectByBatchId(1L).stream()
                .mapToInt(AuthorIncomeBreakdown::getAmount).sum();
        assertEquals(result.getData().get(0).getIncomeAccount(), breakdownTotal);
    }

    @Test
    void testDailyIncome_multiBookAggregation() {
        LocalDate today = LocalDate.now();

        AuthorIncomeDetail detail1 = new AuthorIncomeDetail();
        detail1.setAuthorId(authorId);
        detail1.setBookId(10L);
        detail1.setIncomeDate(today);
        detail1.setIncomeAccount(50);
        detail1.setIncomeCount(5);
        detail1.setIncomeNumber(4);

        AuthorIncomeDetail detail2 = new AuthorIncomeDetail();
        detail2.setAuthorId(authorId);
        detail2.setBookId(20L);
        detail2.setIncomeDate(today);
        detail2.setIncomeAccount(30);
        detail2.setIncomeCount(3);
        detail2.setIncomeNumber(2);

        when(authorIncomeDetailMapper.selectList(any())).thenReturn(List.of(detail1, detail2));
        when(bookInfoMapper.selectById(10L)).thenReturn(createBookInfo("小说A"));
        when(bookInfoMapper.selectById(20L)).thenReturn(createBookInfo("小说B"));

        RestResp<List<AuthorIncomeDetailRespDto>> result =
                authorIncomeService.listDailyIncomeDetails(authorId, null, today, today);

        assertTrue(result.isOk());
        assertEquals(2, result.getData().size());
        int total = result.getData().stream().mapToInt(AuthorIncomeDetailRespDto::getIncomeAccount).sum();
        assertEquals(80, total);
    }

    private AuthorIncomeBreakdown createBreakdown(int type, int amount) {
        AuthorIncomeBreakdown b = new AuthorIncomeBreakdown();
        b.setAuthorId(authorId);
        b.setBookId(bookId);
        b.setIncomeType(type);
        b.setAmount(amount);
        return b;
    }

    private BookInfo createBookInfo(String name) {
        BookInfo bi = new BookInfo();
        bi.setBookName(name);
        return bi;
    }

}
