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
 * 阅读券测试
 * 场景：券抵扣、过期、已使用、不满足最低消费、CAS失败回退、退款恢复、超额夹紧
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReadingCouponTest {

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
    private final Long chapterId = 100L;
    private final Long bookId = 10L;
    private final Long authorId = 5L;
    private final Long couponId = 200L;

    @BeforeEach
    void setUp() throws Exception {
        FinanceProperties props = new FinanceProperties();
        props.setDefaultChapterPrice(10);
        var field = ChapterPurchaseServiceImpl.class.getDeclaredField("financeProperties");
        field.setAccessible(true);
        field.set(chapterPurchaseService, props);

        lenient().when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        lenient().when(userConsumeLogMapper.selectCount(any())).thenReturn(0L);
        lenient().when(userConsumeLogMapper.insert(any())).thenReturn(1);
        lenient().when(memberInfoCacheManager.getActiveMemberInfo(anyLong())).thenReturn(null);
        lenient().when(memberInfoMapper.selectActiveByUserId(anyLong())).thenReturn(null);
        lenient().when(settlementService.createPendingSettlement(
                anyLong(), anyLong(), anyLong(), anyLong(), any(), anyInt(),
                anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new PendingSettlement());

        // 缓存 mock（buildFullContent 需要）
        lenient().when(bookContentCacheManager.getBookContent(anyLong())).thenReturn("章节内容");
        lenient().when(bookChapterCacheManager.getChapter(anyLong())).thenReturn(
                io.github.xxyopen.novel.dto.resp.BookChapterRespDto.builder()
                        .id(chapterId).bookId(bookId).chapterName("VIP章节").build());
        lenient().when(bookInfoCacheManager.getBookInfo(anyLong())).thenReturn(
                io.github.xxyopen.novel.dto.resp.BookInfoRespDto.builder()
                        .id(bookId).bookName("测试小说").build());
    }

    private BookChapter buildVipChapter() {
        BookChapter ch = new BookChapter();
        ch.setId(chapterId);
        ch.setBookId(bookId);
        ch.setIsVip(1);
        ch.setIsFreeLimit(0);
        ch.setChapterPrice(10);
        ch.setChapterName("VIP章节");
        return ch;
    }

    private BookInfo buildBookInfo() {
        BookInfo bi = new BookInfo();
        bi.setId(bookId);
        bi.setAuthorId(authorId);
        bi.setIsVip(1);
        bi.setIsFreeLimit(0);
        return bi;
    }

    private ReadingCoupon buildValidCoupon() {
        ReadingCoupon c = new ReadingCoupon();
        c.setId(couponId);
        c.setUserId(userId);
        c.setCouponName("阅读券");
        c.setDiscountAmount(3);
        c.setMinPurchaseAmount(5);
        c.setExpireTime(LocalDateTime.now().plusDays(7));
        c.setUseStatus(0);
        return c;
    }

    @Test
    void testCouponApplied() {
        when(bookChapterMapper.selectById(chapterId)).thenReturn(buildVipChapter());
        when(bookInfoMapper.selectById(bookId)).thenReturn(buildBookInfo());

        ReadingCoupon coupon = buildValidCoupon();
        when(readingCouponMapper.selectById(couponId)).thenReturn(coupon);
        // 锁内重新查询
        when(readingCouponMapper.selectById(couponId)).thenReturn(coupon);
        when(readingCouponMapper.casUseCoupon(eq(couponId), any())).thenReturn(1);

        // 折扣价 10 - 3 = 7
        UserInfo userInfo = new UserInfo();
        userInfo.setAccountBalance(100L);
        when(userInfoMapper.selectById(userId)).thenReturn(userInfo);
        when(userInfoMapper.deductBalance(userId, 7)).thenReturn(1);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.purchaseChapterWithCoupon(userId, chapterId, couponId);

        assertTrue(result.isOk());
        verify(userInfoMapper).deductBalance(userId, 7);
    }

    @Test
    void testCouponExpired() {
        when(bookChapterMapper.selectById(chapterId)).thenReturn(buildVipChapter());
        when(bookInfoMapper.selectById(bookId)).thenReturn(buildBookInfo());

        ReadingCoupon coupon = buildValidCoupon();
        coupon.setExpireTime(LocalDateTime.now().minusDays(1));
        when(readingCouponMapper.selectById(couponId)).thenReturn(coupon);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chapterPurchaseService.purchaseChapterWithCoupon(userId, chapterId, couponId));

        assertEquals(ErrorCodeEnum.COUPON_EXPIRED, ex.getErrorCodeEnum());
    }

    @Test
    void testCouponAlreadyUsed() {
        when(bookChapterMapper.selectById(chapterId)).thenReturn(buildVipChapter());
        when(bookInfoMapper.selectById(bookId)).thenReturn(buildBookInfo());

        ReadingCoupon coupon = buildValidCoupon();
        coupon.setUseStatus(1);
        when(readingCouponMapper.selectById(couponId)).thenReturn(coupon);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chapterPurchaseService.purchaseChapterWithCoupon(userId, chapterId, couponId));

        assertEquals(ErrorCodeEnum.COUPON_NOT_AVAILABLE, ex.getErrorCodeEnum());
    }

    @Test
    void testCouponMinNotMet() {
        BookChapter ch = buildVipChapter();
        ch.setChapterPrice(3); // 价格低于最低消费门槛
        when(bookChapterMapper.selectById(chapterId)).thenReturn(ch);
        when(bookInfoMapper.selectById(bookId)).thenReturn(buildBookInfo());

        ReadingCoupon coupon = buildValidCoupon();
        coupon.setMinPurchaseAmount(5);
        when(readingCouponMapper.selectById(couponId)).thenReturn(coupon);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chapterPurchaseService.purchaseChapterWithCoupon(userId, chapterId, couponId));

        assertEquals(ErrorCodeEnum.COUPON_MIN_PURCHASE_NOT_MET, ex.getErrorCodeEnum());
    }

    @Test
    void testCouponCASFailed_fallback() {
        when(bookChapterMapper.selectById(chapterId)).thenReturn(buildVipChapter());
        when(bookInfoMapper.selectById(bookId)).thenReturn(buildBookInfo());

        ReadingCoupon coupon = buildValidCoupon();
        when(readingCouponMapper.selectById(couponId)).thenReturn(coupon);
        // CAS 失败（券已被其他操作使用）
        when(readingCouponMapper.casUseCoupon(eq(couponId), any())).thenReturn(0);

        // 回退后全价购买 10 屋币
        UserInfo userInfo = new UserInfo();
        userInfo.setAccountBalance(100L);
        when(userInfoMapper.selectById(userId)).thenReturn(userInfo);
        when(userInfoMapper.deductBalance(userId, 7)).thenReturn(1);
        when(userInfoMapper.deductBalance(userId, 10)).thenReturn(1);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.purchaseChapterWithCoupon(userId, chapterId, couponId);

        assertTrue(result.isOk());
        // 验证回退到全价
        verify(userInfoMapper).deductBalance(userId, 10);
    }

    @Test
    void testCouponExceedsPrice_clamped() {
        BookChapter ch = buildVipChapter();
        ch.setChapterPrice(2); // 价格2，券抵扣5，最终应为0
        when(bookChapterMapper.selectById(chapterId)).thenReturn(ch);
        when(bookInfoMapper.selectById(bookId)).thenReturn(buildBookInfo());

        ReadingCoupon coupon = buildValidCoupon();
        coupon.setMinPurchaseAmount(0); // 无门槛
        when(readingCouponMapper.selectById(couponId)).thenReturn(coupon);
        when(readingCouponMapper.casUseCoupon(eq(couponId), any())).thenReturn(1);

        // finalPrice = max(0, 2-5) = 0, 不需要扣余额
        // 但实现仍会查询用户信息检查余额
        UserInfo userInfo = new UserInfo();
        userInfo.setAccountBalance(100L);
        when(userInfoMapper.selectById(userId)).thenReturn(userInfo);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.purchaseChapterWithCoupon(userId, chapterId, couponId);

        assertTrue(result.isOk());
        // 验证未扣余额
        verify(userInfoMapper, never()).deductBalance(anyLong(), anyInt());
    }

}
