package io.github.xxyopen.novel.service.impl;

import io.github.xxyopen.novel.core.common.constant.ErrorCodeEnum;
import io.github.xxyopen.novel.core.common.exception.BusinessException;
import io.github.xxyopen.novel.core.config.FinanceProperties;
import io.github.xxyopen.novel.dao.entity.*;
import io.github.xxyopen.novel.dao.mapper.*;
import io.github.xxyopen.novel.dto.SettlementAggregation;
import io.github.xxyopen.novel.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 结算服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementServiceImpl implements SettlementService {

    private final PendingSettlementMapper pendingSettlementMapper;
    private final SettlementBatchMapper settlementBatchMapper;
    private final RefundFreezeMapper refundFreezeMapper;
    private final AuthorIncomeBreakdownMapper authorIncomeBreakdownMapper;
    private final AuthorIncomeDetailMapper authorIncomeDetailMapper;
    private final FinanceProperties financeProperties;

    @Override
    public PendingSettlement createPendingSettlement(
            Long userId, Long authorId, Long bookId, Long chapterId,
            Long consumeLogId, Integer settlementType,
            Integer originalAmount, Integer discountAmount,
            Integer couponAmount, Integer actualAmount) {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime freezeEndTime = now.plusDays(financeProperties.getRefundFreezeDays());

        // 1. 创建待结算记录
        PendingSettlement settlement = new PendingSettlement();
        settlement.setUserId(userId);
        settlement.setAuthorId(authorId);
        settlement.setBookId(bookId);
        settlement.setChapterId(chapterId);
        settlement.setConsumeLogId(consumeLogId);
        settlement.setSettlementType(settlementType);
        settlement.setOriginalAmount(originalAmount);
        settlement.setDiscountAmount(discountAmount);
        settlement.setCouponAmount(couponAmount);
        settlement.setActualAmount(actualAmount);
        settlement.setStatus(0); // 待结算
        settlement.setFreezeEndTime(freezeEndTime);
        settlement.setCreateTime(now);
        settlement.setUpdateTime(now);
        pendingSettlementMapper.insert(settlement);

        // 2. 如果有消费记录，创建退款冻结
        if (consumeLogId != null && actualAmount > 0) {
            RefundFreeze freeze = new RefundFreeze();
            freeze.setConsumeLogId(consumeLogId);
            freeze.setPendingSettlementId(settlement.getId());
            freeze.setUserId(userId);
            freeze.setFreezeAmount(actualAmount);
            freeze.setFreezeEndTime(freezeEndTime);
            freeze.setStatus(0); // 冻结中
            freeze.setCreateTime(now);
            freeze.setUpdateTime(now);
            refundFreezeMapper.insert(freeze);
        }

        log.info("创建待结算记录: userId={}, chapterId={}, type={}, amount={}",
                userId, chapterId, settlementType, actualAmount);
        return settlement;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void executeBatchSettlement() {
        int batchSize = financeProperties.getSettlementBatchSize();

        // 1. 查询可结算记录
        List<PendingSettlement> settleableList = pendingSettlementMapper.selectSettleable(batchSize);
        if (settleableList == null || settleableList.isEmpty()) {
            log.info("无待结算记录");
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // 2. 创建结算批次
        String batchNo = "BATCH-" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        SettlementBatch batch = new SettlementBatch();
        batch.setBatchNo(batchNo);
        batch.setTotalAmount(0);
        batch.setChapterPurchaseAmount(0);
        batch.setMemberSubsidyAmount(0);
        batch.setPlatformSubsidyAmount(0);
        batch.setStatus(0); // 处理中
        batch.setSettleTime(now);
        batch.setCreateTime(now);
        batch.setUpdateTime(now);
        settlementBatchMapper.insert(batch);

        Long batchId = batch.getId();

        // 3. 按 (authorId, bookId) 分组聚合
        Map<String, SettlementAggregation> aggregationMap = new LinkedHashMap<>();

        for (PendingSettlement ps : settleableList) {
            // CAS 标记已结算
            int casResult = pendingSettlementMapper.casMarkSettled(ps.getId(), batchId);
            if (casResult == 0) {
                // 已被其他批次处理或已取消，跳过
                continue;
            }

            String key = ps.getAuthorId() + "_" + ps.getBookId();
            SettlementAggregation agg = aggregationMap.computeIfAbsent(key, k -> {
                SettlementAggregation a = new SettlementAggregation();
                a.setAuthorId(ps.getAuthorId());
                a.setBookId(ps.getBookId());
                a.setTotalAmount(0);
                a.setChapterPurchaseAmount(0);
                a.setMemberSubsidyAmount(0);
                a.setPlatformSubsidyAmount(0);
                return a;
            });

            // 按结算类型拆分金额
            int amount = ps.getActualAmount();
            agg.setTotalAmount(agg.getTotalAmount() + amount);

            switch (ps.getSettlementType()) {
                case 0 -> agg.setChapterPurchaseAmount(agg.getChapterPurchaseAmount() + amount);
                case 1 -> agg.setMemberSubsidyAmount(agg.getMemberSubsidyAmount() + amount);
                case 2 -> agg.setPlatformSubsidyAmount(agg.getPlatformSubsidyAmount() + amount);
            }

            // 4. 插入作者收入拆分记录
            AuthorIncomeBreakdown breakdown = new AuthorIncomeBreakdown();
            breakdown.setAuthorId(ps.getAuthorId());
            breakdown.setBookId(ps.getBookId());
            breakdown.setPendingSettlementId(ps.getId());
            breakdown.setBatchId(batchId);
            breakdown.setIncomeType(ps.getSettlementType());
            breakdown.setAmount(amount);
            breakdown.setCreateTime(now);
            breakdown.setUpdateTime(now);
            authorIncomeBreakdownMapper.insert(breakdown);
        }

        // 5. 汇总并更新作者日收入
        LocalDate today = LocalDate.now();
        int totalChapter = 0;
        int totalMember = 0;
        int totalPlatform = 0;

        for (SettlementAggregation agg : aggregationMap.values()) {
            // upsert 日收入（使用结算日期）
            int totalForAuthor = agg.getTotalAmount();
            if (totalForAuthor > 0) {
                // 简化处理：每条结算记录贡献1次count，去重用户数暂用count代替
                authorIncomeDetailMapper.upsertDailyIncome(
                        agg.getAuthorId(), agg.getBookId(), today,
                        totalForAuthor, 1, 1);
            }
            totalChapter += agg.getChapterPurchaseAmount();
            totalMember += agg.getMemberSubsidyAmount();
            totalPlatform += agg.getPlatformSubsidyAmount();
        }

        // 6. 更新结算批次
        int grandTotal = totalChapter + totalMember + totalPlatform;
        batch.setTotalAmount(grandTotal);
        batch.setChapterPurchaseAmount(totalChapter);
        batch.setMemberSubsidyAmount(totalMember);
        batch.setPlatformSubsidyAmount(totalPlatform);
        batch.setStatus(1); // 已完成
        batch.setUpdateTime(now);
        settlementBatchMapper.updateById(batch);

        log.info("批量结算完成: batchNo={}, total={}, chapter={}, member={}, platform={}",
                batchNo, grandTotal, totalChapter, totalMember, totalPlatform);
    }

    @Override
    public void cancelPendingSettlement(Long consumeLogId) {
        PendingSettlement ps = pendingSettlementMapper.selectByConsumeLogId(consumeLogId);
        if (ps == null) {
            // 旧记录可能没有待结算，兼容处理
            log.info("未找到待结算记录，可能为旧购买记录: consumeLogId={}", consumeLogId);
            return;
        }

        // CAS 取消待结算
        int casResult = pendingSettlementMapper.casMarkCancelled(ps.getId());
        if (casResult == 0) {
            log.info("待结算记录已处理，无法取消: id={}", ps.getId());
            return;
        }

        // CAS 标记退款冻结为已退款
        refundFreezeMapper.casMarkRefunded(consumeLogId);

        log.info("取消待结算记录: consumeLogId={}, pendingId={}", consumeLogId, ps.getId());
    }

}
