package io.github.xxyopen.novel.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * <p>
 * 结算批次 — 退款窗口结束后按批次将待结算流水入账
 * </p>
 */
@TableName("settlement_batch")
public class SettlementBatch implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 批次号
     */
    private String batchNo;

    /**
     * 结算月份
     */
    private LocalDate settlementMonth;

    /**
     * 本批次用户实付总额
     */
    private Integer totalAmount;

    /**
     * 本批次作者分账总额
     */
    private Integer totalAuthorShare;

    /**
     * 本批次会员补贴总额
     */
    private Integer totalMembershipSubsidy;

    /**
     * 本批次平台活动补贴总额
     */
    private Integer totalPlatformSubsidy;

    /**
     * 本批次结算记录条数
     */
    private Integer recordCount;

    /**
     * 状态;0-处理中 1-已完成 2-失败
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public LocalDate getSettlementMonth() { return settlementMonth; }
    public void setSettlementMonth(LocalDate settlementMonth) { this.settlementMonth = settlementMonth; }
    public Integer getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Integer totalAmount) { this.totalAmount = totalAmount; }
    public Integer getTotalAuthorShare() { return totalAuthorShare; }
    public void setTotalAuthorShare(Integer totalAuthorShare) { this.totalAuthorShare = totalAuthorShare; }
    public Integer getTotalMembershipSubsidy() { return totalMembershipSubsidy; }
    public void setTotalMembershipSubsidy(Integer totalMembershipSubsidy) { this.totalMembershipSubsidy = totalMembershipSubsidy; }
    public Integer getTotalPlatformSubsidy() { return totalPlatformSubsidy; }
    public void setTotalPlatformSubsidy(Integer totalPlatformSubsidy) { this.totalPlatformSubsidy = totalPlatformSubsidy; }
    public Integer getRecordCount() { return recordCount; }
    public void setRecordCount(Integer recordCount) { this.recordCount = recordCount; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "SettlementBatch{" +
                "id=" + id +
                ", batchNo='" + batchNo + '\'' +
                ", settlementMonth=" + settlementMonth +
                ", totalAmount=" + totalAmount +
                ", totalAuthorShare=" + totalAuthorShare +
                ", totalMembershipSubsidy=" + totalMembershipSubsidy +
                ", totalPlatformSubsidy=" + totalPlatformSubsidy +
                ", recordCount=" + recordCount +
                ", status=" + status +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                "}";
    }
}
