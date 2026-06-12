package io.github.xxyopen.novel.core.task;

import io.github.xxyopen.novel.service.MembershipService;
import io.github.xxyopen.novel.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 结算批处理定时任务
 * - 每小时执行一次，将退款窗口已关闭的待结算流水入账
 * - 每天凌晨处理会员/阅读券过期
 * - 每月1日重置免费章节配额
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementBatchTask {

    private final SettlementService settlementService;
    private final MembershipService membershipService;

    /**
     * 每小时执行结算批处理
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void executeHourlySettlement() {
        log.info("开始执行结算批处理...");
        try {
            settlementService.executeBatchSettlement();
        } catch (Exception e) {
            log.error("结算批处理异常", e);
        }
    }

    /**
     * 每天凌晨2点处理过期会员和阅读券
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void processExpirations() {
        log.info("开始处理会员和阅读券过期...");
        try {
            membershipService.processExpirations();
        } catch (Exception e) {
            log.error("过期处理异常", e);
        }
    }

    /**
     * 每月1日凌晨0点重置免费章节配额
     */
    @Scheduled(cron = "0 0 0 1 * ?")
    public void resetMonthlyQuotas() {
        log.info("开始重置月度免费章节配额...");
        try {
            membershipService.resetMonthlyQuotas();
        } catch (Exception e) {
            log.error("月度配额重置异常", e);
        }
    }

    /**
     * 手动触发结算批处理（用于测试）
     */
    public void manualExecute() {
        settlementService.executeBatchSettlement();
    }

}
