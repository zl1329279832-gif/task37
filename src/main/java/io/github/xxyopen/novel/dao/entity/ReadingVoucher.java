package io.github.xxyopen.novel.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 限时阅读券
 * </p>
 */
@TableName("reading_voucher")
public class ReadingVoucher implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 关联的会员记录ID
     */
    private Long membershipId;

    /**
     * 券类型;0-全免券 1-折扣券
     */
    private Integer voucherType;

    /**
     * 抵扣金额（屋币）;全免券=章节价格，折扣券=固定减免额
     */
    private Integer discountAmount;

    /**
     * 状态;0-未使用 1-已使用 2-已过期
     */
    private Integer status;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 使用的章节ID
     */
    private Long usedChapterId;

    /**
     * 使用时间
     */
    private LocalDateTime usedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getMembershipId() { return membershipId; }
    public void setMembershipId(Long membershipId) { this.membershipId = membershipId; }
    public Integer getVoucherType() { return voucherType; }
    public void setVoucherType(Integer voucherType) { this.voucherType = voucherType; }
    public Integer getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(Integer discountAmount) { this.discountAmount = discountAmount; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }
    public Long getUsedChapterId() { return usedChapterId; }
    public void setUsedChapterId(Long usedChapterId) { this.usedChapterId = usedChapterId; }
    public LocalDateTime getUsedTime() { return usedTime; }
    public void setUsedTime(LocalDateTime usedTime) { this.usedTime = usedTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "ReadingVoucher{" +
                "id=" + id +
                ", userId=" + userId +
                ", membershipId=" + membershipId +
                ", voucherType=" + voucherType +
                ", discountAmount=" + discountAmount +
                ", status=" + status +
                ", expireTime=" + expireTime +
                ", usedChapterId=" + usedChapterId +
                ", usedTime=" + usedTime +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                "}";
    }
}
