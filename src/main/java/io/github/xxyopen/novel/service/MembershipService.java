package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.dao.entity.ReadingVoucher;
import io.github.xxyopen.novel.dao.entity.UserMembership;
import io.github.xxyopen.novel.dto.resp.ReadingVoucherRespDto;
import io.github.xxyopen.novel.dto.resp.UserMembershipRespDto;

import java.util.List;

/**
 * 会员服务接口
 */
public interface MembershipService {

    /**
     * 购买会员
     *
     * @param userId          用户ID
     * @param membershipLevel 会员等级 1-基础 2-高级 3-至尊
     * @return 会员信息
     */
    RestResp<UserMembershipRespDto> purchaseMembership(Long userId, Integer membershipLevel);

    /**
     * 查询用户当前有效会员
     *
     * @param userId 用户ID
     * @return 会员信息（无有效会员返回 null data）
     */
    RestResp<UserMembershipRespDto> getActiveMembership(Long userId);

    /**
     * 获取用户有效会员实体（内部使用）
     *
     * @param userId 用户ID
     * @return 会员实体，无则 null
     */
    UserMembership getActiveMembershipEntity(Long userId);

    /**
     * 检查会员是否可免费阅读该章节（消耗免费配额）
     *
     * @param membership 会员实体
     * @return true=可免费阅读
     */
    boolean canFreeRead(UserMembership membership);

    /**
     * 消耗一次免费阅读配额
     *
     * @param membershipId 会员记录ID
     * @return 是否成功
     */
    boolean consumeFreeQuota(Long membershipId);

    /**
     * 退还一次免费阅读配额（退款时）
     *
     * @param membershipId 会员记录ID
     */
    void restoreFreeQuota(Long membershipId);

    /**
     * 计算会员折扣价
     *
     * @param originalPrice 原价
     * @param membership    会员实体
     * @return 折扣后价格
     */
    int calculateDiscountPrice(int originalPrice, UserMembership membership);

    /**
     * 查询用户可用阅读券列表
     *
     * @param userId 用户ID
     * @return 可用阅读券列表
     */
    RestResp<List<ReadingVoucherRespDto>> listAvailableVouchers(Long userId);

    /**
     * 使用阅读券
     *
     * @param voucherId 阅读券ID
     * @param chapterId 章节ID
     * @return 阅读券实体（用于获取抵扣金额）
     */
    ReadingVoucher useVoucher(Long voucherId, Long chapterId);

    /**
     * 退还阅读券（退款时）
     *
     * @param voucherId 阅读券ID
     */
    void restoreVoucher(Long voucherId);

    /**
     * 查询用户可用阅读券数量
     *
     * @param userId 用户ID
     * @return 可用数量
     */
    int countAvailableVouchers(Long userId);

    /**
     * 过期处理：标记过期会员和过期阅读券
     */
    void processExpirations();

    /**
     * 每月重置免费章节配额
     */
    void resetMonthlyQuotas();

}
