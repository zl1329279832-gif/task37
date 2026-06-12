package io.github.xxyopen.novel.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 结算批次
 */
@TableName("settlement_batch")
public class SettlementBatch implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 批次号 */
    private String batchNo;

    /** 总金额 */
    private Integer totalAmount;

    /** 章节购买金额 */
    private Integer chapterPurchaseAmount;

    /** 会员补贴金额 */
    private Integer memberSubsidyAmount;

    /** 平台活动补贴金额 */
    private Integer platformSubsidyAmount;

    /** 状态;0-处理中 1-已完成 2-失败 */
    private Integer status;

    /** 结算时间 */
    private LocalDateTime settleTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }

    public Integer getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Integer totalAmount) { this.totalAmount = totalAmount; }

    public Integer getChapterPurchaseAmount() { return chapterPurchaseAmount; }
    public void setChapterPurchaseAmount(Integer chapterPurchaseAmount) { this.chapterPurchaseAmount = chapterPurchaseAmount; }

    public Integer getMemberSubsidyAmount() { return memberSubsidyAmount; }
    public void setMemberSubsidyAmount(Integer memberSubsidyAmount) { this.memberSubsidyAmount = memberSubsidyAmount; }

    public Integer getPlatformSubsidyAmount() { return platformSubsidyAmount; }
    public void setPlatformSubsidyAmount(Integer platformSubsidyAmount) { this.platformSubsidyAmount = platformSubsidyAmount; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getSettleTime() { return settleTime; }
    public void setSettleTime(LocalDateTime settleTime) { this.settleTime = settleTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "SettlementBatch{" +
                "id=" + id +
                ", batchNo='" + batchNo + '\'' +
                ", totalAmount=" + totalAmount +
                ", chapterPurchaseAmount=" + chapterPurchaseAmount +
                ", memberSubsidyAmount=" + memberSubsidyAmount +
                ", platformSubsidyAmount=" + platformSubsidyAmount +
                ", status=" + status +
                ", settleTime=" + settleTime +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
