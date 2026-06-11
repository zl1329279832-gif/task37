package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.core.common.constant.ErrorCodeEnum;
import io.github.xxyopen.novel.core.common.exception.BusinessException;
import io.github.xxyopen.novel.core.config.FinanceProperties;
import io.github.xxyopen.novel.dao.entity.*;
import io.github.xxyopen.novel.dao.mapper.*;
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
 * 重复购买幂等性测试
 * 场景：用户已购买章节后再次购买，应被拒绝
 */
@ExtendWith(MockitoExtension.class)
class ChapterPurchaseIdempotencyTest {

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
    }

    @Test
    void testDuplicatePurchase_throwsAlreadyPurchased() {
        // 准备 VIP 章节
        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        chapter.setIsVip(1);
        chapter.setIsFreeLimit(0);
        chapter.setChapterPrice(10);
        chapter.setChapterName("VIP章节");
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        // 模拟已购买（幂等检查返回记录数 > 0）
        when(userConsumeLogMapper.selectCount(any())).thenReturn(1L);

        // 执行并验证
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chapterPurchaseService.purchaseChapter(userId, chapterId));

        assertEquals(ErrorCodeEnum.USER_CHAPTER_ALREADY_PURCHASED, ex.getErrorCodeEnum());

        // 验证：未扣余额，未创建消费记录
        verify(userInfoMapper, never()).deductBalance(anyLong(), anyInt());
        verify(userConsumeLogMapper, never()).insertIdempotent(
                anyLong(), anyInt(), anyInt(), anyLong(), anyString(), anyInt(), anyLong());
    }

    @Test
    void testDuplicatePurchaseAfterLockAcquired_stillBlocked() {
        // 准备 VIP 章节
        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        chapter.setIsVip(1);
        chapter.setIsFreeLimit(0);
        chapter.setChapterPrice(10);
        chapter.setChapterName("VIP章节");
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        BookInfo bookInfo = new BookInfo();
        bookInfo.setId(bookId);
        bookInfo.setAuthorId(5L);
        bookInfo.setIsFreeLimit(0);
        when(bookInfoMapper.selectById(bookId)).thenReturn(bookInfo);

        // 第一次幂等检查通过（锁外），锁内第二次检查发现已购买
        when(userConsumeLogMapper.selectCount(any()))
                .thenReturn(0L)   // 锁外预检：未购买
                .thenReturn(1L);  // 锁内双重检查：已购买

        when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);

        UserInfo user = new UserInfo();
        user.setAccountBalance(100L);
        when(userInfoMapper.selectById(userId)).thenReturn(user);

        // 执行
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chapterPurchaseService.purchaseChapter(userId, chapterId));

        assertEquals(ErrorCodeEnum.USER_CHAPTER_ALREADY_PURCHASED, ex.getErrorCodeEnum());

        // 验证未扣余额
        verify(userInfoMapper, never()).deductBalance(anyLong(), anyInt());
    }

    @Test
    void testPurchaseNonVipChapter_returnsDirectly() {
        // 准备免费章节
        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        chapter.setIsVip(0);
        chapter.setChapterName("免费章节");
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        when(bookContentCacheManager.getBookContent(chapterId)).thenReturn("免费章节内容");
        when(bookChapterCacheManager.getChapter(chapterId)).thenReturn(
                io.github.xxyopen.novel.dto.resp.BookChapterRespDto.builder()
                        .id(chapterId).chapterName("免费章节").build());
        when(bookInfoCacheManager.getBookInfo(bookId)).thenReturn(
                io.github.xxyopen.novel.dto.resp.BookInfoRespDto.builder()
                        .id(bookId).bookName("测试小说").build());

        // 执行
        var result = chapterPurchaseService.purchaseChapter(userId, chapterId);

        // 验证：直接返回内容
        assertTrue(result.isOk());
        assertNotNull(result.getData().getBookContent());
        assertEquals(false, result.getData().getNeedPurchase());

        // 验证：未进行任何财务操作
        verify(userInfoMapper, never()).deductBalance(anyLong(), anyInt());
        verify(userConsumeLogMapper, never()).insertIdempotent(
                anyLong(), anyInt(), anyInt(), anyLong(), anyString(), anyInt(), anyLong());
    }

    @Test
    void testDatabaseIdempotency_insertIdempotentReturnsZero() {
        // 锁外+锁内幂等检查都通过，但数据库级幂等插入返回0（极端并发下重复）
        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        chapter.setIsVip(1);
        chapter.setIsFreeLimit(0);
        chapter.setChapterPrice(10);
        chapter.setChapterName("VIP章节");
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        BookInfo bookInfo = new BookInfo();
        bookInfo.setId(bookId);
        bookInfo.setAuthorId(5L);
        bookInfo.setIsFreeLimit(0);
        when(bookInfoMapper.selectById(bookId)).thenReturn(bookInfo);

        when(userConsumeLogMapper.selectCount(any())).thenReturn(0L);
        when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);

        UserInfo user = new UserInfo();
        user.setAccountBalance(100L);
        when(userInfoMapper.selectById(userId)).thenReturn(user);

        when(userInfoMapper.deductBalance(eq(userId), eq(10))).thenReturn(1);

        // 数据库级幂等插入返回0（已有重复记录）
        when(userConsumeLogMapper.insertIdempotent(
                anyLong(), anyInt(), anyInt(), anyLong(), anyString(), anyInt(), anyLong()))
                .thenReturn(0);

        // 执行应抛已购买异常
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chapterPurchaseService.purchaseChapter(userId, chapterId));

        assertEquals(ErrorCodeEnum.USER_CHAPTER_ALREADY_PURCHASED, ex.getErrorCodeEnum());
    }
}
