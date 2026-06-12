package io.github.xxyopen.novel.core.task;

import io.github.xxyopen.novel.dao.mapper.MemberInfoMapper;
import io.github.xxyopen.novel.dao.mapper.ReadingCouponMapper;
import io.github.xxyopen.novel.service.MemberService;
import io.github.xxyopen.novel.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 批量结算与会员维护定时任务
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchSettlementTask {

    private final SettlementService settlementService;
    private final MemberService memberService;
    private final MemberInfoMapper memberInfoMapper;
    private final ReadingCouponMapper readingCouponMapper;

    /**
     * 每小时执行一次批量结算
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void executeBatchSettlement() {
        log.info("开始执行批量结算任务");
        try {
            settlementService.executeBatchSettlement();
        } catch (Exception e) {
            log.error("批量结算任务执行失败", e);
        }
    }

    /**
     * 每日凌晨2点处理过期会员
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void processExpiredMemberships() {
        log.info("开始处理过期会员");
        try {
            memberService.processExpiredMemberships();
        } catch (Exception e) {
            log.error("过期会员处理失败", e);
        }
    }

    /**
     * 每月1日00:30重置月度配额
     */
    @Scheduled(cron = "0 30 0 1 * ?")
    public void resetMonthlyQuotas() {
        log.info("开始重置月度配额");
        try {
            memberService.resetMonthlyQuotas();
        } catch (Exception e) {
            log.error("月度配额重置失败", e);
        }
    }

    /**
     * 每日凌晨3点处理过期阅读券
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void processExpiredCoupons() {
        log.info("开始处理过期阅读券");
        try {
            memberService.processExpiredCoupons();
        } catch (Exception e) {
            log.error("过期阅读券处理失败", e);
        }
    }

    /**
     * 每日凌晨4点发放月度阅读券
     */
    @Scheduled(cron = "0 0 4 1 * ?")
    public void issueMonthlyCoupons() {
        log.info("开始发放月度阅读券");
        try {
            memberService.issueMonthlyCoupons();
        } catch (Exception e) {
            log.error("月度阅读券发放失败", e);
        }
    }

}
