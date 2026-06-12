package io.github.xxyopen.novel.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 退款冻结
 */
@TableName("refund_freeze")
public class RefundFreeze implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 消费记录ID */
    private Long consumeLogId;

    /** 待结算ID */
    private Long pendingSettlementId;

    /** 用户ID */
    private Long userId;

    /** 冻结金额 */
    private Integer freezeAmount;

    /** 冻结结束时间 */
    private LocalDateTime freezeEndTime;

    /** 状态;0-冻结中 1-已解冻 2-已退款 */
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

    public Integer getFreezeAmount() { return freezeAmount; }
    public void setFreezeAmount(Integer freezeAmount) { this.freezeAmount = freezeAmount; }

    public LocalDateTime getFreezeEndTime() { return freezeEndTime; }
    public void setFreezeEndTime(LocalDateTime freezeEndTime) { this.freezeEndTime = freezeEndTime; }

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
                ", freezeAmount=" + freezeAmount +
                ", freezeEndTime=" + freezeEndTime +
                ", status=" + status +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
