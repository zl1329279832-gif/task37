package io.github.xxyopen.novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.xxyopen.novel.core.common.constant.ErrorCodeEnum;
import io.github.xxyopen.novel.core.common.exception.BusinessException;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.config.FinanceProperties;
import io.github.xxyopen.novel.core.constant.DatabaseConsts;
import io.github.xxyopen.novel.dao.entity.MembershipBenefitSnapshot;
import io.github.xxyopen.novel.dao.entity.ReadingVoucher;
import io.github.xxyopen.novel.dao.entity.UserMembership;
import io.github.xxyopen.novel.dao.mapper.MembershipBenefitSnapshotMapper;
import io.github.xxyopen.novel.dao.mapper.ReadingVoucherMapper;
import io.github.xxyopen.novel.dao.mapper.UserMembershipMapper;
import io.github.xxyopen.novel.dto.resp.ReadingVoucherRespDto;
import io.github.xxyopen.novel.dto.resp.UserMembershipRespDto;
import io.github.xxyopen.novel.service.MembershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 会员服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MembershipServiceImpl implements MembershipService {

    private final UserMembershipMapper userMembershipMapper;
    private final MembershipBenefitSnapshotMapper snapshotMapper;
    private final ReadingVoucherMapper readingVoucherMapper;
    private final FinanceProperties financeProperties;

    private static final String[] LEVEL_NAMES = {"", "基础会员", "高级会员", "至尊会员"};

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RestResp<UserMembershipRespDto> purchaseMembership(Long userId, Integer membershipLevel) {
        if (membershipLevel == null || membershipLevel < 1 || membershipLevel > 3) {
            throw new BusinessException(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
        }

        // 检查是否已有生效中的会员
        UserMembership existing = getActiveMembershipEntity(userId);
        if (existing != null) {
            throw new BusinessException(ErrorCodeEnum.USER_ALREADY_MEMBER);
        }

        // 获取权益配置
        int freeQuota = getFreeQuotaByLevel(membershipLevel);
        int discountRate = getDiscountByLevel(membershipLevel);
        int voucherCount = getVoucherCountByLevel(membershipLevel);

        // 1. 创建权益快照
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusMonths(1);

        MembershipBenefitSnapshot snapshot = new MembershipBenefitSnapshot();
        snapshot.setMembershipLevel(membershipLevel);
        snapshot.setFreeChapterQuota(freeQuota);
        snapshot.setDiscountRate(discountRate);
        snapshot.setVoucherCount(voucherCount);
        snapshot.setVoucherValidDays(financeProperties.getVoucherValidDays());
        snapshot.setEffectiveFrom(now);
        snapshot.setEffectiveTo(expireTime);
        snapshot.setCreateTime(now);
        snapshotMapper.insert(snapshot);

        // 2. 创建会员记录
        UserMembership membership = new UserMembership();
        membership.setUserId(userId);
        membership.setMembershipLevel(membershipLevel);
        membership.setStartTime(now);
        membership.setExpireTime(expireTime);
        membership.setFreeChapterQuota(freeQuota);
        membership.setFreeChapterUsed(0);
        membership.setDiscountRate(discountRate);
        membership.setStatus(0);
        membership.setSnapshotId(snapshot.getId());
        membership.setCreateTime(now);
        membership.setUpdateTime(now);
        userMembershipMapper.insert(membership);

        // 3. 发放阅读券
        if (voucherCount > 0) {
            issueVouchers(userId, membership.getId(), voucherCount,
                    financeProperties.getVoucherValidDays());
        }

        log.info("用户 {} 购买 {} 成功", userId, LEVEL_NAMES[membershipLevel]);

        return RestResp.ok(toMembershipRespDto(membership));
    }

    @Override
    public RestResp<UserMembershipRespDto> getActiveMembership(Long userId) {
        UserMembership membership = getActiveMembershipEntity(userId);
        if (membership == null) {
            return RestResp.ok(null);
        }
        return RestResp.ok(toMembershipRespDto(membership));
    }

    @Override
    public UserMembership getActiveMembershipEntity(Long userId) {
        QueryWrapper<UserMembership> qw = new QueryWrapper<>();
        qw.eq(DatabaseConsts.UserMembershipTable.COLUMN_USER_ID, userId)
                .eq(DatabaseConsts.UserMembershipTable.COLUMN_STATUS, 0)
                .gt(DatabaseConsts.UserMembershipTable.COLUMN_EXPIRE_TIME, LocalDateTime.now())
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        return userMembershipMapper.selectOne(qw);
    }

    @Override
    public boolean canFreeRead(UserMembership membership) {
        if (membership == null || !Objects.equals(membership.getStatus(), 0)) {
            return false;
        }
        if (membership.getExpireTime().isBefore(LocalDateTime.now())) {
            return false;
        }
        return membership.getFreeChapterUsed() < membership.getFreeChapterQuota();
    }

    @Override
    public boolean consumeFreeQuota(Long membershipId) {
        return userMembershipMapper.incrementFreeChapterUsed(membershipId) > 0;
    }

    @Override
    public void restoreFreeQuota(Long membershipId) {
        userMembershipMapper.decrementFreeChapterUsed(membershipId);
    }

    @Override
    public int calculateDiscountPrice(int originalPrice, UserMembership membership) {
        if (membership == null || !Objects.equals(membership.getStatus(), 0)) {
            return originalPrice;
        }
        return originalPrice * membership.getDiscountRate() / 100;
    }

    @Override
    public RestResp<List<ReadingVoucherRespDto>> listAvailableVouchers(Long userId) {
        QueryWrapper<ReadingVoucher> qw = new QueryWrapper<>();
        qw.eq(DatabaseConsts.ReadingVoucherTable.COLUMN_USER_ID, userId)
                .eq(DatabaseConsts.ReadingVoucherTable.COLUMN_STATUS, 0)
                .gt(DatabaseConsts.ReadingVoucherTable.COLUMN_EXPIRE_TIME, LocalDateTime.now())
                .orderByAsc(DatabaseConsts.ReadingVoucherTable.COLUMN_EXPIRE_TIME);
        List<ReadingVoucher> vouchers = readingVoucherMapper.selectList(qw);
        List<ReadingVoucherRespDto> respList = vouchers.stream()
                .map(this::toVoucherRespDto).toList();
        return RestResp.ok(respList);
    }

    @Override
    public ReadingVoucher useVoucher(Long voucherId, Long chapterId) {
        ReadingVoucher voucher = readingVoucherMapper.selectById(voucherId);
        if (voucher == null || !Objects.equals(voucher.getStatus(), 0)) {
            throw new BusinessException(ErrorCodeEnum.USER_VOUCHER_INVALID);
        }
        if (voucher.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCodeEnum.USER_VOUCHER_EXPIRED);
        }
        int affected = readingVoucherMapper.casUseVoucher(voucherId, chapterId);
        if (affected == 0) {
            throw new BusinessException(ErrorCodeEnum.USER_VOUCHER_INVALID);
        }
        voucher.setStatus(1);
        voucher.setUsedChapterId(chapterId);
        return voucher;
    }

    @Override
    public void restoreVoucher(Long voucherId) {
        readingVoucherMapper.restoreVoucher(voucherId);
    }

    @Override
    public int countAvailableVouchers(Long userId) {
        QueryWrapper<ReadingVoucher> qw = new QueryWrapper<>();
        qw.eq(DatabaseConsts.ReadingVoucherTable.COLUMN_USER_ID, userId)
                .eq(DatabaseConsts.ReadingVoucherTable.COLUMN_STATUS, 0)
                .gt(DatabaseConsts.ReadingVoucherTable.COLUMN_EXPIRE_TIME, LocalDateTime.now());
        return Math.toIntExact(readingVoucherMapper.selectCount(qw));
    }

    @Override
    public void processExpirations() {
        int expiredMemberships = userMembershipMapper.expireOverdueMemberships();
        int expiredVouchers = readingVoucherMapper.expireOverdueVouchers();
        if (expiredMemberships > 0 || expiredVouchers > 0) {
            log.info("过期处理: {}个会员过期, {}个阅读券过期", expiredMemberships, expiredVouchers);
        }
    }

    @Override
    public void resetMonthlyQuotas() {
        int affected = userMembershipMapper.resetMonthlyFreeChapterUsed();
        log.info("月度配额重置: {}个会员", affected);
    }

    // ======================== 私有方法 ========================

    private int getFreeQuotaByLevel(int level) {
        return switch (level) {
            case 1 -> financeProperties.getBasicMemberFreeQuota();
            case 2 -> financeProperties.getPremiumMemberFreeQuota();
            case 3 -> financeProperties.getSupremeMemberFreeQuota();
            default -> 0;
        };
    }

    private int getDiscountByLevel(int level) {
        return switch (level) {
            case 1 -> financeProperties.getBasicMemberDiscount();
            case 2 -> financeProperties.getPremiumMemberDiscount();
            case 3 -> financeProperties.getSupremeMemberDiscount();
            default -> 100;
        };
    }

    private int getVoucherCountByLevel(int level) {
        return switch (level) {
            case 1 -> financeProperties.getBasicMemberVoucherCount();
            case 2 -> financeProperties.getPremiumMemberVoucherCount();
            case 3 -> financeProperties.getSupremeMemberVoucherCount();
            default -> 0;
        };
    }

    private void issueVouchers(Long userId, Long membershipId, int count, int validDays) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusDays(validDays);
        List<ReadingVoucher> vouchers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ReadingVoucher voucher = new ReadingVoucher();
            voucher.setUserId(userId);
            voucher.setMembershipId(membershipId);
            voucher.setVoucherType(0); // 全免券
            voucher.setDiscountAmount(0); // 全免券使用时按章节价格抵扣
            voucher.setStatus(0);
            voucher.setExpireTime(expireTime);
            voucher.setCreateTime(now);
            voucher.setUpdateTime(now);
            readingVoucherMapper.insert(voucher);
            vouchers.add(voucher);
        }
        log.info("为用户 {} 发放 {} 张阅读券，有效期至 {}", userId, count, expireTime);
    }

    private UserMembershipRespDto toMembershipRespDto(UserMembership m) {
        return UserMembershipRespDto.builder()
                .id(m.getId())
                .membershipLevel(m.getMembershipLevel())
                .membershipLevelName(m.getMembershipLevel() <= 3 ? LEVEL_NAMES[m.getMembershipLevel()] : "未知")
                .startTime(m.getStartTime())
                .expireTime(m.getExpireTime())
                .freeChapterQuota(m.getFreeChapterQuota())
                .freeChapterUsed(m.getFreeChapterUsed())
                .discountRate(m.getDiscountRate())
                .status(m.getStatus())
                .build();
    }

    private ReadingVoucherRespDto toVoucherRespDto(ReadingVoucher v) {
        return ReadingVoucherRespDto.builder()
                .id(v.getId())
                .voucherType(v.getVoucherType())
                .discountAmount(v.getDiscountAmount())
                .status(v.getStatus())
                .expireTime(v.getExpireTime())
                .usedChapterId(v.getUsedChapterId())
                .usedTime(v.getUsedTime())
                .build();
    }

}
