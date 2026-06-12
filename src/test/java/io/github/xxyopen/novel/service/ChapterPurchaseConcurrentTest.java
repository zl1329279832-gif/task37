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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 并发购买测试
 * 场景：10个线程同时购买同一章节，验证仅1次成功
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChapterPurchaseConcurrentTest {

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
    @Mock private ReadingVoucherMapper readingVoucherMapper;
    @Mock private MembershipService membershipService;
    @Mock private SettlementService settlementService;

    private final Long userId = 1L;
    private final Long chapterId = 100L;
    private final Long bookId = 10L;
    private final Long authorId = 5L;
    private final int chapterPrice = 10;

    @BeforeEach
    void setUp() {
        // 默认配置
        lenient().when(bookChapterMapper.selectById(chapterId)).thenReturn(buildVipChapter());
        lenient().when(bookInfoMapper.selectById(bookId)).thenReturn(buildBookInfo());
        lenient().when(userConsumeLogMapper.selectCount(any())).thenReturn(0L);
        lenient().when(userInfoMapper.deductBalance(eq(userId), eq(chapterPrice))).thenReturn(1);
        lenient().when(userConsumeLogMapper.insert(any())).thenReturn(1);
        lenient().when(authorIncomeDetailMapper.countUserPurchasesToday(anyLong(), anyLong(), anyLong(), any())).thenReturn(0);
        lenient().when(bookContentCacheManager.getBookContent(chapterId)).thenReturn("VIP章节完整内容");
    }

    @Test
    void testConcurrentPurchase_onlyOneSucceeds() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // 模拟锁行为：第一个线程获取锁成功，其余失败
        AtomicInteger lockAcquired = new AtomicInteger(0);
        when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenAnswer(inv -> {
            return lockAcquired.incrementAndGet() == 1;
        });

        // 模拟余额检查：第一次返回足够余额，后续返回不足
        AtomicInteger balanceChecks = new AtomicInteger(0);
        when(userInfoMapper.selectById(userId)).thenAnswer(inv -> {
            UserInfo user = new UserInfo();
            // 仅第一个进入锁的线程有足够余额
            user.setAccountBalance(balanceChecks.incrementAndGet() == 1 ? 100L : 5L);
            return user;
        });

        // 模拟幂等检查：第一次未购买，锁内第二次检查也未购买（仅对获锁线程）
        when(userConsumeLogMapper.selectCount(any())).thenReturn(0L);

        // 配置 FinanceProperties
        FinanceProperties props = new FinanceProperties();
        props.setDefaultChapterPrice(chapterPrice);
        // 使用反射注入
        try {
            var field = ChapterPurchaseServiceImpl.class.getDeclaredField("financeProperties");
            field.setAccessible(true);
            field.set(chapterPurchaseService, props);
        } catch (Exception e) {
            fail("Failed to inject financeProperties: " + e.getMessage());
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    RestResp<BookContentAboutRespDto> result = chapterPurchaseService.purchaseChapter(userId, chapterId);
                    if (result.isOk()) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (BusinessException e) {
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 同时释放所有线程
        doneLatch.await();
        executor.shutdown();

        // 验证：仅1次成功
        assertEquals(1, successCount.get(), "仅应有1次购买成功");
        assertEquals(9, failCount.get(), "应有9次购买失败");

        // 验证：余额仅扣一次
        verify(userInfoMapper, times(1)).deductBalance(eq(userId), eq(chapterPrice));

        // 验证：消费记录仅创建一次
        verify(userConsumeLogMapper, times(1)).insert(any(UserConsumeLog.class));
    }

    private BookChapter buildVipChapter() {
        BookChapter ch = new BookChapter();
        ch.setId(chapterId);
        ch.setBookId(bookId);
        ch.setIsVip(1);
        ch.setIsFreeLimit(0);
        ch.setChapterPrice(chapterPrice);
        ch.setChapterName("VIP测试章节");
        return ch;
    }

    private BookInfo buildBookInfo() {
        BookInfo info = new BookInfo();
        info.setId(bookId);
        info.setAuthorId(authorId);
        info.setIsFreeLimit(0);
        info.setBookName("测试小说");
        return info;
    }
}
