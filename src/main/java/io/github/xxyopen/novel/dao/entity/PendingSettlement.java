package io.github.xxyopen.novel.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 待结算流水
 */
@TableName("pending_settlement")
public class PendingSettlement implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 作者ID */
    private Long authorId;

    /** 小说ID */
    private Long bookId;

    /** 章节ID */
    private Long chapterId;

    /** 消费记录ID;NULL=会员免费读 */
    private Long consumeLogId;

    /** 结算类型;0-章节购买 1-会员免费读 2-平台活动 */
    private Integer settlementType;

    /** 原价(屋币) */
    private Integer originalAmount;

    /** 折扣减免 */
    private Integer discountAmount;

    /** 阅读券抵扣 */
    private Integer couponAmount;

    /** 实付金额 */
    private Integer actualAmount;

    /** 状态;0-待结算 1-已结算 2-已取消 */
    private Integer status;

    /** 退款冻结结束时间 */
    private LocalDateTime freezeEndTime;

    /** 结算批次ID */
    private Long batchId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }

    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }

    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }

    public Long getConsumeLogId() { return consumeLogId; }
    public void setConsumeLogId(Long consumeLogId) { this.consumeLogId = consumeLogId; }

    public Integer getSettlementType() { return settlementType; }
    public void setSettlementType(Integer settlementType) { this.settlementType = settlementType; }

    public Integer getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(Integer originalAmount) { this.originalAmount = originalAmount; }

    public Integer getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(Integer discountAmount) { this.discountAmount = discountAmount; }

    public Integer getCouponAmount() { return couponAmount; }
    public void setCouponAmount(Integer couponAmount) { this.couponAmount = couponAmount; }

    public Integer getActualAmount() { return actualAmount; }
    public void setActualAmount(Integer actualAmount) { this.actualAmount = actualAmount; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getFreezeEndTime() { return freezeEndTime; }
    public void setFreezeEndTime(LocalDateTime freezeEndTime) { this.freezeEndTime = freezeEndTime; }

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "PendingSettlement{" +
                "id=" + id +
                ", userId=" + userId +
                ", authorId=" + authorId +
                ", bookId=" + bookId +
                ", chapterId=" + chapterId +
                ", consumeLogId=" + consumeLogId +
                ", settlementType=" + settlementType +
                ", originalAmount=" + originalAmount +
                ", discountAmount=" + discountAmount +
                ", couponAmount=" + couponAmount +
                ", actualAmount=" + actualAmount +
                ", status=" + status +
                ", freezeEndTime=" + freezeEndTime +
                ", batchId=" + batchId +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
