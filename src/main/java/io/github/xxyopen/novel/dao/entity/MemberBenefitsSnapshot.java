package io.github.xxyopen.novel.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会员权益快照
 */
@TableName("member_benefits_snapshot")
public class MemberBenefitsSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 会员等级 */
    private Integer memberLevel;

    /** 原价(屋币) */
    private Integer originalPrice;

    /** 实付(屋币) */
    private Integer actualPrice;

    /** 权益类型;0-免费读 1-折扣 2-阅读券 */
    private Integer benefitType;

    /** 权益值 */
    private Integer benefitValue;

    /** 章节ID */
    private Long chapterId;

    /** 权益过期时间 */
    private LocalDateTime expireTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Integer getMemberLevel() { return memberLevel; }
    public void setMemberLevel(Integer memberLevel) { this.memberLevel = memberLevel; }

    public Integer getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(Integer originalPrice) { this.originalPrice = originalPrice; }

    public Integer getActualPrice() { return actualPrice; }
    public void setActualPrice(Integer actualPrice) { this.actualPrice = actualPrice; }

    public Integer getBenefitType() { return benefitType; }
    public void setBenefitType(Integer benefitType) { this.benefitType = benefitType; }

    public Integer getBenefitValue() { return benefitValue; }
    public void setBenefitValue(Integer benefitValue) { this.benefitValue = benefitValue; }

    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }

    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "MemberBenefitsSnapshot{" +
                "id=" + id +
                ", userId=" + userId +
                ", memberLevel=" + memberLevel +
                ", originalPrice=" + originalPrice +
                ", actualPrice=" + actualPrice +
                ", benefitType=" + benefitType +
                ", benefitValue=" + benefitValue +
                ", chapterId=" + chapterId +
                ", expireTime=" + expireTime +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
