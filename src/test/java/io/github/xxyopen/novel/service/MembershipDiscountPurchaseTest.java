package io.github.xxyopen.novel.service;

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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 会员折扣购买测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MembershipDiscountPurchaseTest {

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

    @BeforeEach
    void setUp() throws Exception {
        FinanceProperties props = new FinanceProperties();
        props.setDefaultChapterPrice(10);
        props.setTaxRate(20);
        props.setRefundWindowHours(72);
        props.setMembershipSubsidyRate(50);
        var field = ChapterPurchaseServiceImpl.class.getDeclaredField("financeProperties");
        field.setAccessible(true);
        field.set(chapterPurchaseService, props);
    }

    private void stubCommonPurchaseFlow(BookChapter chapter, BookInfo book, UserMembership membership) {
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);
        when(bookInfoMapper.selectById(bookId)).thenReturn(book);
        when(membershipService.getActiveMembershipEntity(userId)).thenReturn(membership);
        when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        when(userConsumeLogMapper.selectCount(any())).thenReturn(0L);

        doAnswer(inv -> {
            UserConsumeLog log = inv.getArgument(0);
            log.setId(501L);
            return 1;
        }).when(userConsumeLogMapper).insert(any(UserConsumeLog.class));

        when(bookContentCacheManager.getBookContent(chapterId)).thenReturn("测试内容");
        lenient().when(bookChapterCacheManager.getChapter(chapterId)).thenReturn(null);
        lenient().when(bookInfoCacheManager.getBookInfo(bookId)).thenReturn(null);
    }

    @Test
    void testMemberDiscountPurchase_paysDiscountPrice_withSubsidy() {
        BookChapter chapter = buildVipChapter(10);
        BookInfo book = buildBookInfo();
        UserMembership membership = buildMembership(2, 80, 0, 20);

        stubCommonPurchaseFlow(chapter, book, membership);

        when(membershipService.canFreeRead(membership)).thenReturn(false);
        when(membershipService.calculateDiscountPrice(10, membership)).thenReturn(8);

        UserInfo userInfo = new UserInfo();
        userInfo.setAccountBalance(100L);
        when(userInfoMapper.selectById(userId)).thenReturn(userInfo);
        when(userInfoMapper.deductBalance(userId, 8)).thenReturn(1);

        PendingSettlement ps = new PendingSettlement();
        ps.setId(1L);
        when(settlementService.createPendingSettlement(
                eq(501L), eq(userId), eq(authorId), eq(bookId), eq(chapterId),
                eq(8), eq(2), eq(1), eq(0)))
                .thenReturn(ps);

        RestResp<BookContentAboutRespDto> result = chapterPurchaseService.purchaseChapter(userId, chapterId);

        assertTrue(result.isOk());
        verify(userInfoMapper).deductBalance(userId, 8);
        verify(userConsumeLogMapper).insert(argThat(log ->
                log.getPayType() == 2 && log.getAmount() == 8));
        verify(settlementService).createPendingSettlement(
                eq(501L), eq(userId), eq(authorId), eq(bookId), eq(chapterId),
                eq(8), eq(2), eq(1), eq(0));
    }

    @Test
    void testMemberFreeRead_noBalanceDeduction_fullSubsidy() {
        BookChapter chapter = buildVipChapter(10);
        BookInfo book = buildBookInfo();
        UserMembership membership = buildMembership(2, 80, 2, 20);

        stubCommonPurchaseFlow(chapter, book, membership);

        when(membershipService.canFreeRead(membership)).thenReturn(true);
        when(membershipService.consumeFreeQuota(membership.getId())).thenReturn(true);

        PendingSettlement ps = new PendingSettlement();
        ps.setId(1L);
        when(settlementService.createPendingSettlement(
                eq(501L), eq(userId), eq(authorId), eq(bookId), eq(chapterId),
                eq(0), eq(1), eq(10), eq(0)))
                .thenReturn(ps);

        RestResp<BookContentAboutRespDto> result = chapterPurchaseService.purchaseChapter(userId, chapterId);

        assertTrue(result.isOk());
        verify(userInfoMapper, never()).deductBalance(anyLong(), anyInt());
        verify(userConsumeLogMapper).insert(argThat(log ->
                log.getPayType() == 1 && log.getAmount() == 0));
        verify(membershipService).consumeFreeQuota(membership.getId());
        verify(settlementService).createPendingSettlement(
                eq(501L), eq(userId), eq(authorId), eq(bookId), eq(chapterId),
                eq(0), eq(1), eq(10), eq(0));
    }

    @Test
    void testMemberFreeRead_quotaExhausted_fallsBackToDiscount() {
        BookChapter chapter = buildVipChapter(10);
        BookInfo book = buildBookInfo();
        UserMembership membership = buildMembership(2, 80, 1, 20);

        stubCommonPurchaseFlow(chapter, book, membership);

        when(membershipService.canFreeRead(membership)).thenReturn(true);
        when(membershipService.consumeFreeQuota(membership.getId())).thenReturn(false);
        when(membershipService.calculateDiscountPrice(10, membership)).thenReturn(8);

        UserInfo userInfo = new UserInfo();
        userInfo.setAccountBalance(100L);
        when(userInfoMapper.selectById(userId)).thenReturn(userInfo);
        when(userInfoMapper.deductBalance(userId, 8)).thenReturn(1);

        PendingSettlement ps = new PendingSettlement();
        ps.setId(1L);
        when(settlementService.createPendingSettlement(
                eq(501L), eq(userId), eq(authorId), eq(bookId), eq(chapterId),
                eq(8), eq(2), eq(1), eq(0)))
                .thenReturn(ps);

        RestResp<BookContentAboutRespDto> result = chapterPurchaseService.purchaseChapter(userId, chapterId);

        assertTrue(result.isOk());
        verify(userInfoMapper).deductBalance(userId, 8);
        verify(userConsumeLogMapper).insert(argThat(log ->
                log.getPayType() == 2 && log.getAmount() == 8));
    }

    private BookChapter buildVipChapter(int price) {
        BookChapter c = new BookChapter();
        c.setId(chapterId);
        c.setBookId(bookId);
        c.setChapterName("VIP章节");
        c.setIsVip(1);
        c.setIsFreeLimit(0);
        c.setChapterPrice(price);
        return c;
    }

    private BookInfo buildBookInfo() {
        BookInfo b = new BookInfo();
        b.setId(bookId);
        b.setAuthorId(authorId);
        b.setIsFreeLimit(0);
        return b;
    }

    private UserMembership buildMembership(int level, int discountRate, int freeUsed, int freeQuota) {
        UserMembership m = new UserMembership();
        m.setId(100L);
        m.setUserId(userId);
        m.setMembershipLevel(level);
        m.setDiscountRate(discountRate);
        m.setFreeChapterUsed(freeUsed);
        m.setFreeChapterQuota(freeQuota);
        m.setStatus(0);
        return m;
    }
}
