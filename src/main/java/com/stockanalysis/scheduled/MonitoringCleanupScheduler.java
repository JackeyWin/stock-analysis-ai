package com.stockanalysis.scheduled;

import com.stockanalysis.service.StockAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MonitoringCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitoringCleanupScheduler.class);

    private final StockAnalysisService stockAnalysisService;

    public MonitoringCleanupScheduler(StockAnalysisService stockAnalysisService) {
        this.stockAnalysisService = stockAnalysisService;
    }

    // 每个工作日 15:00 自动清理所有盯盘任务（Asia/Shanghai 时区）
    // 表达式：秒 分 时 日 月 周
    @Scheduled(cron = "0 0 15 * * MON-FRI", zone = "Asia/Shanghai")
    public void cleanupAllMonitoringJobsAtMarketClose() {
        try {
            log.info("[MonitoringCleanupScheduler] 触发每日15:00清理所有盯盘任务...");
            stockAnalysisService.cleanupAllMonitoringJobs();
            log.info("[MonitoringCleanupScheduler] 清理所有盯盘任务完成。");
        } catch (Exception e) {
            log.error("[MonitoringCleanupScheduler] 清理盯盘任务失败", e);
        }
    }

    // 每个工作日 11:30 自动暂停所有盯盘任务（Asia/Shanghai 时区）
    @Scheduled(cron = "0 30 11 * * MON-FRI", zone = "Asia/Shanghai")
    public void pauseAllMonitoringAtLunchBreak() {
        try {
            log.info("[MonitoringCleanupScheduler] 触发每日11:30暂停所有盯盘任务...");
            stockAnalysisService.pauseAllMonitoringJobs();
            log.info("[MonitoringCleanupScheduler] 暂停所有盯盘任务完成。");
        } catch (Exception e) {
            log.error("[MonitoringCleanupScheduler] 暂停盯盘任务失败", e);
        }
    }

    // 每个工作日 13:00 自动恢复盯盘任务（Asia/Shanghai 时区）
    @Scheduled(cron = "0 0 13 * * MON-FRI", zone = "Asia/Shanghai")
    public void resumeAllMonitoringAfterLunchBreak() {
        try {
            log.info("[MonitoringCleanupScheduler] 触发每日13:00恢复盯盘任务...");
            stockAnalysisService.resumeAllMonitoringJobs();
            log.info("[MonitoringCleanupScheduler] 恢复所有盯盘任务完成。");
        } catch (Exception e) {
            log.error("[MonitoringCleanupScheduler] 恢复盯盘任务失败", e);
        }
    }
}


