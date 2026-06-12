package io.github.xxyopen.novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.xxyopen.novel.core.common.constant.ErrorCodeEnum;
import io.github.xxyopen.novel.core.common.exception.BusinessException;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.config.MemberProperties;
import io.github.xxyopen.novel.core.constant.DatabaseConsts;
import io.github.xxyopen.novel.dao.entity.MemberInfo;
import io.github.xxyopen.novel.dao.entity.ReadingCoupon;
import io.github.xxyopen.novel.dao.entity.UserInfo;
import io.github.xxyopen.novel.dao.entity.UserPayLog;
import io.github.xxyopen.novel.dao.mapper.MemberInfoMapper;
import io.github.xxyopen.novel.dao.mapper.ReadingCouponMapper;
import io.github.xxyopen.novel.dao.mapper.UserInfoMapper;
import io.github.xxyopen.novel.dao.mapper.UserPayLogMapper;
import io.github.xxyopen.novel.dto.resp.MemberInfoRespDto;
import io.github.xxyopen.novel.dto.resp.ReadingCouponRespDto;
import io.github.xxyopen.novel.manager.cache.MemberInfoCacheManager;
import io.github.xxyopen.novel.manager.redis.RedisDistributedLockManager;
import io.github.xxyopen.novel.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * 会员服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberServiceImpl implements MemberService {

    private final MemberInfoMapper memberInfoMapper;
    private final ReadingCouponMapper readingCouponMapper;
    private final UserInfoMapper userInfoMapper;
    private final UserPayLogMapper userPayLogMapper;
    private final MemberInfoCacheManager memberInfoCacheManager;
    private final RedisDistributedLockManager lockManager;
    private final MemberProperties memberProperties;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RestResp<Void> purchaseMembership(Long userId, Integer memberLevel) {
        // 1. 校验会员等级
        int price = getPriceForLevel(memberLevel);
        int discountRate = getDiscountRateForLevel(memberLevel);
        int freeReadQuota = memberProperties.getMonthlyFreeReadQuota();

        // 2. 分布式锁
        String lockKey = String.format("member:purchase:%d", userId);
        String lockValue = UUID.randomUUID().toString();
        try {
            if (!lockManager.tryLock(lockKey, lockValue, 10)) {
                throw new BusinessException(ErrorCodeEnum.SYSTEM_PURCHASE_LOCK_FAILED);
            }

            // 3. 检查余额
            UserInfo userInfo = userInfoMapper.selectById(userId);
            if (userInfo.getAccountBalance() == null || userInfo.getAccountBalance() < price) {
                throw new BusinessException(ErrorCodeEnum.USER_BALANCE_INSUFFICIENT);
            }

            // 4. 原子扣减余额
            int affected = userInfoMapper.deductBalance(userId, price);
            if (affected == 0) {
                throw new BusinessException(ErrorCodeEnum.USER_BALANCE_INSUFFICIENT);
            }

            // 5. 创建或续期会员
            LocalDateTime now = LocalDateTime.now();
            MemberInfo existingMember = memberInfoMapper.selectActiveByUserId(userId);

            if (existingMember != null) {
                // 续期：从当前过期时间向后延长
                long daysToAdd = getDaysForLevel(memberLevel);
                LocalDateTime newExpireTime = existingMember.getExpireTime().plusDays(daysToAdd);
                existingMember.setExpireTime(newExpireTime);
                existingMember.setMemberLevel(memberLevel);
                existingMember.setDiscountRate(discountRate);
                existingMember.setFreeReadQuota(freeReadQuota);
                existingMember.setUpdateTime(now);
                memberInfoMapper.updateById(existingMember);
            } else {
                // 新建会员
                long daysToAdd = getDaysForLevel(memberLevel);
                MemberInfo memberInfo = new MemberInfo();
                memberInfo.setUserId(userId);
                memberInfo.setMemberLevel(memberLevel);
                memberInfo.setExpireTime(now.plusDays(daysToAdd));
                memberInfo.setFreeReadQuota(freeReadQuota);
                memberInfo.setUsedFreeRead(0);
                memberInfo.setQuotaResetDate(LocalDate.now());
                memberInfo.setDiscountRate(discountRate);
                memberInfo.setStatus(0);
                memberInfo.setCreateTime(now);
                memberInfo.setUpdateTime(now);
                memberInfoMapper.insert(memberInfo);
            }

            // 6. 创建充值记录
            UserPayLog payLog = new UserPayLog();
            payLog.setUserId(userId);
            payLog.setPayChannel(0);
            payLog.setOutTradeNo(UUID.randomUUID().toString());
            payLog.setAmount(price);
            payLog.setProductType(1); // 1 = 包年/包月VIP
            payLog.setProductId((long) memberLevel);
            payLog.setProductName("会员等级" + memberLevel);
            payLog.setProductValue(memberLevel);
            payLog.setPayTime(now);
            payLog.setCreateTime(now);
            payLog.setUpdateTime(now);
            userPayLogMapper.insert(payLog);

            // 7. 发放阅读券
            issueCoupons(userId, now);

            // 8. 清除会员缓存
            memberInfoCacheManager.evictMemberInfo(userId);

            log.info("用户 {} 购买会员等级 {} 成功，消费 {} 屋币", userId, memberLevel, price);
            return RestResp.ok();

        } finally {
            lockManager.releaseLock(lockKey, lockValue);
        }
    }

    @Override
    public RestResp<MemberInfoRespDto> getMemberInfo(Long userId) {
        MemberInfo memberInfo = memberInfoCacheManager.getActiveMemberInfo(userId);
        if (memberInfo == null) {
            return RestResp.ok(MemberInfoRespDto.builder()
                    .memberLevel(0)
                    .status(1)
                    .build());
        }
        return RestResp.ok(MemberInfoRespDto.builder()
                .memberLevel(memberInfo.getMemberLevel())
                .expireTime(memberInfo.getExpireTime())
                .freeReadQuota(memberInfo.getFreeReadQuota())
                .usedFreeRead(memberInfo.getUsedFreeRead())
                .remainingFreeRead(memberInfo.getFreeReadQuota() - memberInfo.getUsedFreeRead())
                .discountRate(memberInfo.getDiscountRate())
                .status(memberInfo.getStatus())
                .build());
    }

    @Override
    public RestResp<List<ReadingCouponRespDto>> listAvailableCoupons(Long userId) {
        List<ReadingCoupon> coupons = readingCouponMapper.selectAvailableByUserId(userId);
        List<ReadingCouponRespDto> respList = coupons.stream().map(c -> ReadingCouponRespDto.builder()
                .id(c.getId())
                .couponName(c.getCouponName())
                .discountAmount(c.getDiscountAmount())
                .minPurchaseAmount(c.getMinPurchaseAmount())
                .expireTime(c.getExpireTime())
                .useStatus(c.getUseStatus())
                .build()).toList();
        return RestResp.ok(respList);
    }

    @Override
    public void issueMonthlyCoupons() {
        // 查询所有活跃会员，发放阅读券
        QueryWrapper<MemberInfo> qw = new QueryWrapper<>();
        qw.eq("status", 0).gt("expire_time", LocalDateTime.now());
        List<MemberInfo> activeMembers = memberInfoMapper.selectList(qw);

        LocalDateTime now = LocalDateTime.now();
        for (MemberInfo member : activeMembers) {
            issueCoupons(member.getUserId(), now);
        }
        log.info("月度阅读券发放完成，共 {} 位会员", activeMembers.size());
    }

    @Override
    public void resetMonthlyQuotas() {
        LocalDate today = LocalDate.now();
        int count = memberInfoMapper.resetMonthlyQuota(today);
        log.info("月度配额重置完成，共 {} 位会员", count);
    }

    @Override
    public void processExpiredMemberships() {
        int count = memberInfoMapper.expireMemberships();
        log.info("过期会员处理完成，共 {} 位", count);
    }

    @Override
    public void processExpiredCoupons() {
        int count = readingCouponMapper.expireCoupons();
        log.info("过期阅读券处理完成，共 {} 张", count);
    }

    // ======================== 私有辅助方法 ========================

    private void issueCoupons(Long userId, LocalDateTime now) {
        int couponCount = memberProperties.getMonthlyCouponCount();
        int expireDays = memberProperties.getCouponExpireDays();
        LocalDateTime expireTime = now.plusDays(expireDays);

        for (int i = 0; i < couponCount; i++) {
            ReadingCoupon coupon = new ReadingCoupon();
            coupon.setUserId(userId);
            coupon.setCouponName("会员阅读券");
            coupon.setDiscountAmount(memberProperties.getCouponDiscountAmount());
            coupon.setMinPurchaseAmount(memberProperties.getCouponMinPurchaseAmount());
            coupon.setExpireTime(expireTime);
            coupon.setUseStatus(0);
            coupon.setCreateTime(now);
            coupon.setUpdateTime(now);
            readingCouponMapper.insert(coupon);
        }
    }

    private int getPriceForLevel(int memberLevel) {
        return switch (memberLevel) {
            case 1 -> memberProperties.getMonthlyPrice();
            case 2 -> memberProperties.getQuarterlyPrice();
            case 3 -> memberProperties.getYearlyPrice();
            default -> throw new BusinessException(ErrorCodeEnum.MEMBER_LEVEL_NOT_EXIST);
        };
    }

    private int getDiscountRateForLevel(int memberLevel) {
        return switch (memberLevel) {
            case 1 -> memberProperties.getMonthlyDiscountRate();
            case 2 -> memberProperties.getQuarterlyDiscountRate();
            case 3 -> memberProperties.getYearlyDiscountRate();
            default -> throw new BusinessException(ErrorCodeEnum.MEMBER_LEVEL_NOT_EXIST);
        };
    }

    private long getDaysForLevel(int memberLevel) {
        return switch (memberLevel) {
            case 1 -> 30;
            case 2 -> 90;
            case 3 -> 365;
            default -> throw new BusinessException(ErrorCodeEnum.MEMBER_LEVEL_NOT_EXIST);
        };
    }

}
