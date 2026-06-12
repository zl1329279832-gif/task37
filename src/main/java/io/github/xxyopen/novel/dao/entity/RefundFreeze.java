package io.github.xxyopen.novel.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 退款冻结记录 — 退款窗口内退款时冻结对应的待结算流水
 * </p>
 */
@TableName("refund_freeze")
public class RefundFreeze implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 消费记录ID
     */
    private Long consumeLogId;

    /**
     * 待结算流水ID
     */
    private Long pendingSettlementId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 作者ID
     */
    private Long authorId;

    /**
     * 冻结金额（用户实付部分）
     */
    private Integer freezeAmount;

    /**
     * 冻结原因
     */
    private String reason;

    /**
     * 状态;0-已冻结 1-已退款 2-已释放（窗口过期未退款则释放）
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getConsumeLogId() { return consumeLogId; }
    public void setConsumeLogId(Long consumeLogId) { this.consumeLogId = consumeLogId; }
    public Long getPendingSettlementId() { return pendingSettlementId; }
    public void setPendingSettlementId(Long pendingSettlementId) { this.pendingSettlementId = pendingSettlementId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public Integer getFreezeAmount() { return freezeAmount; }
    public void setFreezeAmount(Integer freezeAmount) { this.freezeAmount = freezeAmount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "RefundFreeze{" +
                "id=" + id +
                ", consumeLogId=" + consumeLogId +
                ", pendingSettlementId=" + pendingSettlementId +
                ", userId=" + userId +
                ", authorId=" + authorId +
                ", freezeAmount=" + freezeAmount +
                ", reason='" + reason + '\'' +
                ", status=" + status +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                "}";
    }
}
