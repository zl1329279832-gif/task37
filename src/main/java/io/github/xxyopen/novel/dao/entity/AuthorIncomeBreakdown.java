package io.github.xxyopen.novel.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 作者收入拆分
 */
@TableName("author_income_breakdown")
public class AuthorIncomeBreakdown implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 作者ID */
    private Long authorId;

    /** 小说ID */
    private Long bookId;

    /** 待结算ID */
    private Long pendingSettlementId;

    /** 批次ID */
    private Long batchId;

    /** 收入类型;0-章节购买 1-会员补贴 2-平台补贴 */
    private Integer incomeType;

    /** 金额(屋币) */
    private Integer amount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }

    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }

    public Long getPendingSettlementId() { return pendingSettlementId; }
    public void setPendingSettlementId(Long pendingSettlementId) { this.pendingSettlementId = pendingSettlementId; }

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public Integer getIncomeType() { return incomeType; }
    public void setIncomeType(Integer incomeType) { this.incomeType = incomeType; }

    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "AuthorIncomeBreakdown{" +
                "id=" + id +
                ", authorId=" + authorId +
                ", bookId=" + bookId +
                ", pendingSettlementId=" + pendingSettlementId +
                ", batchId=" + batchId +
                ", incomeType=" + incomeType +
                ", amount=" + amount +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
