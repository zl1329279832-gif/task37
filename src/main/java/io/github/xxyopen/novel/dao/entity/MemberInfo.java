package io.github.xxyopen.novel.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 会员信息
 */
@TableName("member_info")
public class MemberInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 会员等级;0-非会员 1-月度 2-季度 3-年度 */
    private Integer memberLevel;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 每月免费阅读配额 */
    private Integer freeReadQuota;

    /** 当月已使用免费阅读次数 */
    private Integer usedFreeRead;

    /** 配额重置日期 */
    private LocalDate quotaResetDate;

    /** 折扣率;80=8折 100=无折扣 */
    private Integer discountRate;

    /** 状态;0-正常 1-已过期 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Integer getMemberLevel() { return memberLevel; }
    public void setMemberLevel(Integer memberLevel) { this.memberLevel = memberLevel; }

    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }

    public Integer getFreeReadQuota() { return freeReadQuota; }
    public void setFreeReadQuota(Integer freeReadQuota) { this.freeReadQuota = freeReadQuota; }

    public Integer getUsedFreeRead() { return usedFreeRead; }
    public void setUsedFreeRead(Integer usedFreeRead) { this.usedFreeRead = usedFreeRead; }

    public LocalDate getQuotaResetDate() { return quotaResetDate; }
    public void setQuotaResetDate(LocalDate quotaResetDate) { this.quotaResetDate = quotaResetDate; }

    public Integer getDiscountRate() { return discountRate; }
    public void setDiscountRate(Integer discountRate) { this.discountRate = discountRate; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "MemberInfo{" +
                "id=" + id +
                ", userId=" + userId +
                ", memberLevel=" + memberLevel +
                ", expireTime=" + expireTime +
                ", freeReadQuota=" + freeReadQuota +
                ", usedFreeRead=" + usedFreeRead +
                ", quotaResetDate=" + quotaResetDate +
                ", discountRate=" + discountRate +
                ", status=" + status +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
