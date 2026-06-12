package io.github.xxyopen.novel.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户会员信息
 * </p>
 */
@TableName("user_membership")
public class UserMembership implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会员等级;1-基础会员 2-高级会员 3-至尊会员
     */
    private Integer membershipLevel;

    /**
     * 生效时间
     */
    private LocalDateTime startTime;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 每月免费章节配额
     */
    private Integer freeChapterQuota;

    /**
     * 本月已用免费章节数
     */
    private Integer freeChapterUsed;

    /**
     * 折扣比例;如 80 表示八折
     */
    private Integer discountRate;

    /**
     * 状态;0-生效中 1-已过期 2-已取消
     */
    private Integer status;

    /**
     * 权益快照ID
     */
    private Long snapshotId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getMembershipLevel() { return membershipLevel; }
    public void setMembershipLevel(Integer membershipLevel) { this.membershipLevel = membershipLevel; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }
    public Integer getFreeChapterQuota() { return freeChapterQuota; }
    public void setFreeChapterQuota(Integer freeChapterQuota) { this.freeChapterQuota = freeChapterQuota; }
    public Integer getFreeChapterUsed() { return freeChapterUsed; }
    public void setFreeChapterUsed(Integer freeChapterUsed) { this.freeChapterUsed = freeChapterUsed; }
    public Integer getDiscountRate() { return discountRate; }
    public void setDiscountRate(Integer discountRate) { this.discountRate = discountRate; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getSnapshotId() { return snapshotId; }
    public void setSnapshotId(Long snapshotId) { this.snapshotId = snapshotId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "UserMembership{" +
                "id=" + id +
                ", userId=" + userId +
                ", membershipLevel=" + membershipLevel +
                ", startTime=" + startTime +
                ", expireTime=" + expireTime +
                ", freeChapterQuota=" + freeChapterQuota +
                ", freeChapterUsed=" + freeChapterUsed +
                ", discountRate=" + discountRate +
                ", status=" + status +
                ", snapshotId=" + snapshotId +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                "}";
    }
}
