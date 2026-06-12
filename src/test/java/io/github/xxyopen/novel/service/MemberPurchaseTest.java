package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.core.common.constant.ErrorCodeEnum;
import io.github.xxyopen.novel.core.common.exception.BusinessException;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.config.MemberProperties;
import io.github.xxyopen.novel.dao.entity.MemberInfo;
import io.github.xxyopen.novel.dao.entity.ReadingCoupon;
import io.github.xxyopen.novel.dao.entity.UserInfo;
import io.github.xxyopen.novel.dao.mapper.MemberInfoMapper;
import io.github.xxyopen.novel.dao.mapper.ReadingCouponMapper;
import io.github.xxyopen.novel.dao.mapper.UserInfoMapper;
import io.github.xxyopen.novel.dao.mapper.UserPayLogMapper;
import io.github.xxyopen.novel.manager.cache.MemberInfoCacheManager;
import io.github.xxyopen.novel.manager.redis.RedisDistributedLockManager;
import io.github.xxyopen.novel.service.impl.MemberServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * 会员购买测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberPurchaseTest {

    @InjectMocks
    private MemberServiceImpl memberService;

    @Mock private MemberInfoMapper memberInfoMapper;
    @Mock private ReadingCouponMapper readingCouponMapper;
    @Mock private UserInfoMapper userInfoMapper;
    @Mock private UserPayLogMapper userPayLogMapper;
    @Mock private MemberInfoCacheManager memberInfoCacheManager;
    @Mock private RedisDistributedLockManager lockManager;

    private final Long userId = 1L;

    @BeforeEach
    void setUp() throws Exception {
        MemberProperties props = new MemberProperties();
        props.setMonthlyPrice(300);
        props.setQuarterlyPrice(800);
        props.setYearlyPrice(2800);
        props.setMonthlyFreeReadQuota(30);
        props.setMonthlyDiscountRate(90);
        props.setQuarterlyDiscountRate(80);
        props.setYearlyDiscountRate(70);
        props.setMonthlyCouponCount(3);
        props.setCouponDiscountAmount(5);
        props.setCouponMinPurchaseAmount(10);
        props.setCouponExpireDays(30);

        var field = MemberServiceImpl.class.getDeclaredField("memberProperties");
        field.setAccessible(true);
        field.set(memberService, props);

        // 默认锁获取成功
        when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        when(userPayLogMapper.insert(any())).thenReturn(1);
        when(readingCouponMapper.insert(any())).thenReturn(1);
    }

    @Test
    void testPurchaseMonthly_success() {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(userId);
        userInfo.setAccountBalance(1000L);
        when(userInfoMapper.selectById(userId)).thenReturn(userInfo);
        when(userInfoMapper.deductBalance(userId, 300)).thenReturn(1);
        when(memberInfoMapper.selectActiveByUserId(userId)).thenReturn(null);
        when(memberInfoMapper.insert(any())).thenReturn(1);

        RestResp<Void> result = memberService.purchaseMembership(userId, 1);

        assertTrue(result.isOk());
        verify(userInfoMapper).deductBalance(userId, 300);
        verify(memberInfoMapper).insert(any(MemberInfo.class));
        verify(readingCouponMapper, times(3)).insert(any(ReadingCoupon.class));
        verify(memberInfoCacheManager).evictMemberInfo(userId);
    }

    @Test
    void testPurchase_insufficientBalance() {
        when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);

        UserInfo userInfo = new UserInfo();
        userInfo.setId(userId);
        userInfo.setAccountBalance(100L);
        when(userInfoMapper.selectById(userId)).thenReturn(userInfo);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> memberService.purchaseMembership(userId, 1));

        assertEquals(ErrorCodeEnum.USER_BALANCE_INSUFFICIENT, ex.getErrorCodeEnum());
        verify(memberInfoMapper, never()).insert(any());
    }

    @Test
    void testRenewal_extendsExpireTime() {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(userId);
        userInfo.setAccountBalance(1000L);
        when(userInfoMapper.selectById(userId)).thenReturn(userInfo);
        when(userInfoMapper.deductBalance(userId, 300)).thenReturn(1);

        // 使用固定过期时间以便比较
        LocalDateTime originalExpire = LocalDateTime.of(2026, 7, 1, 0, 0);
        MemberInfo existing = new MemberInfo();
        existing.setId(1L);
        existing.setUserId(userId);
        existing.setMemberLevel(1);
        existing.setExpireTime(originalExpire);
        existing.setFreeReadQuota(30);
        existing.setUsedFreeRead(5);
        existing.setDiscountRate(90);
        existing.setStatus(0);

        // 返回不同的实例以避免引用问题
        when(memberInfoMapper.selectActiveByUserId(userId)).thenReturn(existing);
        when(memberInfoMapper.updateById(any())).thenAnswer(inv -> {
            MemberInfo m = inv.getArgument(0);
            // 验证过期时间被延长
            assertTrue(m.getExpireTime().isAfter(originalExpire));
            return 1;
        });

        RestResp<Void> result = memberService.purchaseMembership(userId, 1);
        assertTrue(result.isOk());
        verify(memberInfoMapper).updateById(any(MemberInfo.class));
    }

    @Test
    void testPurchaseYearly_correctDiscountRate() {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(userId);
        userInfo.setAccountBalance(5000L);
        when(userInfoMapper.selectById(userId)).thenReturn(userInfo);
        when(userInfoMapper.deductBalance(userId, 2800)).thenReturn(1);
        when(memberInfoMapper.selectActiveByUserId(userId)).thenReturn(null);
        when(memberInfoMapper.insert(any())).thenReturn(1);

        RestResp<Void> result = memberService.purchaseMembership(userId, 3);
        assertTrue(result.isOk());

        ArgumentCaptor<MemberInfo> captor = ArgumentCaptor.forClass(MemberInfo.class);
        verify(memberInfoMapper).insert(captor.capture());
        assertEquals(70, captor.getValue().getDiscountRate());
    }

    @Test
    void testPurchase_lockFailed() {
        // 覆盖默认mock，让锁获取失败
        when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> memberService.purchaseMembership(userId, 1));

        assertEquals(ErrorCodeEnum.SYSTEM_PURCHASE_LOCK_FAILED, ex.getErrorCodeEnum());
    }

    @Test
    void testPurchase_invalidLevel() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> memberService.purchaseMembership(userId, 99));

        assertEquals(ErrorCodeEnum.MEMBER_LEVEL_NOT_EXIST, ex.getErrorCodeEnum());
    }

}
