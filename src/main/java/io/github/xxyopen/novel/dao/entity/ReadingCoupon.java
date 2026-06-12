package io.github.xxyopen.novel.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 阅读券
 */
@TableName("reading_coupon")
public class ReadingCoupon implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 券名称 */
    private String couponName;

    /** 抵扣金额(屋币) */
    private Integer discountAmount;

    /** 最低消费门槛 */
    private Integer minPurchaseAmount;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 使用状态;0-未使用 1-已使用 2-已过期 */
    private Integer useStatus;

    /** 使用时间 */
    private LocalDateTime usedTime;

    /** 关联消费记录ID */
    private Long consumeLogId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCouponName() { return couponName; }
    public void setCouponName(String couponName) { this.couponName = couponName; }

    public Integer getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(Integer discountAmount) { this.discountAmount = discountAmount; }

    public Integer getMinPurchaseAmount() { return minPurchaseAmount; }
    public void setMinPurchaseAmount(Integer minPurchaseAmount) { this.minPurchaseAmount = minPurchaseAmount; }

    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }

    public Integer getUseStatus() { return useStatus; }
    public void setUseStatus(Integer useStatus) { this.useStatus = useStatus; }

    public LocalDateTime getUsedTime() { return usedTime; }
    public void setUsedTime(LocalDateTime usedTime) { this.usedTime = usedTime; }

    public Long getConsumeLogId() { return consumeLogId; }
    public void setConsumeLogId(Long consumeLogId) { this.consumeLogId = consumeLogId; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "ReadingCoupon{" +
                "id=" + id +
                ", userId=" + userId +
                ", couponName='" + couponName + '\'' +
                ", discountAmount=" + discountAmount +
                ", minPurchaseAmount=" + minPurchaseAmount +
                ", expireTime=" + expireTime +
                ", useStatus=" + useStatus +
                ", usedTime=" + usedTime +
                ", consumeLogId=" + consumeLogId +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
