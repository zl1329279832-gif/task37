package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.dto.resp.MemberInfoRespDto;
import io.github.xxyopen.novel.dto.resp.ReadingCouponRespDto;

import java.util.List;

/**
 * 会员服务接口
 */
public interface MemberService {

    /**
     * 购买会员
     */
    RestResp<Void> purchaseMembership(Long userId, Integer memberLevel);

    /**
     * 获取会员信息
     */
    RestResp<MemberInfoRespDto> getMemberInfo(Long userId);

    /**
     * 获取可用阅读券列表
     */
    RestResp<List<ReadingCouponRespDto>> listAvailableCoupons(Long userId);

    /**
     * 批量发放月度阅读券
     */
    void issueMonthlyCoupons();

    /**
     * 重置月度配额
     */
    void resetMonthlyQuotas();

    /**
     * 处理过期会员
     */
    void processExpiredMemberships();

    /**
     * 处理过期阅读券
     */
    void processExpiredCoupons();

}
