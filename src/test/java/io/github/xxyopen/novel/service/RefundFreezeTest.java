package io.github.xxyopen.novel.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.xxyopen.novel.core.common.constant.ErrorCodeEnum;
import io.github.xxyopen.novel.core.common.exception.BusinessException;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.config.FinanceProperties;
import io.github.xxyopen.novel.dao.entity.*;
import io.github.xxyopen.novel.dao.mapper.*;
import io.github.xxyopen.novel.manager.cache.BookChapterCacheManager;
import io.github.xxyopen.novel.manager.cache.BookContentCacheManager;
import io.github.xxyopen.novel.manager.cache.BookInfoCacheManager;
import io.github.xxyopen.novel.manager.cache.MemberInfoCacheManager;
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

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 退款冻结测试
 * 场景：冻结中拒绝退款、冻结到期可退款、旧记录兼容、取消待结算、恢复阅读券
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundFreezeTest {

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
    @Mock private MemberInfoCacheManager memberInfoCacheManager;
    @Mock private MemberInfoMapper memberInfoMapper;
    @Mock private ReadingCouponMapper readingCouponMapper;
    @Mock private MemberBenefitsSnapshotMapper memberBenefitsSnapshotMapper;
    @Mock private SettlementService settlementService;
    @Mock private RefundFreezeMapper refundFreezeMapper;

    private final Long userId = 1L;
    private final Long consumeLogId = 999L;
    private final Long chapterId = 100L;
    private final Long bookId = 10L;
    private final Long authorId = 5L;
    private final int purchaseAmount = 10;

    @BeforeEach
    void setUp() throws Exception {
        FinanceProperties props = new FinanceProperties();
        props.setDefaultChapterPrice(10);
        props.setTaxRate(20);
        var field = ChapterPurchaseServiceImpl.class.getDeclaredField("financeProperties");
        field.setAccessible(true);
        field.set(chapterPurchaseService, props);
    }

    private UserConsumeLog buildConsumeLog() {
        UserConsumeLog log = new UserConsumeLog();
        log.setId(consumeLogId);
        log.setUserId(userId);
        log.setAuthorId(authorId);
        log.setAmount(purchaseAmount);
        log.setProductType(0);
        log.setProductId(chapterId);
        log.setProducName("VIP测试章节");
        log.setProducValue(1);
        log.setRefundStatus(0);
        log.setCreateTime(LocalDateTime.now());
        log.setUpdateTime(LocalDateTime.now());
        return log;
    }

    @Test
    void testRefund_withinFreeze_throwsFrozen() {
        UserConsumeLog log = buildConsumeLog();
        when(userConsumeLogMapper.selectById(consumeLogId)).thenReturn(log);

        // 冻结记录存在且未到期
        RefundFreeze freeze = new RefundFreeze();
        freeze.setConsumeLogId(consumeLogId);
        freeze.setStatus(0);
        freeze.setFreezeEndTime(LocalDateTime.now().plusDays(5));
        when(refundFreezeMapper.selectByConsumeLogId(consumeLogId)).thenReturn(freeze);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chapterPurchaseService.refund(userId, consumeLogId));

        assertEquals(ErrorCodeEnum.REFUND_FROZEN, ex.getErrorCodeEnum());
        verify(userConsumeLogMapper, never()).casSetRefunded(anyLong());
    }

    @Test
    void testRefund_afterFreeze_succeeds() {
        UserConsumeLog log = buildConsumeLog();
        when(userConsumeLogMapper.selectById(consumeLogId)).thenReturn(log);

        // 冻结已过期
        RefundFreeze freeze = new RefundFreeze();
        freeze.setConsumeLogId(consumeLogId);
        freeze.setStatus(0);
        freeze.setFreezeEndTime(LocalDateTime.now().minusDays(1));
        when(refundFreezeMapper.selectByConsumeLogId(consumeLogId)).thenReturn(freeze);

        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);
        when(authorIncomeMapper.getConfirmStatus(anyLong(), anyLong(), any())).thenReturn(null);
        when(userConsumeLogMapper.casSetRefunded(consumeLogId)).thenReturn(1);
        when(userInfoMapper.restoreBalance(userId, purchaseAmount)).thenReturn(1);
        when(authorIncomeDetailMapper.countUserPurchasesToday(anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(1);
        when(authorIncomeDetailMapper.deductDailyIncome(anyLong(), anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(1);
        when(authorIncomeMapper.deductSettlement(anyLong(), anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(0);
        when(readingCouponMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        RestResp<Void> result = chapterPurchaseService.refund(userId, consumeLogId);

        assertTrue(result.isOk());
        verify(userConsumeLogMapper).casSetRefunded(consumeLogId);
    }

    @Test
    void testRefund_noFreezeRecord_legacy() {
        UserConsumeLog log = buildConsumeLog();
        when(userConsumeLogMapper.selectById(consumeLogId)).thenReturn(log);

        // 旧记录没有冻结信息
        when(refundFreezeMapper.selectByConsumeLogId(consumeLogId)).thenReturn(null);

        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);
        when(authorIncomeMapper.getConfirmStatus(anyLong(), anyLong(), any())).thenReturn(null);
        when(userConsumeLogMapper.casSetRefunded(consumeLogId)).thenReturn(1);
        when(userInfoMapper.restoreBalance(userId, purchaseAmount)).thenReturn(1);
        when(authorIncomeDetailMapper.countUserPurchasesToday(anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(1);
        when(authorIncomeDetailMapper.deductDailyIncome(anyLong(), anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(1);
        when(authorIncomeMapper.deductSettlement(anyLong(), anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(0);
        when(readingCouponMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        RestResp<Void> result = chapterPurchaseService.refund(userId, consumeLogId);

        assertTrue(result.isOk());
        // 验证旧记录正常退款
        verify(userInfoMapper).restoreBalance(userId, purchaseAmount);
    }

    @Test
    void testRefund_cancelsPendingSettlement() {
        UserConsumeLog log = buildConsumeLog();
        when(userConsumeLogMapper.selectById(consumeLogId)).thenReturn(log);
        when(refundFreezeMapper.selectByConsumeLogId(consumeLogId)).thenReturn(null);

        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);
        when(authorIncomeMapper.getConfirmStatus(anyLong(), anyLong(), any())).thenReturn(null);
        when(userConsumeLogMapper.casSetRefunded(consumeLogId)).thenReturn(1);
        when(userInfoMapper.restoreBalance(userId, purchaseAmount)).thenReturn(1);
        when(authorIncomeDetailMapper.countUserPurchasesToday(anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(1);
        when(authorIncomeDetailMapper.deductDailyIncome(anyLong(), anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(1);
        when(authorIncomeMapper.deductSettlement(anyLong(), anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(0);
        when(readingCouponMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        chapterPurchaseService.refund(userId, consumeLogId);

        // 验证取消了待结算记录
        verify(settlementService).cancelPendingSettlement(consumeLogId);
    }

    @Test
    void testRefund_restoresCoupon() {
        UserConsumeLog log = buildConsumeLog();
        when(userConsumeLogMapper.selectById(consumeLogId)).thenReturn(log);
        when(refundFreezeMapper.selectByConsumeLogId(consumeLogId)).thenReturn(null);

        BookChapter chapter = new BookChapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);
        when(authorIncomeMapper.getConfirmStatus(anyLong(), anyLong(), any())).thenReturn(null);
        when(userConsumeLogMapper.casSetRefunded(consumeLogId)).thenReturn(1);
        when(userInfoMapper.restoreBalance(userId, purchaseAmount)).thenReturn(1);
        when(authorIncomeDetailMapper.countUserPurchasesToday(anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(1);
        when(authorIncomeDetailMapper.deductDailyIncome(anyLong(), anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(1);
        when(authorIncomeMapper.deductSettlement(anyLong(), anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(0);

        // 模拟使用了阅读券
        ReadingCoupon usedCoupon = new ReadingCoupon();
        usedCoupon.setId(200L);
        usedCoupon.setUseStatus(1);
        when(readingCouponMapper.selectOne(any(QueryWrapper.class))).thenReturn(usedCoupon);
        when(readingCouponMapper.restoreCoupon(200L)).thenReturn(1);

        chapterPurchaseService.refund(userId, consumeLogId);

        // 验证阅读券被恢复
        verify(readingCouponMapper).restoreCoupon(200L);
    }

}
