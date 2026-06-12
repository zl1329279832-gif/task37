package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.core.common.constant.ErrorCodeEnum;
import io.github.xxyopen.novel.core.common.exception.BusinessException;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.config.FinanceProperties;
import io.github.xxyopen.novel.dao.entity.MembershipBenefitSnapshot;
import io.github.xxyopen.novel.dao.entity.ReadingVoucher;
import io.github.xxyopen.novel.dao.entity.UserMembership;
import io.github.xxyopen.novel.dao.mapper.MembershipBenefitSnapshotMapper;
import io.github.xxyopen.novel.dao.mapper.ReadingVoucherMapper;
import io.github.xxyopen.novel.dao.mapper.UserMembershipMapper;
import io.github.xxyopen.novel.dto.resp.ReadingVoucherRespDto;
import io.github.xxyopen.novel.dto.resp.UserMembershipRespDto;
import io.github.xxyopen.novel.service.impl.MembershipServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 作者收入统计测试（含会员补贴场景）
 * 验证：会员免费/折扣/阅读券购买的收入明细正确拆分
 */
@ExtendWith(MockitoExtension.class)
class AuthorIncomeWithSubsidyTest {

    @InjectMocks
    private MembershipServiceImpl membershipService;

    @Mock private UserMembershipMapper userMembershipMapper;
    @Mock private MembershipBenefitSnapshotMapper snapshotMapper;
    @Mock private ReadingVoucherMapper readingVoucherMapper;

    private final Long userId = 1L;

    @BeforeEach
    void setUp() throws Exception {
        FinanceProperties props = new FinanceProperties();
        props.setBasicMemberFreeQuota(5);
        props.setPremiumMemberFreeQuota(20);
        props.setSupremeMemberFreeQuota(100);
        props.setBasicMemberDiscount(90);
        props.setPremiumMemberDiscount(80);
        props.setSupremeMemberDiscount(60);
        props.setBasicMemberVoucherCount(1);
        props.setPremiumMemberVoucherCount(3);
        props.setSupremeMemberVoucherCount(10);
        props.setVoucherValidDays(30);
        var field = MembershipServiceImpl.class.getDeclaredField("financeProperties");
        field.setAccessible(true);
        field.set(membershipService, props);
    }

    @Test
    void testPurchaseMembership_basic_createsSnapshotAndVouchers() {
        when(userMembershipMapper.selectOne(any())).thenReturn(null); // 无现有会员

        RestResp<UserMembershipRespDto> result = membershipService.purchaseMembership(userId, 1);

        assertTrue(result.isOk());
        UserMembershipRespDto dto = result.getData();
        assertEquals(1, dto.getMembershipLevel());
        assertEquals("基础会员", dto.getMembershipLevelName());
        assertEquals(5, dto.getFreeChapterQuota());
        assertEquals(0, dto.getFreeChapterUsed());
        assertEquals(90, dto.getDiscountRate());

        // 验证创建快照
        verify(snapshotMapper).insert(argThat(s ->
                s.getMembershipLevel() == 1
                        && s.getFreeChapterQuota() == 5
                        && s.getDiscountRate() == 90
                        && s.getVoucherCount() == 1));

        // 验证创建会员记录
        verify(userMembershipMapper).insert(argThat(m ->
                m.getMembershipLevel() == 1
                        && m.getFreeChapterQuota() == 5
                        && m.getFreeChapterUsed() == 0));

        // 验证发放1张阅读券
        verify(readingVoucherMapper, times(1)).insert(argThat(v ->
                v.getUserId().equals(userId) && v.getVoucherType() == 0));
    }

    @Test
    void testPurchaseMembership_premium_issuesMoreVouchers() {
        when(userMembershipMapper.selectOne(any())).thenReturn(null);

        RestResp<UserMembershipRespDto> result = membershipService.purchaseMembership(userId, 2);

        assertTrue(result.isOk());
        assertEquals(2, result.getData().getMembershipLevel());
        assertEquals(20, result.getData().getFreeChapterQuota());
        assertEquals(80, result.getData().getDiscountRate());

        // 高级会员发放3张阅读券
        verify(readingVoucherMapper, times(3)).insert(any(ReadingVoucher.class));
    }

    @Test
    void testPurchaseMembership_alreadyMember_throws() {
        UserMembership existing = new UserMembership();
        existing.setId(100L);
        existing.setStatus(0);
        when(userMembershipMapper.selectOne(any())).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> membershipService.purchaseMembership(userId, 1));

