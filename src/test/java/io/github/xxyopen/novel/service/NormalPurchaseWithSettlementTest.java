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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 普通购买测试（含延迟结算）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NormalPurchaseWithSettlementTest {

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

    @Test
    void testNormalPurchase_createsConsumeLogAndPendingSettlement() {
        BookChapter chapter = buildVipChapter();
        // Called in phase1 and phase3 (double-check)
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        BookInfo book = buildBookInfo();
        // Called in isFreeLimit (x2) and for bookInfoEntity
        when(bookInfoMapper.selectById(bookId)).thenReturn(book);

        when(membershipService.getActiveMembershipEntity(userId)).thenReturn(null);
        when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        when(userConsumeLogMapper.selectCount(any())).thenReturn(0L);

        UserInfo userInfo = new UserInfo();
        userInfo.setId(userId);
        userInfo.setAccountBalance(100L);
        when(userInfoMapper.selectById(userId)).thenReturn(userInfo);
        when(userInfoMapper.deductBalance(userId, 10)).thenReturn(1);

        // Simulate auto-id on insert
        doAnswer(inv -> {
            UserConsumeLog log = inv.getArgument(0);
            log.setId(501L);
            return 1;
        }).when(userConsumeLogMapper).insert(any(UserConsumeLog.class));

        when(bookContentCacheManager.getBookContent(chapterId)).thenReturn("测试内容");
        lenient().when(bookChapterCacheManager.getChapter(chapterId)).thenReturn(null);
        lenient().when(bookInfoCacheManager.getBookInfo(bookId)).thenReturn(null);

        PendingSettlement ps = new PendingSettlement();
        ps.setId(1L);
        when(settlementService.createPendingSettlement(
                eq(501L), eq(userId), eq(authorId), eq(bookId), eq(chapterId),
                eq(10), eq(0), eq(0), eq(0)))
                .thenReturn(ps);

        RestResp<BookContentAboutRespDto> result = chapterPurchaseService.purchaseChapter(userId, chapterId);

        assertTrue(result.isOk());

        verify(userInfoMapper).deductBalance(userId, 10);
        verify(userConsumeLogMapper).insert(argThat(log ->
                log.getPayType() == 0 && log.getAmount() == 10));
        verify(settlementService).createPendingSettlement(
                eq(501L), eq(userId), eq(authorId), eq(bookId), eq(chapterId),
                eq(10), eq(0), eq(0), eq(0));
        verify(authorIncomeDetailMapper, never()).upsertDailyIncome(
                anyLong(), anyLong(), any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void testNormalPurchase_balanceInsufficient_throws() {
        BookChapter chapter = buildVipChapter();
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        BookInfo book = buildBookInfo();
        when(bookInfoMapper.selectById(bookId)).thenReturn(book);

        when(membershipService.getActiveMembershipEntity(userId)).thenReturn(null);
        when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        when(userConsumeLogMapper.selectCount(any())).thenReturn(0L);

        UserInfo userInfo = new UserInfo();
        userInfo.setId(userId);
        userInfo.setAccountBalance(5L); // insufficient
        when(userInfoMapper.selectById(userId)).thenReturn(userInfo);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chapterPurchaseService.purchaseChapter(userId, chapterId));

        assertEquals(ErrorCodeEnum.USER_BALANCE_INSUFFICIENT, ex.getErrorCodeEnum());
        verify(settlementService, never()).createPendingSettlement(
                any(), anyLong(), anyLong(), anyLong(), anyLong(),
                anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void testNormalPurchase_duplicatePurchase_throws() {
        BookChapter chapter = buildVipChapter();
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        BookInfo book = buildBookInfo();
        when(bookInfoMapper.selectById(bookId)).thenReturn(book);

        when(userConsumeLogMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chapterPurchaseService.purchaseChapter(userId, chapterId));

        assertEquals(ErrorCodeEnum.USER_CHAPTER_ALREADY_PURCHASED, ex.getErrorCodeEnum());
    }

    private BookChapter buildVipChapter() {
        BookChapter c = new BookChapter();
        c.setId(chapterId);
        c.setBookId(bookId);
        c.setChapterName("VIP测试章节");
        c.setIsVip(1);
        c.setIsFreeLimit(0);
        c.setChapterPrice(0);
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
