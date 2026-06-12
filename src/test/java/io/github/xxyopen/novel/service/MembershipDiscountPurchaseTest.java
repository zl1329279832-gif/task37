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
 * 会员折扣购买测试
 * 场景：会员8折购买、会员免费读、配额耗尽回退、过期全价、待结算创建
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

    @BeforeEach
    void setUp() throws Exception {
        FinanceProperties props = new FinanceProperties();
        props.setDefaultChapterPrice(10);
        props.setTaxRate(20);
        props.setRefundFreezeDays(7);
        props.setMemberSubsidyRate(10);
        var field = ChapterPurchaseServiceImpl.class.getDeclaredField("financeProperties");
        field.setAccessible(true);
        field.set(chapterPurchaseService, props);

        lenient().when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        lenient().when(userConsumeLogMapper.selectCount(any())).thenReturn(0L);
        lenient().when(userConsumeLogMapper.insert(any())).thenReturn(1);
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
        lenient().when(memberBenefitsSnapshotMapper.insert(any())).thenReturn(1);
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

    private MemberInfo buildMember(int discountRate, int usedFreeRead, int freeReadQuota) {
        MemberInfo m = new MemberInfo();
        m.setId(1L);
        m.setUserId(userId);
        m.setMemberLevel(2);
        m.setExpireTime(LocalDateTime.now().plusDays(30));
        m.setFreeReadQuota(freeReadQuota);
        m.setUsedFreeRead(usedFreeRead);
        m.setDiscountRate(discountRate);
        m.setStatus(0);
        return m;
    }

    @Test
    void testMemberDiscount_80percent() {
        BookChapter ch = buildVipChapter();
        when(bookChapterMapper.selectById(chapterId)).thenReturn(ch);
        when(bookInfoMapper.selectById(bookId)).thenReturn(buildBookInfo());

        // 会员8折，配额已用完
        MemberInfo member = buildMember(80, 30, 30);
        when(memberInfoCacheManager.getActiveMemberInfo(userId)).thenReturn(member);
        when(memberInfoMapper.selectActiveByUserId(userId)).thenReturn(member);

        // 余额检查：折扣价 = 10 * 80/100 = 8
        UserInfo userInfo = new UserInfo();
        userInfo.setAccountBalance(100L);
        when(userInfoMapper.selectById(userId)).thenReturn(userInfo);
        when(userInfoMapper.deductBalance(userId, 8)).thenReturn(1);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.purchaseChapterWithCoupon(userId, chapterId, null);

        assertTrue(result.isOk());
        // 验证按折扣价扣款
        verify(userInfoMapper).deductBalance(userId, 8);
    }

    @Test
    void testMemberFreeRead_noCharge() {
        BookChapter ch = buildVipChapter();
        when(bookChapterMapper.selectById(chapterId)).thenReturn(ch);
        when(bookInfoMapper.selectById(bookId)).thenReturn(buildBookInfo());

        // 会员有免费配额
        MemberInfo member = buildMember(80, 0, 30);
        when(memberInfoCacheManager.getActiveMemberInfo(userId)).thenReturn(member);
        when(memberInfoMapper.selectActiveByUserId(userId)).thenReturn(member);
        when(memberInfoMapper.deductFreeReadQuota(userId)).thenReturn(1);
        when(memberBenefitsSnapshotMapper.insert(any())).thenReturn(1);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.purchaseChapterWithCoupon(userId, chapterId, null);

        assertTrue(result.isOk());
        assertNotNull(result.getData().getIsMemberFreeRead());
        assertTrue(result.getData().getIsMemberFreeRead());
        // 验证未扣余额
        verify(userInfoMapper, never()).deductBalance(anyLong(), anyInt());
        // 验证创建了待结算（会员免费读类型）
        verify(settlementService).createPendingSettlement(
                eq(userId), eq(authorId), eq(bookId), eq(chapterId),
                any(), eq(1), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void testMemberFreeRead_quotaExhausted_fallback() {
        BookChapter ch = buildVipChapter();
        when(bookChapterMapper.selectById(chapterId)).thenReturn(ch);
        when(bookInfoMapper.selectById(bookId)).thenReturn(buildBookInfo());

        // 会员有配额但锁内CAS失败
        MemberInfo member = buildMember(80, 0, 30);
        when(memberInfoCacheManager.getActiveMemberInfo(userId)).thenReturn(member);
        when(memberInfoMapper.selectActiveByUserId(userId)).thenReturn(member);
        // 锁内CAS扣减配额失败（配额耗尽）
        when(memberInfoMapper.deductFreeReadQuota(userId)).thenReturn(0);

        // 回退到折扣购买
        UserInfo userInfo = new UserInfo();
        userInfo.setAccountBalance(100L);
        when(userInfoMapper.selectById(userId)).thenReturn(userInfo);
        when(userInfoMapper.deductBalance(userId, 8)).thenReturn(1);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.purchaseChapterWithCoupon(userId, chapterId, null);

        assertTrue(result.isOk());
        // 验证回退到折扣购买
        verify(userInfoMapper).deductBalance(userId, 8);
    }

    @Test
    void testMemberExpired_fullPrice() {
        BookChapter ch = buildVipChapter();
        when(bookChapterMapper.selectById(chapterId)).thenReturn(ch);
        when(bookInfoMapper.selectById(bookId)).thenReturn(buildBookInfo());

        // 非会员（会员过期）
        when(memberInfoCacheManager.getActiveMemberInfo(userId)).thenReturn(null);
        when(memberInfoMapper.selectActiveByUserId(userId)).thenReturn(null);

        UserInfo userInfo = new UserInfo();
        userInfo.setAccountBalance(100L);
        when(userInfoMapper.selectById(userId)).thenReturn(userInfo);
        when(userInfoMapper.deductBalance(userId, 10)).thenReturn(1);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.purchaseChapterWithCoupon(userId, chapterId, null);

        assertTrue(result.isOk());
        // 验证按原价扣款
        verify(userInfoMapper).deductBalance(userId, 10);
    }

    @Test
    void testPendingSettlement_created() {
        BookChapter ch = buildVipChapter();
        when(bookChapterMapper.selectById(chapterId)).thenReturn(ch);
        when(bookInfoMapper.selectById(bookId)).thenReturn(buildBookInfo());
        when(memberInfoCacheManager.getActiveMemberInfo(userId)).thenReturn(null);
        when(memberInfoMapper.selectActiveByUserId(userId)).thenReturn(null);

        UserInfo userInfo = new UserInfo();
        userInfo.setAccountBalance(100L);
        when(userInfoMapper.selectById(userId)).thenReturn(userInfo);
        when(userInfoMapper.deductBalance(userId, 10)).thenReturn(1);

        chapterPurchaseService.purchaseChapterWithCoupon(userId, chapterId, null);

        // 验证创建了待结算记录
        verify(settlementService).createPendingSettlement(
                eq(userId), eq(authorId), eq(bookId), eq(chapterId),
                any(), eq(0), eq(10), eq(0), eq(0), eq(10));
    }

    @Test
    void testFreeRead_type1() {
        BookChapter ch = buildVipChapter();
        when(bookChapterMapper.selectById(chapterId)).thenReturn(ch);
        when(bookInfoMapper.selectById(bookId)).thenReturn(buildBookInfo());

        MemberInfo member = buildMember(80, 0, 30);
        when(memberInfoCacheManager.getActiveMemberInfo(userId)).thenReturn(member);
        when(memberInfoMapper.selectActiveByUserId(userId)).thenReturn(member);
        when(memberInfoMapper.deductFreeReadQuota(userId)).thenReturn(1);
        when(memberBenefitsSnapshotMapper.insert(any())).thenReturn(1);

        chapterPurchaseService.purchaseChapterWithCoupon(userId, chapterId, null);

        // 验证待结算类型为1（会员免费读）
        verify(settlementService).createPendingSettlement(
                eq(userId), eq(authorId), eq(bookId), eq(chapterId),
                any(), eq(1), anyInt(), anyInt(), anyInt(), anyInt());
    }

}
