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
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 限免切换测试
 * 场景：章节级限免、全书级限免、取消限免、限免状态竞态
 */
@ExtendWith(MockitoExtension.class)
class FreeLimitToggleTest {

    @InjectMocks
    private ChapterPurchaseServiceImpl chapterPurchaseService;

    @Mock private BookChapterMapper bookChapterMapper;
    @Mock private BookInfoMapper bookInfoMapper;
    @Mock private BookContentMapper bookContentMapper;
    @Mock private UserInfoMapper userInfoMapper;
    @Mock private UserConsumeLogMapper userConsumeLogMapper;
    @Mock private AuthorIncomeDetailMapper authorIncomeDetailMapper;
    @Mock private AuthorInfoMapper authorInfoMapper;
    @Mock private BookChapterCacheManager bookChapterCacheManager;
    @Mock private BookInfoCacheManager bookInfoCacheManager;
    @Mock private BookContentCacheManager bookContentCacheManager;
    @Mock private RedisDistributedLockManager lockManager;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private AuthorIncomeMapper authorIncomeMapper;

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

        // TransactionTemplate 直接执行回调
        lenient().doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            var callback = (java.util.function.Consumer<org.springframework.transaction.TransactionStatus>) inv.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            var callback = (TransactionCallback<?>) inv.getArgument(0);
            return callback.doInTransaction(null);
        });

        // 通用缓存 mock
        lenient().when(bookChapterCacheManager.getChapter(chapterId)).thenReturn(
                BookChapterRespDto.builder().id(chapterId).bookId(bookId).chapterName("VIP章节").build());
        lenient().when(bookInfoCacheManager.getBookInfo(bookId)).thenReturn(
                BookInfoRespDto.builder().id(bookId).bookName("测试小说").build());
        lenient().when(bookContentCacheManager.getBookContent(chapterId)).thenReturn("VIP章节的完整内容这是一段测试文本");
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
    void testFreeLimitToggledDuringPurchase_returnsFreeContentNoCharge() {
        // Phase 1（锁外）: isFreeLimit=0 → 进入购买流程
        // Phase 3（锁内重检）: isFreeLimit=1 → 应返回限免内容，不扣费
        BookChapter chapterNotFree = buildVipChapter();
        chapterNotFree.setIsFreeLimit(0);

        BookChapter chapterNowFree = buildVipChapter();
        chapterNowFree.setIsFreeLimit(1);

        BookInfo bookInfo = new BookInfo();
        bookInfo.setId(bookId);
        bookInfo.setAuthorId(5L);
        bookInfo.setIsFreeLimit(0);

        // 第1次 selectById（Phase 1 锁外预检）→ 无限免
        // 第2次 selectById（Phase 3 锁内限免重检）→ 已限免（章节级 isFreeLimit=1，不再查 BookInfo）
        when(bookChapterMapper.selectById(chapterId))
                .thenReturn(chapterNotFree)   // Phase 1
                .thenReturn(chapterNowFree);  // Phase 3 锁内重检

        when(bookInfoMapper.selectById(bookId)).thenReturn(bookInfo);

        when(userConsumeLogMapper.selectCount(any())).thenReturn(0L);
        when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);

        UserInfo user = new UserInfo();
        user.setAccountBalance(100L);
        when(userInfoMapper.selectById(userId)).thenReturn(user);

        // 执行购买
        RestResp<BookContentAboutRespDto> result = chapterPurchaseService.purchaseChapter(userId, chapterId);

        // 应返回完整内容（限免），不应扣费
        assertTrue(result.isOk());
        assertNotNull(result.getData().getBookContent());

        // 验证：未扣余额，未创建消费记录
        verify(userInfoMapper, never()).deductBalance(anyLong(), anyInt());
        verify(userConsumeLogMapper, never()).insertIdempotent(
                anyLong(), anyInt(), anyInt(), anyLong(), anyString(), anyInt(), anyLong());
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
