package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.dao.entity.AuthorIncomeDetail;
import io.github.xxyopen.novel.dao.entity.BookInfo;
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
 * 作者收入统计测试
 * 场景：多用户多章节购买后日收入金额/次数/人数统计
 */
@ExtendWith(MockitoExtension.class)
class AuthorIncomeStatisticsTest {

    @InjectMocks
    private AuthorIncomeServiceImpl authorIncomeService;

    @Mock
    private AuthorIncomeDetailMapper authorIncomeDetailMapper;

    @Mock
    private AuthorIncomeMapper authorIncomeMapper;

    @Mock
    private BookInfoMapper bookInfoMapper;

    private final Long authorId = 5L;
    private final Long bookId = 10L;

    @Test
    void testDailyIncome_multipleUsersAndChapters() {
        LocalDate today = LocalDate.now();

        // 模拟 DB 返回：3 个用户购买了 4 个章节，总计 40 屋币
        AuthorIncomeDetail detail = new AuthorIncomeDetail();
        detail.setAuthorId(authorId);
        detail.setBookId(bookId);
        detail.setIncomeDate(today);
        detail.setIncomeAccount(40);  // 4个章节 x 10屋币
        detail.setIncomeCount(4);     // 4次订阅
        detail.setIncomeNumber(3);    // 3个不同用户

        when(authorIncomeDetailMapper.selectList(any())).thenReturn(List.of(detail));

        BookInfo bookInfo = new BookInfo();
        bookInfo.setId(bookId);
        bookInfo.setBookName("测试小说");
        when(bookInfoMapper.selectById(bookId)).thenReturn(bookInfo);

        // 查询日收入明细
        RestResp<List<AuthorIncomeDetailRespDto>> result =
                authorIncomeService.listDailyIncomeDetails(authorId, bookId, today, today);

        assertTrue(result.isOk());
        assertEquals(1, result.getData().size());

        AuthorIncomeDetailRespDto resp = result.getData().get(0);
        assertEquals(40, resp.getIncomeAccount(), "订阅总额应为40屋币");
        assertEquals(4, resp.getIncomeCount(), "订阅次数应为4次");
        assertEquals(3, resp.getIncomeNumber(), "订阅人数应为3人");
        assertEquals(bookId, resp.getBookId());
        assertEquals("测试小说", resp.getBookName());
    }

    @Test
    void testDailyIncome_sameUserBuysMore_numberNotDoubleCounted() {
        LocalDate today = LocalDate.now();

        // 用户A再买1个章节后：总额50，次数5，人数仍为3
        AuthorIncomeDetail detail = new AuthorIncomeDetail();
        detail.setAuthorId(authorId);
        detail.setBookId(bookId);
        detail.setIncomeDate(today);
        detail.setIncomeAccount(50);  // 5 x 10
        detail.setIncomeCount(5);     // 5次
        detail.setIncomeNumber(3);    // 仍3人（用户A不重复计数）

        when(authorIncomeDetailMapper.selectList(any())).thenReturn(List.of(detail));

        BookInfo bookInfo = new BookInfo();
        bookInfo.setId(bookId);
        bookInfo.setBookName("测试小说");
        when(bookInfoMapper.selectById(bookId)).thenReturn(bookInfo);

        RestResp<List<AuthorIncomeDetailRespDto>> result =
                authorIncomeService.listDailyIncomeDetails(authorId, bookId, today, today);

        assertTrue(result.isOk());
        AuthorIncomeDetailRespDto resp = result.getData().get(0);
        assertEquals(50, resp.getIncomeAccount());
        assertEquals(5, resp.getIncomeCount());
        assertEquals(3, resp.getIncomeNumber(), "同一用户不应重复计数");
    }

    @Test
    void testDailyIncome_allBooks_queryWithoutBookId() {
        LocalDate today = LocalDate.now();

        // 作者所有作品的日收入
        AuthorIncomeDetail detail1 = new AuthorIncomeDetail();
        detail1.setAuthorId(authorId);
        detail1.setBookId(10L);
        detail1.setIncomeDate(today);
        detail1.setIncomeAccount(30);
        detail1.setIncomeCount(3);
        detail1.setIncomeNumber(2);

        AuthorIncomeDetail detail2 = new AuthorIncomeDetail();
        detail2.setAuthorId(authorId);
        detail2.setBookId(20L);
        detail2.setIncomeDate(today);
        detail2.setIncomeAccount(20);
        detail2.setIncomeCount(2);
        detail2.setIncomeNumber(1);

        when(authorIncomeDetailMapper.selectList(any())).thenReturn(List.of(detail1, detail2));

        BookInfo book1 = new BookInfo();
        book1.setId(10L);
        book1.setBookName("小说A");
        BookInfo book2 = new BookInfo();
        book2.setId(20L);
        book2.setBookName("小说B");
        when(bookInfoMapper.selectById(10L)).thenReturn(book1);
        when(bookInfoMapper.selectById(20L)).thenReturn(book2);

        // 查询全部作品收入（bookId = null）
        RestResp<List<AuthorIncomeDetailRespDto>> result =
                authorIncomeService.listDailyIncomeDetails(authorId, null, today, today);

        assertTrue(result.isOk());
        assertEquals(2, result.getData().size());
    }

    @Test
    void testDailyIncome_emptyResult() {
        when(authorIncomeDetailMapper.selectList(any())).thenReturn(List.of());

        RestResp<List<AuthorIncomeDetailRespDto>> result =
                authorIncomeService.listDailyIncomeDetails(authorId, bookId, LocalDate.now(), LocalDate.now());

        assertTrue(result.isOk());
        assertTrue(result.getData().isEmpty());
    }

    @Test
    void testDailyIncome_afterRefund_incomeDecreased() {
        LocalDate today = LocalDate.now();

        // 场景：原有 4 次购买 40 屋币，退款 1 次后变为 3 次 30 屋币
        AuthorIncomeDetail detail = new AuthorIncomeDetail();
        detail.setAuthorId(authorId);
        detail.setBookId(bookId);
        detail.setIncomeDate(today);
        detail.setIncomeAccount(30);  // 退款后 30 屋币
        detail.setIncomeCount(3);     // 退款后 3 次
        detail.setIncomeNumber(2);    // 退款后 2 人（退款用户无其他购买）

        when(authorIncomeDetailMapper.selectList(any())).thenReturn(List.of(detail));

        BookInfo bookInfo = new BookInfo();
        bookInfo.setId(bookId);
        bookInfo.setBookName("测试小说");
        when(bookInfoMapper.selectById(bookId)).thenReturn(bookInfo);

        RestResp<List<AuthorIncomeDetailRespDto>> result =
                authorIncomeService.listDailyIncomeDetails(authorId, bookId, today, today);

        assertTrue(result.isOk());
        assertEquals(1, result.getData().size());

        AuthorIncomeDetailRespDto resp = result.getData().get(0);
        assertEquals(30, resp.getIncomeAccount(), "退款后订阅总额应为30屋币");
        assertEquals(3, resp.getIncomeCount(), "退款后订阅次数应为3次");
        assertEquals(2, resp.getIncomeNumber(), "退款后订阅人数应为2人");
    }
}
