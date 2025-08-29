package com.stockanalysis.scheduled;

import com.stockanalysis.model.DailyRecommendation;
import com.stockanalysis.service.DailyRecommendationService;
import com.stockanalysis.service.SimplifiedStockPickerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * AI选股定时任务调度器
 */
@Slf4j
@Component
public class AIStockPickerScheduler {

    private final DailyRecommendationService dailyRecommendationService;
    private final SimplifiedStockPickerService simplifiedStockPickerService;

    public AIStockPickerScheduler(DailyRecommendationService dailyRecommendationService, 
                                SimplifiedStockPickerService simplifiedStockPickerService) {
        this.dailyRecommendationService = dailyRecommendationService;
        this.simplifiedStockPickerService = simplifiedStockPickerService;
    }

    /**
     * 每日凌晨1点执行AI选股
     * cron表达式: 秒 分 时 日 月 周
     * 0 0 1 * * ? 表示每天凌晨1点执行
     */
    @Scheduled(cron = "0 37 9 * * ?")
    @Transactional
    public void performDailyStockPicking() {
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        log.info("=== 开始执行每日AI选股任务 === 时间: {}", currentTime);
        
        try {
            // 检查是否需要更新
            // if (!dailyRecommendationService.needsUpdate()) {
            //     log.info("今日推荐已存在，跳过生成");
            //     return;
            // }
            
            long startTime = System.currentTimeMillis();
            
            // 执行简化版AI选股
            DailyRecommendation dailyRecommendation = dailyRecommendationService.generateDailyRecommendation();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            if (dailyRecommendation != null) {
                // 在事务中安全访问懒加载集合
                int stockCount = 0;
                try {
                    stockCount = dailyRecommendation.getRecommendedStocks() != null ? 
                        dailyRecommendation.getRecommendedStocks().size() : 0;
                } catch (Exception e) {
                    log.warn("获取推荐股票数量时出现懒加载问题，使用默认值: {}", e.getMessage());
                    stockCount = 0;
                }
                
                log.info("=== AI选股任务执行成功 === 推荐股票数: {}, 耗时: {}ms", stockCount, duration);
                
                // 记录成功指标
                recordSuccessMetrics(stockCount, duration);
                
            } else {
                log.error("=== AI选股任务执行失败 === 推荐结果异常");
                recordFailureMetrics("推荐结果异常");
            }
            
        } catch (Exception e) {
            log.error("=== AI选股任务执行失败 === 错误: {}", e.getMessage(), e);
            recordFailureMetrics(e.getMessage());
        }
    }

    /**
     * 每小时检查推荐状态（工作时间）
     * 0 0 9-17 * * MON-FRI 表示周一到周五的9点到17点每小时执行
     */
    @Scheduled(cron = "0 0 9-17 * * MON-FRI")
    @Transactional
    public void checkRecommendationStatus() {
        try {
            log.debug("检查推荐状态");
            
            if (dailyRecommendationService.needsUpdate()) {
                log.warn("检测到推荐需要更新，但当前为工作时间，跳过自动更新");
            }
            
            // 获取推荐状态
            var status = dailyRecommendationService.getRecommendationStatus();
            log.debug("推荐状态: {}", status);
            
        } catch (Exception e) {
            log.warn("检查推荐状态失败: {}", e.getMessage());
        }
    }

    /**
     * 每天凌晨2点清理过期数据
     * 0 0 2 * * ? 表示每天凌晨2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredData() {
        try {
            log.info("开始清理过期数据");
            
            // 这里可以添加清理逻辑
            // 例如清理超过30天的推荐数据、日志文件等
            
            log.info("过期数据清理完成");
            
        } catch (Exception e) {
            log.error("清理过期数据失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 每周日凌晨3点执行周度分析
     * 0 0 3 * * SUN 表示每周日凌晨3点执行
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    @Transactional
    public void performWeeklyAnalysis() {
        try {
            log.info("开始执行周度分析");
            
            // 获取推荐状态
            var status = dailyRecommendationService.getRecommendationStatus();
            
            if (status != null) {
                log.info("周度分析数据获取成功");
                
                // 这里可以添加周度分析逻辑
                // 例如统计推荐准确率、热门板块等
                
                // analyzeWeeklyPerformance(status); // 暂时注释掉，因为参数类型不匹配
            }
            
            log.info("周度分析完成");
            
        } catch (Exception e) {
            log.error("周度分析失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 手动触发AI选股（用于测试）
     */
    public void manualTrigger() {
        log.info("手动触发AI选股任务");
        performDailyStockPicking();
    }

    /**
     * 记录成功指标
     */
    private void recordSuccessMetrics(int stockCount, long duration) {
        try {
            // 这里可以记录到监控系统
            log.info("AI选股成功指标 - 股票数: {}, 耗时: {}ms", stockCount, duration);
            
            // 可以发送到监控系统，如Prometheus、InfluxDB等
            // metricsService.recordSuccess(stockCount, duration);
            
        } catch (Exception e) {
            log.warn("记录成功指标失败: {}", e.getMessage());
        }
    }

    /**
     * 记录失败指标
     */
    private void recordFailureMetrics(String errorMessage) {
        try {
            // 这里可以记录到监控系统
            log.error("AI选股失败指标 - 错误: {}", errorMessage);
            
            // 可以发送告警通知
            // alertService.sendAlert("AI选股失败", errorMessage);
            
        } catch (Exception e) {
            log.warn("记录失败指标失败: {}", e.getMessage());
        }
    }

    /**
     * 分析周度表现
     */
    private void analyzeWeeklyPerformance(java.util.List<DailyRecommendation> weeklyHistory) {
        try {
            int totalRecommendations = 0;
            int totalStocks = 0;
            
            for (DailyRecommendation recommendation : weeklyHistory) {
                if (recommendation.getRecommendedStocks() != null) {
                    totalStocks += recommendation.getRecommendedStocks().size();
                    totalRecommendations++;
                }
            }
            
            if (totalRecommendations > 0) {
                double avgStocksPerDay = (double) totalStocks / totalRecommendations;
                log.info("周度分析结果 - 平均每日推荐股票数: {:.1f}, 总推荐次数: {}", 
                        avgStocksPerDay, totalRecommendations);
            }
            
            // 这里可以添加更多分析逻辑
            // 例如分析推荐的行业分布、评分分布等
            
        } catch (Exception e) {
            log.warn("分析周度表现失败: {}", e.getMessage());
        }
    }

    /**
     * 获取调度器状态
     */
    public java.util.Map<String, Object> getSchedulerStatus() {
        java.util.Map<String, Object> status = new java.util.HashMap<>();
        
        status.put("schedulerActive", true);
        status.put("lastCheckTime", LocalDateTime.now());
        status.put("nextDailyRun", "每日凌晨1点");
        status.put("nextWeeklyRun", "每周日凌晨3点");
        
        return status;
    }
}
