package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.core.config.FinanceProperties;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.dao.entity.*;
import io.github.xxyopen.novel.dao.mapper.*;
import io.github.xxyopen.novel.dto.resp.BookChapterRespDto;
import io.github.xxyopen.novel.dto.resp.BookContentAboutRespDto;
import io.github.xxyopen.novel.dto.resp.BookInfoRespDto;
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
 * 限免切换测试
 * 场景：章节级限免、全书级限免、取消限免
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FreeLimitToggleTest {

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
    @Mock private BookChapterCacheManager bookChapterCacheManager;
    @Mock private BookInfoCacheManager bookInfoCacheManager;
    @Mock private BookContentCacheManager bookContentCacheManager;
    @Mock private RedisDistributedLockManager lockManager;
    @Mock private io.github.xxyopen.novel.manager.cache.MemberInfoCacheManager memberInfoCacheManager;
    @Mock private MemberInfoMapper memberInfoMapper;
    @Mock private ReadingCouponMapper readingCouponMapper;
    @Mock private MemberBenefitsSnapshotMapper memberBenefitsSnapshotMapper;
    @Mock private SettlementService settlementService;
    @Mock private RefundFreezeMapper refundFreezeMapper;

    private final Long userId = 1L;
    private final Long chapterId = 100L;
    private final Long bookId = 10L;

    @BeforeEach
    void setUp() throws Exception {
        FinanceProperties props = new FinanceProperties();
        props.setDefaultChapterPrice(10);
        var field = ChapterPurchaseServiceImpl.class.getDeclaredField("financeProperties");
        field.setAccessible(true);
        field.set(chapterPurchaseService, props);

        // 通用缓存 mock
        lenient().when(bookChapterCacheManager.getChapter(chapterId)).thenReturn(
                BookChapterRespDto.builder().id(chapterId).bookId(bookId).chapterName("VIP章节").build());
        lenient().when(bookInfoCacheManager.getBookInfo(bookId)).thenReturn(
                BookInfoRespDto.builder().id(bookId).bookName("测试小说").build());
        lenient().when(bookContentCacheManager.getBookContent(chapterId)).thenReturn("VIP章节的完整内容这是一段测试文本");

        // 会员和结算相关默认 mock
        lenient().when(memberInfoCacheManager.getActiveMemberInfo(anyLong())).thenReturn(null);
        lenient().when(settlementService.createPendingSettlement(
                anyLong(), anyLong(), anyLong(), anyLong(), any(), anyInt(),
                anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new PendingSettlement());
    }

    @Test
    void testChapterLevelFreeLimit_returnsFullContent() {
        // 章节级限免 isFreeLimit=1
        BookChapter chapter = buildVipChapter();
        chapter.setIsFreeLimit(1);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.getChapterContentWithAccessControl(userId, chapterId);

        assertTrue(result.isOk());
        assertNotNull(result.getData().getBookContent());
        assertFalse(result.getData().getNeedPurchase());
        assertTrue(result.getData().getIsFreeLimit());

        // 未进行购买相关操作
        verify(userConsumeLogMapper, never()).selectCount(any());
    }

    @Test
    void testBookLevelFreeLimit_returnsFullContent() {
        // 章节级无限免，但全书限免
        BookChapter chapter = buildVipChapter();
        chapter.setIsFreeLimit(0);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        BookInfo bookInfo = new BookInfo();
        bookInfo.setId(bookId);
        bookInfo.setIsFreeLimit(1); // 全书限免
        when(bookInfoMapper.selectById(bookId)).thenReturn(bookInfo);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.getChapterContentWithAccessControl(userId, chapterId);

        assertTrue(result.isOk());
        assertNotNull(result.getData().getBookContent());
        assertFalse(result.getData().getNeedPurchase());
    }

    @Test
    void testFreeLimitRemoved_returnsPreview() {
        // 限免已取消，用户未购买
        BookChapter chapter = buildVipChapter();
        chapter.setIsFreeLimit(0);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        BookInfo bookInfo = new BookInfo();
        bookInfo.setId(bookId);
        bookInfo.setIsFreeLimit(0); // 无限免
        when(bookInfoMapper.selectById(bookId)).thenReturn(bookInfo);

        // 用户未购买
        when(userConsumeLogMapper.selectCount(any())).thenReturn(0L);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.getChapterContentWithAccessControl(userId, chapterId);

        assertTrue(result.isOk());
        assertNull(result.getData().getBookContent()); // 完整内容不返回
        assertTrue(result.getData().getNeedPurchase()); // 需要购买
        assertNotNull(result.getData().getPreviewContent()); // 返回预览
    }

    @Test
    void testFreeChapter_bypassesAllChecks() {
        // 免费章节（isVip=0），任何用户直接获取完整内容
        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        chapter.setIsVip(0);
        chapter.setChapterName("免费章节");
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.getChapterContentWithAccessControl(null, chapterId);

        assertTrue(result.isOk());
        assertNotNull(result.getData().getBookContent());
        assertFalse(result.getData().getNeedPurchase());
    }

    @Test
    void testPurchasedChapter_returnsFullContent() {
        // VIP章节，用户已购买
        BookChapter chapter = buildVipChapter();
        chapter.setIsFreeLimit(0);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        BookInfo bookInfo = new BookInfo();
        bookInfo.setId(bookId);
        bookInfo.setIsFreeLimit(0);
        when(bookInfoMapper.selectById(bookId)).thenReturn(bookInfo);

        // 已购买
        when(userConsumeLogMapper.selectCount(any())).thenReturn(1L);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.getChapterContentWithAccessControl(userId, chapterId);

        assertTrue(result.isOk());
        assertNotNull(result.getData().getBookContent());
        assertTrue(result.getData().getIsPurchased());
    }

    @Test
    void testPurchase_freeLimitToggledDuringLockWait_noCharge() {
        // 场景：用户发起购买时章节非限免，但获取锁后章节已变为限免
        BookChapter chapter = buildVipChapter();
        chapter.setIsFreeLimit(0);

        BookInfo bookInfo = new BookInfo();
        bookInfo.setId(bookId);
        bookInfo.setAuthorId(5L);
        bookInfo.setIsFreeLimit(0);

        // 第1次 selectById 返回非限免（锁外预检），第2次返回限免（锁内双重检查）
        BookChapter freeLimitChapter = buildVipChapter();
        freeLimitChapter.setIsFreeLimit(1);
        when(bookChapterMapper.selectById(chapterId))
                .thenReturn(chapter)          // 锁外：非限免
                .thenReturn(freeLimitChapter); // 锁内：已切为限免

        when(bookInfoMapper.selectById(bookId)).thenReturn(bookInfo);
        when(userConsumeLogMapper.selectCount(any())).thenReturn(0L); // 未购买
        when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.purchaseChapter(userId, chapterId);

        assertTrue(result.isOk());
        assertNotNull(result.getData().getBookContent());
        assertFalse(result.getData().getNeedPurchase());

        // 验证：未扣余额，未创建消费记录
        verify(userInfoMapper, never()).deductBalance(anyLong(), anyInt());
        verify(userConsumeLogMapper, never()).insert(any());
    }

    private BookChapter buildVipChapter() {
        BookChapter ch = new BookChapter();
        ch.setId(chapterId);
        ch.setBookId(bookId);
        ch.setIsVip(1);
        ch.setChapterPrice(10);
        ch.setChapterName("VIP测试章节");
        return ch;
    }
}
