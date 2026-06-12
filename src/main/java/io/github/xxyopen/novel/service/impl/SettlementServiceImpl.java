package io.github.xxyopen.novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.xxyopen.novel.core.common.constant.ErrorCodeEnum;
import io.github.xxyopen.novel.core.common.exception.BusinessException;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.config.FinanceProperties;
import io.github.xxyopen.novel.core.constant.DatabaseConsts;
import io.github.xxyopen.novel.dao.entity.PendingSettlement;
import io.github.xxyopen.novel.dao.entity.RefundFreeze;
import io.github.xxyopen.novel.dao.entity.SettlementBatch;
import io.github.xxyopen.novel.dao.mapper.*;
import io.github.xxyopen.novel.dto.resp.PendingSettlementRespDto;
import io.github.xxyopen.novel.dto.resp.SettlementBatchRespDto;
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
 * 延迟结算服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementServiceImpl implements SettlementService {

    private final PendingSettlementMapper pendingSettlementMapper;
    private final SettlementBatchMapper settlementBatchMapper;
    private final RefundFreezeMapper refundFreezeMapper;
    private final AuthorIncomeDetailMapper authorIncomeDetailMapper;
    private final AuthorIncomeMapper authorIncomeMapper;
    private final FinanceProperties financeProperties;

    @Override
    public PendingSettlement createPendingSettlement(Long consumeLogId, Long userId, Long authorId,
                                                     Long bookId, Long chapterId, int amount,
                                                     int payType, int membershipSubsidy,
                                                     int platformSubsidy) {
        // 计算作者应得份额 = 用户实付 + 会员补贴 + 平台补贴
        int authorShare = amount + membershipSubsidy + platformSubsidy;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime refundWindowEnd = now.plusHours(financeProperties.getRefundWindowHours());

        PendingSettlement ps = new PendingSettlement();
        ps.setConsumeLogId(consumeLogId);
        ps.setUserId(userId);
        ps.setAuthorId(authorId);
        ps.setBookId(bookId);
        ps.setChapterId(chapterId);
        ps.setAmount(amount);
        ps.setPayType(payType);
        ps.setMembershipSubsidy(membershipSubsidy);
        ps.setPlatformSubsidy(platformSubsidy);
        ps.setAuthorShare(authorShare);
        ps.setStatus(0); // 待结算
        ps.setRefundWindowEnd(refundWindowEnd);
        ps.setCreateTime(now);
        ps.setUpdateTime(now);
        pendingSettlementMapper.insert(ps);

        log.info("创建待结算流水: consumeLogId={}, authorId={}, bookId={}, amount={}, " +
                        "membershipSubsidy={}, platformSubsidy={}, authorShare={}, 退款窗口截止={}",
                consumeLogId, authorId, bookId, amount,
                membershipSubsidy, platformSubsidy, authorShare, refundWindowEnd);

        return ps;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RestResp<Void> freezeForRefund(Long consumeLogId, Long userId, String reason) {
        // 查找待结算流水
        PendingSettlement ps = getPendingByConsumeLogId(consumeLogId);
        if (ps == null) {
            throw new BusinessException(ErrorCodeEnum.USER_PENDING_SETTLEMENT_NOT_EXIST);
        }

        // 检查退款窗口
        if (ps.getRefundWindowEnd().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCodeEnum.USER_REFUND_WINDOW_CLOSED);
        }

        // CAS 冻结待结算流水
        int affected = pendingSettlementMapper.casFreeze(ps.getId());
        if (affected == 0) {
            throw new BusinessException(ErrorCodeEnum.USER_REFUND_NOT_ALLOWED);
        }

        // 创建退款冻结记录
        RefundFreeze freeze = new RefundFreeze();
        freeze.setConsumeLogId(consumeLogId);
        freeze.setPendingSettlementId(ps.getId());
        freeze.setUserId(userId);
        freeze.setAuthorId(ps.getAuthorId());
        freeze.setFreezeAmount(ps.getAmount());
        freeze.setReason(reason);
        freeze.setStatus(0); // 已冻结
        freeze.setCreateTime(LocalDateTime.now());
        freeze.setUpdateTime(LocalDateTime.now());
        refundFreezeMapper.insert(freeze);

        // 取消待结算流水
        pendingSettlementMapper.casCancel(ps.getId());

        log.info("退款冻结: consumeLogId={}, pendingId={}, freezeAmount={}",
                consumeLogId, ps.getId(), ps.getAmount());

        return RestResp.ok();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void executeBatchSettlement() {
        LocalDateTime now = LocalDateTime.now();

        // 查询所有退款窗口已过且状态为待结算的流水
        QueryWrapper<PendingSettlement> qw = new QueryWrapper<>();
        qw.eq(DatabaseConsts.PendingSettlementTable.COLUMN_STATUS, 0)
                .le(DatabaseConsts.PendingSettlementTable.COLUMN_REFUND_WINDOW_END, now);
        List<PendingSettlement> pendingList = pendingSettlementMapper.selectList(qw);

        if (pendingList.isEmpty()) {
            log.info("无待结算流水需要处理");
            return;
        }

        // 创建结算批次
        String batchNo = "BATCH-" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        SettlementBatch batch = new SettlementBatch();
        batch.setBatchNo(batchNo);
        batch.setSettlementMonth(LocalDate.now().withDayOfMonth(1));
        batch.setStatus(0); // 处理中
        batch.setCreateTime(now);
        batch.setUpdateTime(now);
        settlementBatchMapper.insert(batch);

        int totalAmount = 0;
        int totalAuthorShare = 0;
        int totalMembershipSubsidy = 0;
        int totalPlatformSubsidy = 0;
        int recordCount = 0;
        int failCount = 0;

        // 按 (authorId, bookId) 分组处理
        Map<String, List<PendingSettlement>> grouped = pendingList.stream()
                .collect(Collectors.groupingBy(
                        ps -> ps.getAuthorId() + ":" + ps.getBookId()));

        for (Map.Entry<String, List<PendingSettlement>> entry : grouped.entrySet()) {
            List<PendingSettlement> group = entry.getValue();
            Long authorId = group.get(0).getAuthorId();
            Long bookId = group.get(0).getBookId();

            int groupAuthorShare = 0;
            int groupSettledCount = 0;

            for (PendingSettlement ps : group) {
                try {
                    // CAS 标记为已结算
                    int affected = pendingSettlementMapper.casSettle(ps.getId(), batch.getId());
                    if (affected == 0) {
                        continue; // 已被其他批次处理或已冻结
                    }

                    totalAmount += ps.getAmount();
                    totalAuthorShare += ps.getAuthorShare();
                    totalMembershipSubsidy += (ps.getMembershipSubsidy() != null ? ps.getMembershipSubsidy() : 0);
                    totalPlatformSubsidy += (ps.getPlatformSubsidy() != null ? ps.getPlatformSubsidy() : 0);
                    groupAuthorShare += ps.getAuthorShare();
                    groupSettledCount++;
                    recordCount++;
                } catch (Exception e) {
                    log.error("结算失败: pendingId={}", ps.getId(), e);
                    failCount++;
                }
            }

            // 累计到作者日收入
            if (groupAuthorShare > 0) {
                LocalDate today = LocalDate.now();
                authorIncomeDetailMapper.upsertDailyIncome(
                        authorId, bookId, today, groupAuthorShare, groupSettledCount, 0);
            }
        }

        // 更新批次汇总
        batch.setTotalAmount(totalAmount);
        batch.setTotalAuthorShare(totalAuthorShare);
        batch.setTotalMembershipSubsidy(totalMembershipSubsidy);
        batch.setTotalPlatformSubsidy(totalPlatformSubsidy);
        batch.setRecordCount(recordCount);
        batch.setStatus(failCount == 0 ? 1 : 2);
        batch.setUpdateTime(LocalDateTime.now());
        settlementBatchMapper.updateById(batch);

        log.info("结算批处理完成: batchNo={}, 成功={}, 失败={}, 作者分账总额={}",
                batchNo, recordCount, failCount, totalAuthorShare);
    }

    @Override
    public RestResp<List<PendingSettlementRespDto>> listPendingSettlements(Long authorId, Long bookId) {
        QueryWrapper<PendingSettlement> qw = new QueryWrapper<>();
        qw.eq(DatabaseConsts.PendingSettlementTable.COLUMN_AUTHOR_ID, authorId);
        if (bookId != null && bookId > 0) {
            qw.eq(DatabaseConsts.PendingSettlementTable.COLUMN_BOOK_ID, bookId);
        }
        qw.orderByDesc(DatabaseConsts.CommonColumnEnum.CREATE_TIME.getName());
        List<PendingSettlement> list = pendingSettlementMapper.selectList(qw);
        List<PendingSettlementRespDto> respList = list.stream()
                .map(this::toPendingRespDto).toList();
        return RestResp.ok(respList);
    }

    @Override
    public RestResp<List<SettlementBatchRespDto>> listSettlementBatches(Long authorId) {
        // 查询包含该作者待结算记录的批次ID
        QueryWrapper<PendingSettlement> psQw = new QueryWrapper<>();
        psQw.eq(DatabaseConsts.PendingSettlementTable.COLUMN_AUTHOR_ID, authorId)
                .eq(DatabaseConsts.PendingSettlementTable.COLUMN_STATUS, 2)
                .select("DISTINCT settled_batch_id");
        List<PendingSettlement> settledPs = pendingSettlementMapper.selectList(psQw);
        List<Long> batchIds = settledPs.stream()
                .map(PendingSettlement::getSettledBatchId)
                .filter(Objects::nonNull)
                .toList();
        if (batchIds.isEmpty()) {
            return RestResp.ok(List.of());
        }
        List<SettlementBatch> batches = settlementBatchMapper.selectBatchIds(batchIds);
        List<SettlementBatchRespDto> respList = batches.stream()
                .map(this::toBatchRespDto).toList();
        return RestResp.ok(respList);
    }

    @Override
    public PendingSettlement getPendingByConsumeLogId(Long consumeLogId) {
        QueryWrapper<PendingSettlement> qw = new QueryWrapper<>();
        qw.eq(DatabaseConsts.PendingSettlementTable.COLUMN_CONSUME_LOG_ID, consumeLogId)
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        return pendingSettlementMapper.selectOne(qw);
    }

    // ======================== 私有方法 ========================

    private PendingSettlementRespDto toPendingRespDto(PendingSettlement ps) {
        return PendingSettlementRespDto.builder()
                .id(ps.getId())
                .consumeLogId(ps.getConsumeLogId())
                .bookId(ps.getBookId())
                .chapterId(ps.getChapterId())
                .amount(ps.getAmount())
                .payType(ps.getPayType())
                .membershipSubsidy(ps.getMembershipSubsidy())
                .platformSubsidy(ps.getPlatformSubsidy())
                .authorShare(ps.getAuthorShare())
                .status(ps.getStatus())
                .refundWindowEnd(ps.getRefundWindowEnd())
                .createTime(ps.getCreateTime())
                .build();
    }

    private SettlementBatchRespDto toBatchRespDto(SettlementBatch b) {
        return SettlementBatchRespDto.builder()
                .id(b.getId())
                .batchNo(b.getBatchNo())
                .settlementMonth(b.getSettlementMonth())
                .totalAmount(b.getTotalAmount())
                .totalAuthorShare(b.getTotalAuthorShare())
                .totalMembershipSubsidy(b.getTotalMembershipSubsidy())
                .totalPlatformSubsidy(b.getTotalPlatformSubsidy())
                .recordCount(b.getRecordCount())
                .status(b.getStatus())
                .createTime(b.getCreateTime())
                .build();
    }

}
