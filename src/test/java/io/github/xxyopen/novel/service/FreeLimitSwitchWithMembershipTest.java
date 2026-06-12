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
 * 限免切换测试（含会员场景）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FreeLimitSwitchWithMembershipTest {

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
    void testChapterFreeLimit_memberUser_returnsContentWithoutCharge() {
        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        chapter.setIsVip(1);
        chapter.setIsFreeLimit(1); // 章节级限免
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        when(bookContentCacheManager.getBookContent(chapterId)).thenReturn("限免内容");
        lenient().when(bookChapterCacheManager.getChapter(chapterId)).thenReturn(null);
        lenient().when(bookInfoCacheManager.getBookInfo(bookId)).thenReturn(null);

        RestResp<BookContentAboutRespDto> result = chapterPurchaseService.purchaseChapter(userId, chapterId);

        assertTrue(result.isOk());
        assertFalse(result.getData().getNeedPurchase());

        verify(membershipService, never()).consumeFreeQuota(anyLong());
        verify(userInfoMapper, never()).deductBalance(anyLong(), anyInt());
        verify(settlementService, never()).createPendingSettlement(
                any(), anyLong(), anyLong(), anyLong(), anyLong(),
                anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void testBookFreeLimit_memberUser_returnsContentWithoutCharge() {
        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        chapter.setIsVip(1);
        chapter.setIsFreeLimit(0);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        BookInfo book = new BookInfo();
        book.setId(bookId);
        book.setAuthorId(authorId);
        book.setIsFreeLimit(1); // 全书限免
        when(bookInfoMapper.selectById(bookId)).thenReturn(book);

        when(bookContentCacheManager.getBookContent(chapterId)).thenReturn("限免内容");
        lenient().when(bookChapterCacheManager.getChapter(chapterId)).thenReturn(null);
        lenient().when(bookInfoCacheManager.getBookInfo(bookId)).thenReturn(null);

        RestResp<BookContentAboutRespDto> result = chapterPurchaseService.purchaseChapter(userId, chapterId);

        assertTrue(result.isOk());
        verify(membershipService, never()).consumeFreeQuota(anyLong());
    }

    @Test
    void testFreeLimitToggledDuringLock_memberNotCharged() {
        // Phase 1: 非限免
        BookChapter chapterPhase1 = new BookChapter();
        chapterPhase1.setId(chapterId);
        chapterPhase1.setBookId(bookId);
        chapterPhase1.setIsVip(1);
        chapterPhase1.setIsFreeLimit(0);

        // Phase 3: 锁内变为限免
        BookChapter chapterPhase3 = new BookChapter();
        chapterPhase3.setId(chapterId);
        chapterPhase3.setBookId(bookId);
        chapterPhase3.setIsVip(1);
        chapterPhase3.setIsFreeLimit(1);

        when(bookChapterMapper.selectById(chapterId))
                .thenReturn(chapterPhase1)   // Phase 1
                .thenReturn(chapterPhase3);  // Phase 3 double-check

        BookInfo book = new BookInfo();
        book.setId(bookId);
        book.setAuthorId(authorId);
        book.setIsFreeLimit(0);
        when(bookInfoMapper.selectById(bookId)).thenReturn(book);

        // These are called before lock, so they must be stubbed
        lenient().when(membershipService.getActiveMembershipEntity(userId)).thenReturn(null);
        when(userConsumeLogMapper.selectCount(any())).thenReturn(0L);
        when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);

        when(bookContentCacheManager.getBookContent(chapterId)).thenReturn("限免内容");
        lenient().when(bookChapterCacheManager.getChapter(chapterId)).thenReturn(null);
        lenient().when(bookInfoCacheManager.getBookInfo(bookId)).thenReturn(null);

        RestResp<BookContentAboutRespDto> result = chapterPurchaseService.purchaseChapter(userId, chapterId);

        assertTrue(result.isOk());
        verify(userInfoMapper, never()).deductBalance(anyLong(), anyInt());
        verify(membershipService, never()).consumeFreeQuota(anyLong());
    }

    @Test
    void testAccessControl_freeLimitChapter_showsFreeLimitFlag() {
        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        chapter.setIsVip(1);
        chapter.setIsFreeLimit(1);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        when(bookContentCacheManager.getBookContent(chapterId)).thenReturn("限免内容");
        lenient().when(bookChapterCacheManager.getChapter(chapterId)).thenReturn(null);
        lenient().when(bookInfoCacheManager.getBookInfo(bookId)).thenReturn(null);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.getChapterContentWithAccessControl(userId, chapterId);

        assertTrue(result.isOk());
        assertTrue(result.getData().getIsFreeLimit());
        assertFalse(result.getData().getNeedPurchase());
    }

    @Test
    void testAccessControl_vipChapter_memberUser_showsMembershipInfo() {
        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        chapter.setIsVip(1);
        chapter.setIsFreeLimit(0);
        chapter.setChapterPrice(10);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        BookInfo book = new BookInfo();
        book.setId(bookId);
        book.setIsFreeLimit(0);
        when(bookInfoMapper.selectById(bookId)).thenReturn(book);

        when(userConsumeLogMapper.selectCount(any())).thenReturn(0L);

        UserMembership membership = new UserMembership();
        membership.setId(100L);
        membership.setDiscountRate(80);
        membership.setStatus(0);
        when(membershipService.getActiveMembershipEntity(userId)).thenReturn(membership);
        when(membershipService.canFreeRead(membership)).thenReturn(true);
        when(membershipService.calculateDiscountPrice(10, membership)).thenReturn(8);
        when(membershipService.countAvailableVouchers(userId)).thenReturn(2);

        String longContent = "A".repeat(300);
        when(bookContentCacheManager.getBookContent(chapterId)).thenReturn(longContent);
        lenient().when(bookChapterCacheManager.getChapter(chapterId)).thenReturn(null);
        lenient().when(bookInfoCacheManager.getBookInfo(bookId)).thenReturn(null);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.getChapterContentWithAccessControl(userId, chapterId);

        assertTrue(result.isOk());
        BookContentAboutRespDto data = result.getData();
        assertTrue(data.getNeedPurchase());
        assertEquals(10, data.getChapterPrice());
        assertTrue(data.getIsMembershipFree());
        assertEquals(8, data.getMembershipDiscountPrice());
        assertEquals(2, data.getAvailableVoucherCount());
    }
}
