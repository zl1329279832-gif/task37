package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.core.common.constant.ErrorCodeEnum;
import io.github.xxyopen.novel.core.common.exception.BusinessException;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.config.FinanceProperties;
import io.github.xxyopen.novel.dao.entity.*;
import io.github.xxyopen.novel.dao.mapper.*;
import io.github.xxyopen.novel.dto.resp.BookContentAboutRespDto;
import io.github.xxyopen.novel.manager.cache.BookChapterCacheManager;
import io.github.xxyopen.novel.manager.cache.BookContentCacheManager;
import io.github.xxyopen.novel.manager.cache.BookInfoCacheManager;
import io.github.xxyopen.novel.manager.redis.RedisDistributedLockManager;
import io.github.xxyopen.novel.service.impl.ChapterPurchaseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 阅读券抵扣购买测试
 */
@ExtendWith(MockitoExtension.class)
class VoucherDeductionTest {

    @InjectMocks
    private ChapterPurchaseServiceImpl chapterPurchaseService;

    @Mock private BookChapterMapper bookChapterMapper;
    @Mock private BookInfoMapper bookInfoMapper;
    @Mock private BookContentMapper bookContentMapper;
    @Mock private UserInfoMapper userInfoMapper;
    @Mock private UserConsumeLogMapper userConsumeLogMapper;
    @Mock private AuthorIncomeDetailMapper authorIncomeDetailMapper;
    @Mock private AuthorIncomeMapper authorIncomeMapper;
    @Mock private AuthorInfoMapper authorInfoMapper;
    @Mock private ReadingVoucherMapper readingVoucherMapper;
    @Mock private BookChapterCacheManager bookChapterCacheManager;
    @Mock private BookInfoCacheManager bookInfoCacheManager;
    @Mock private BookContentCacheManager bookContentCacheManager;
    @Mock private RedisDistributedLockManager lockManager;
    @Mock private MembershipService membershipService;
    @Mock private SettlementService settlementService;

    private final Long userId = 1L;
    private final Long chapterId = 100L;
    private final Long bookId = 10L;
    private final Long authorId = 5L;
    private final Long voucherId = 200L;

    @BeforeEach
    void setUp() throws Exception {
        FinanceProperties props = new FinanceProperties();
        props.setDefaultChapterPrice(10);
        props.setTaxRate(20);
        props.setRefundWindowHours(72);
        var field = ChapterPurchaseServiceImpl.class.getDeclaredField("financeProperties");
        field.setAccessible(true);
        field.set(chapterPurchaseService, props);
    }

    @Test
    void testVoucherPurchase_freeChapter_noVoucherNeeded() {
        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        chapter.setIsVip(0);
        chapter.setIsFreeLimit(0);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        when(bookContentCacheManager.getBookContent(chapterId)).thenReturn("免费内容");
        lenient().when(bookChapterCacheManager.getChapter(chapterId)).thenReturn(null);
        lenient().when(bookInfoCacheManager.getBookInfo(bookId)).thenReturn(null);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.purchaseChapterWithVoucher(userId, chapterId, voucherId);

        assertTrue(result.isOk());
        verify(membershipService, never()).useVoucher(anyLong(), anyLong());
    }

    @Test
    void testVoucherPurchase_vipChapter_fullDeduction() {
        BookChapter chapter = buildVipChapter();
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        BookInfo book = buildBookInfo();
        when(bookInfoMapper.selectById(bookId)).thenReturn(book);

        when(userConsumeLogMapper.selectCount(any())).thenReturn(0L);

        ReadingVoucher voucher = new ReadingVoucher();
        voucher.setId(voucherId);
        voucher.setVoucherType(0);
        voucher.setDiscountAmount(0);
        voucher.setStatus(1);
        when(membershipService.useVoucher(voucherId, chapterId)).thenReturn(voucher);

        doAnswer(inv -> {
            UserConsumeLog log = inv.getArgument(0);
            log.setId(501L);
            return 1;
        }).when(userConsumeLogMapper).insert(any(UserConsumeLog.class));

        when(bookContentCacheManager.getBookContent(chapterId)).thenReturn("VIP内容");
        lenient().when(bookChapterCacheManager.getChapter(chapterId)).thenReturn(null);
        lenient().when(bookInfoCacheManager.getBookInfo(bookId)).thenReturn(null);

        PendingSettlement ps = new PendingSettlement();
        ps.setId(1L);
        when(settlementService.createPendingSettlement(
                eq(501L), eq(userId), eq(authorId), eq(bookId), eq(chapterId),
                eq(0), eq(3), eq(10), eq(0)))
                .thenReturn(ps);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.purchaseChapterWithVoucher(userId, chapterId, voucherId);

        assertTrue(result.isOk());
        verify(userInfoMapper, never()).deductBalance(anyLong(), anyInt());
        verify(membershipService).useVoucher(voucherId, chapterId);
        verify(userConsumeLogMapper).insert(argThat(log ->
                log.getPayType() == 3 && log.getAmount() == 0));
        verify(settlementService).createPendingSettlement(
                eq(501L), eq(userId), eq(authorId), eq(bookId), eq(chapterId),
                eq(0), eq(3), eq(10), eq(0));
    }

    @Test
    void testVoucherPurchase_invalidVoucher_throwsError() {
        BookChapter chapter = buildVipChapter();
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        BookInfo book = buildBookInfo();
        when(bookInfoMapper.selectById(bookId)).thenReturn(book);

        when(userConsumeLogMapper.selectCount(any())).thenReturn(0L);

        when(membershipService.useVoucher(voucherId, chapterId))
                .thenThrow(new BusinessException(ErrorCodeEnum.USER_VOUCHER_INVALID));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chapterPurchaseService.purchaseChapterWithVoucher(userId, chapterId, voucherId));

        assertEquals(ErrorCodeEnum.USER_VOUCHER_INVALID, ex.getErrorCodeEnum());
    }

    @Test
    void testVoucherPurchase_expiredVoucher_throwsError() {
        BookChapter chapter = buildVipChapter();
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        BookInfo book = buildBookInfo();
        when(bookInfoMapper.selectById(bookId)).thenReturn(book);

        when(userConsumeLogMapper.selectCount(any())).thenReturn(0L);

        when(membershipService.useVoucher(voucherId, chapterId))
                .thenThrow(new BusinessException(ErrorCodeEnum.USER_VOUCHER_EXPIRED));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chapterPurchaseService.purchaseChapterWithVoucher(userId, chapterId, voucherId));

        assertEquals(ErrorCodeEnum.USER_VOUCHER_EXPIRED, ex.getErrorCodeEnum());
    }

    private BookChapter buildVipChapter() {
        BookChapter c = new BookChapter();
        c.setId(chapterId);
        c.setBookId(bookId);
        c.setChapterName("VIP章节");
        c.setIsVip(1);
        c.setIsFreeLimit(0);
        c.setChapterPrice(10);
        return c;
    }

    private BookInfo buildBookInfo() {
        BookInfo b = new BookInfo();
        b.setId(bookId);
        b.setAuthorId(authorId);
        b.setIsFreeLimit(0);
        return b;
    }
}
