package io.github.xxyopen.novel.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 会员权益快照 — 记录会员购买时冻结的权益规则
 * </p>
 */
@TableName("membership_benefit_snapshot")
public class MembershipBenefitSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 会员等级
     */
    private Integer membershipLevel;

    /**
     * 每月免费章节配额
     */
    private Integer freeChapterQuota;

    /**
     * 折扣比例;如 80 表示八折
     */
    private Integer discountRate;

    /**
     * 赠送阅读券数量
     */
    private Integer voucherCount;

    /**
     * 阅读券有效天数
     */
    private Integer voucherValidDays;

    /**
     * 权益生效起始时间
     */
    private LocalDateTime effectiveFrom;

    /**
     * 权益生效截止时间
     */
    private LocalDateTime effectiveTo;

    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getMembershipLevel() { return membershipLevel; }
    public void setMembershipLevel(Integer membershipLevel) { this.membershipLevel = membershipLevel; }
    public Integer getFreeChapterQuota() { return freeChapterQuota; }
    public void setFreeChapterQuota(Integer freeChapterQuota) { this.freeChapterQuota = freeChapterQuota; }
    public Integer getDiscountRate() { return discountRate; }
    public void setDiscountRate(Integer discountRate) { this.discountRate = discountRate; }
    public Integer getVoucherCount() { return voucherCount; }
    public void setVoucherCount(Integer voucherCount) { this.voucherCount = voucherCount; }
    public Integer getVoucherValidDays() { return voucherValidDays; }
    public void setVoucherValidDays(Integer voucherValidDays) { this.voucherValidDays = voucherValidDays; }
    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDateTime getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDateTime effectiveTo) { this.effectiveTo = effectiveTo; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    @Override
    public String toString() {
        return "MembershipBenefitSnapshot{" +
                "id=" + id +
                ", membershipLevel=" + membershipLevel +
                ", freeChapterQuota=" + freeChapterQuota +
                ", discountRate=" + discountRate +
                ", voucherCount=" + voucherCount +
                ", voucherValidDays=" + voucherValidDays +
                ", effectiveFrom=" + effectiveFrom +
                ", effectiveTo=" + effectiveTo +
                ", createTime=" + createTime +
                "}";
    }
}