        assertEquals(ErrorCodeEnum.USER_ALREADY_MEMBER, ex.getErrorCodeEnum());
    }

    @Test
    void testCanFreeRead_withQuotaAvailable_returnsTrue() {
        UserMembership membership = new UserMembership();
        membership.setStatus(0);
        membership.setFreeChapterQuota(5);
        membership.setFreeChapterUsed(3);
        membership.setExpireTime(LocalDateTime.now().plusDays(10));

        assertTrue(membershipService.canFreeRead(membership));
    }

    @Test
    void testCanFreeRead_quotaExhausted_returnsFalse() {
        UserMembership membership = new UserMembership();
        membership.setStatus(0);
        membership.setFreeChapterQuota(5);
        membership.setFreeChapterUsed(5); // 配额用完
        membership.setExpireTime(LocalDateTime.now().plusDays(10));

        assertFalse(membershipService.canFreeRead(membership));
    }

    @Test
    void testCanFreeRead_expired_returnsFalse() {
        UserMembership membership = new UserMembership();
        membership.setStatus(0);
        membership.setFreeChapterQuota(5);
        membership.setFreeChapterUsed(0);
        membership.setExpireTime(LocalDateTime.now().minusDays(1)); // 已过期

        assertFalse(membershipService.canFreeRead(membership));
    }

    @Test
    void testCalculateDiscountPrice_premiumMember_80percent() {
        UserMembership membership = new UserMembership();
        membership.setStatus(0);
        membership.setDiscountRate(80);

        int discountPrice = membershipService.calculateDiscountPrice(10, membership);

        assertEquals(8, discountPrice); // 10 * 80 / 100 = 8
    }

    @Test
    void testCalculateDiscountPrice_supremeMember_60percent() {
        UserMembership membership = new UserMembership();
        membership.setStatus(0);
        membership.setDiscountRate(60);

        int discountPrice = membershipService.calculateDiscountPrice(10, membership);

        assertEquals(6, discountPrice); // 10 * 60 / 100 = 6
    }

    @Test
    void testUseVoucher_valid_marksAsUsed() {
        ReadingVoucher voucher = new ReadingVoucher();
        voucher.setId(200L);
        voucher.setStatus(0);
        voucher.setVoucherType(0);
        voucher.setExpireTime(LocalDateTime.now().plusDays(10));
        when(readingVoucherMapper.selectById(200L)).thenReturn(voucher);
        when(readingVoucherMapper.casUseVoucher(200L, 100L)).thenReturn(1);

        ReadingVoucher result = membershipService.useVoucher(200L, 100L);

        assertNotNull(result);
        assertEquals(1, result.getStatus());
        verify(readingVoucherMapper).casUseVoucher(200L, 100L);
    }

    @Test
    void testUseVoucher_alreadyUsed_throws() {
        ReadingVoucher voucher = new ReadingVoucher();
        voucher.setId(200L);
        voucher.setStatus(1); // 已使用
        when(readingVoucherMapper.selectById(200L)).thenReturn(voucher);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> membershipService.useVoucher(200L, 100L));

        assertEquals(ErrorCodeEnum.USER_VOUCHER_INVALID, ex.getErrorCodeEnum());
    }

    @Test
    void testUseVoucher_expired_throws() {
        ReadingVoucher voucher = new ReadingVoucher();
        voucher.setId(200L);
        voucher.setStatus(0);
        voucher.setExpireTime(LocalDateTime.now().minusDays(1)); // 已过期
        when(readingVoucherMapper.selectById(200L)).thenReturn(voucher);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> membershipService.useVoucher(200L, 100L));

        assertEquals(ErrorCodeEnum.USER_VOUCHER_EXPIRED, ex.getErrorCodeEnum());
    }

    @Test
    void testConsumeFreeQuota_success() {
        when(userMembershipMapper.incrementFreeChapterUsed(100L)).thenReturn(1);

        boolean result = membershipService.consumeFreeQuota(100L);

        assertTrue(result);
        verify(userMembershipMapper).incrementFreeChapterUsed(100L);
    }

    @Test
    void testConsumeFreeQuota_exhausted_returnsFalse() {
        when(userMembershipMapper.incrementFreeChapterUsed(100L)).thenReturn(0);

        boolean result = membershipService.consumeFreeQuota(100L);

        assertFalse(result);
    }

    @Test
    void testProcessExpirations_marksExpiredMembershipsAndVouchers() {
        when(userMembershipMapper.expireOverdueMemberships()).thenReturn(3);
        when(readingVoucherMapper.expireOverdueVouchers()).thenReturn(5);

        membershipService.processExpirations();

        verify(userMembershipMapper).expireOverdueMemberships();
        verify(readingVoucherMapper).expireOverdueVouchers();
    }

    @Test
    void testResetMonthlyQuotas() {
        when(userMembershipMapper.resetMonthlyFreeChapterUsed()).thenReturn(10);

        membershipService.resetMonthlyQuotas();

        verify(userMembershipMapper).resetMonthlyFreeChapterUsed();
    }
}
