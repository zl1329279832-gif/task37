package io.github.xxyopen.novel.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 待结算流水 — 用户购买后收入先进入待结算池，退款窗口结束后再结算给作者
 * </p>
 */
@TableName("pending_settlement")
public class PendingSettlement implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 消费记录ID
     */
    private Long consumeLogId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 作者ID
     */
    private Long authorId;

    /**
     * 小说ID
     */
    private Long bookId;

    /**
     * 章节ID
     */
    private Long chapterId;

    /**
     * 用户实付金额（屋币）
     */
    private Integer amount;

    /**
     * 支付方式;0-普通余额 1-会员免费 2-会员折扣 3-阅读券
     */
    private Integer payType;

    /**
     * 会员补贴金额（平台承担的会员免费/折扣差价）
     */
    private Integer membershipSubsidy;

    /**
     * 平台活动补贴金额
     */
    private Integer platformSubsidy;

    /**
     * 作者应得份额（屋币）
     */
    private Integer authorShare;

    /**
     * 状态;0-待结算 1-退款冻结 2-已结算 3-已取消
     */
    private Integer status;

    /**
     * 退款窗口截止时间
     */
    private LocalDateTime refundWindowEnd;

    /**
     * 结算批次ID
     */
    private Long settledBatchId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getConsumeLogId() { return consumeLogId; }
    public void setConsumeLogId(Long consumeLogId) { this.consumeLogId = consumeLogId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }
    public Integer getPayType() { return payType; }
    public void setPayType(Integer payType) { this.payType = payType; }
    public Integer getMembershipSubsidy() { return membershipSubsidy; }
    public void setMembershipSubsidy(Integer membershipSubsidy) { this.membershipSubsidy = membershipSubsidy; }
    public Integer getPlatformSubsidy() { return platformSubsidy; }
    public void setPlatformSubsidy(Integer platformSubsidy) { this.platformSubsidy = platformSubsidy; }
    public Integer getAuthorShare() { return authorShare; }
    public void setAuthorShare(Integer authorShare) { this.authorShare = authorShare; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getRefundWindowEnd() { return refundWindowEnd; }
    public void setRefundWindowEnd(LocalDateTime refundWindowEnd) { this.refundWindowEnd = refundWindowEnd; }
    public Long getSettledBatchId() { return settledBatchId; }
    public void setSettledBatchId(Long settledBatchId) { this.settledBatchId = settledBatchId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "PendingSettlement{" +
                "id=" + id +
                ", consumeLogId=" + consumeLogId +
                ", userId=" + userId +
                ", authorId=" + authorId +
                ", bookId=" + bookId +
                ", chapterId=" + chapterId +
                ", amount=" + amount +
                ", payType=" + payType +
                ", membershipSubsidy=" + membershipSubsidy +
                ", platformSubsidy=" + platformSubsidy +
                ", authorShare=" + authorShare +
                ", status=" + status +
                ", refundWindowEnd=" + refundWindowEnd +
                ", settledBatchId=" + settledBatchId +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                "}";
    }
}
