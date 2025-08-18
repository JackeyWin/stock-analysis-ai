package com.stockanalysis.service;

import com.stockanalysis.model.DailyRecommendation;
import com.stockanalysis.model.StockRecommendationDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 每日推荐管理服务
 */
@Slf4j
@Service
public class DailyRecommendationService {

    private final AIStockPickerService aiStockPickerService;
    
    // 内存存储（实际项目中应该使用数据库）
    private final Map<String, DailyRecommendation> recommendationCache = new ConcurrentHashMap<>();
    private final Map<String, List<DailyRecommendation>> historyCache = new ConcurrentHashMap<>();

    public DailyRecommendationService(AIStockPickerService aiStockPickerService) {
        this.aiStockPickerService = aiStockPickerService;
    }

    /**
     * 生成每日推荐
     */
    public DailyRecommendation generateDailyRecommendation() {
        try {
            log.info("开始生成每日推荐");
            
            // 调用AI选股服务
            DailyRecommendation recommendation = aiStockPickerService.performAIStockPicking().get();
            
            // 保存推荐
            saveDailyRecommendation(recommendation);
            
            log.info("每日推荐生成完成，推荐{}只股票", 
                    recommendation.getRecommendedStocks() != null ? recommendation.getRecommendedStocks().size() : 0);
            
            return recommendation;
            
        } catch (Exception e) {
            log.error("生成每日推荐失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成每日推荐失败: " + e.getMessage());
        }
    }

    /**
     * 获取今日推荐
     */
    public DailyRecommendation getTodayRecommendation() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        DailyRecommendation recommendation = recommendationCache.get(today);
        
        if (recommendation == null) {
            log.info("今日推荐不存在，生成新的推荐");
            recommendation = generateDailyRecommendation();
        }
        
        return recommendation;
    }

    /**
     * 获取指定日期的推荐
     */
    public DailyRecommendation getRecommendationByDate(String date) {
        return recommendationCache.get(date);
    }

    /**
     * 获取推荐历史
     */
    public List<DailyRecommendation> getRecommendationHistory(int days) {
        List<DailyRecommendation> history = new ArrayList<>();
        
        LocalDate endDate = LocalDate.now();
        for (int i = 0; i < days; i++) {
            LocalDate date = endDate.minusDays(i);
            String dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            DailyRecommendation recommendation = recommendationCache.get(dateStr);
            if (recommendation != null) {
                history.add(recommendation);
            }
        }
        
        return history;
    }

    /**
     * 获取热门推荐股票
     */
    public List<StockRecommendationDetail> getHotRecommendations() {
        DailyRecommendation todayRecommendation = getTodayRecommendation();
        
        if (todayRecommendation == null || todayRecommendation.getRecommendedStocks() == null) {
            return new ArrayList<>();
        }
        
        return todayRecommendation.getRecommendedStocks().stream()
                .filter(stock -> stock.getIsHot() != null && stock.getIsHot())
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(10)
                .collect(Collectors.toList());
    }

    /**
     * 按领域获取推荐
     */
    public List<StockRecommendationDetail> getRecommendationsBySector(String sector) {
        DailyRecommendation todayRecommendation = getTodayRecommendation();
        
        if (todayRecommendation == null || todayRecommendation.getRecommendedStocks() == null) {
            return new ArrayList<>();
        }
        
        return todayRecommendation.getRecommendedStocks().stream()
                .filter(stock -> sector.equals(stock.getSector()))
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .collect(Collectors.toList());
    }

    /**
     * 获取推荐详情
     */
    public StockRecommendationDetail getRecommendationDetail(String stockCode) {
        DailyRecommendation todayRecommendation = getTodayRecommendation();
        
        if (todayRecommendation == null || todayRecommendation.getRecommendedStocks() == null) {
            return null;
        }
        
        return todayRecommendation.getRecommendedStocks().stream()
                .filter(stock -> stockCode.equals(stock.getStockCode()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取推荐统计信息
     */
    public Map<String, Object> getRecommendationStatistics() {
        DailyRecommendation todayRecommendation = getTodayRecommendation();
        Map<String, Object> statistics = new HashMap<>();
        
        if (todayRecommendation == null || todayRecommendation.getRecommendedStocks() == null) {
            statistics.put("totalCount", 0);
            statistics.put("hotCount", 0);
            statistics.put("sectorCount", 0);
            statistics.put("averageScore", 0.0);
            return statistics;
        }
        
        List<StockRecommendationDetail> stocks = todayRecommendation.getRecommendedStocks();
        
        // 总数
        statistics.put("totalCount", stocks.size());
        
        // 热门推荐数
        long hotCount = stocks.stream()
                .filter(stock -> stock.getIsHot() != null && stock.getIsHot())
                .count();
        statistics.put("hotCount", hotCount);
        
        // 领域数
        long sectorCount = stocks.stream()
                .map(StockRecommendationDetail::getSector)
                .distinct()
                .count();
        statistics.put("sectorCount", sectorCount);
        
        // 平均评分
        double averageScore = stocks.stream()
                .mapToDouble(StockRecommendationDetail::getScore)
                .average()
                .orElse(0.0);
        statistics.put("averageScore", Math.round(averageScore * 100.0) / 100.0);
        
        // 按领域统计
        Map<String, Long> sectorStats = stocks.stream()
                .collect(Collectors.groupingBy(StockRecommendationDetail::getSector, Collectors.counting()));
        statistics.put("sectorStats", sectorStats);
        
        // 按风险等级统计
        Map<String, Long> riskStats = stocks.stream()
                .collect(Collectors.groupingBy(StockRecommendationDetail::getRiskLevel, Collectors.counting()));
        statistics.put("riskStats", riskStats);
        
        return statistics;
    }

    /**
     * 刷新推荐
     */
    public DailyRecommendation refreshRecommendation() {
        log.info("手动刷新每日推荐");
        return generateDailyRecommendation();
    }

    /**
     * 保存每日推荐
     */
    private void saveDailyRecommendation(DailyRecommendation recommendation) {
        if (recommendation == null) {
            return;
        }
        
        String date = recommendation.getRecommendationDate();
        
        // 保存到缓存
        recommendationCache.put(date, recommendation);
        
        // 保存到历史记录
        String monthKey = date.substring(0, 7); // yyyy-MM
        historyCache.computeIfAbsent(monthKey, k -> new ArrayList<>()).add(recommendation);
        
        // 清理过期数据（保留30天）
        cleanupExpiredData();
        
        log.info("每日推荐已保存: {}", date);
    }

    /**
     * 清理过期数据
     */
    private void cleanupExpiredData() {
        try {
            LocalDate cutoffDate = LocalDate.now().minusDays(30);
            String cutoffDateStr = cutoffDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            // 清理过期的推荐
            recommendationCache.entrySet().removeIf(entry -> entry.getKey().compareTo(cutoffDateStr) < 0);
            
            // 清理过期的历史记录
            String cutoffMonth = cutoffDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            historyCache.entrySet().removeIf(entry -> entry.getKey().compareTo(cutoffMonth) < 0);
            
            log.debug("清理过期数据完成");
            
        } catch (Exception e) {
            log.warn("清理过期数据失败: {}", e.getMessage());
        }
    }

    /**
     * 获取推荐状态
     */
    public Map<String, Object> getRecommendationStatus() {
        Map<String, Object> status = new HashMap<>();
        
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        DailyRecommendation todayRecommendation = recommendationCache.get(today);
        
        status.put("hasToday", todayRecommendation != null);
        status.put("todayDate", today);
        status.put("cacheSize", recommendationCache.size());
        status.put("lastUpdate", todayRecommendation != null ? todayRecommendation.getCreateTime() : null);
        status.put("status", todayRecommendation != null ? todayRecommendation.getStatus() : "NONE");
        
        return status;
    }

    /**
     * 检查是否需要更新推荐
     */
    public boolean needsUpdate() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        DailyRecommendation todayRecommendation = recommendationCache.get(today);
        
        if (todayRecommendation == null) {
            return true;
        }
        
        // 检查是否是今天生成的
        LocalDateTime createTime = todayRecommendation.getCreateTime();
        if (createTime == null) {
            return true;
        }
        
        LocalDate createDate = createTime.toLocalDate();
        return !createDate.equals(LocalDate.now());
    }

    /**
     * 获取推荐摘要（用于首页展示）
     */
    public Map<String, Object> getRecommendationSummary() {
        DailyRecommendation todayRecommendation = getTodayRecommendation();
        Map<String, Object> summary = new HashMap<>();
        
        if (todayRecommendation == null) {
            summary.put("available", false);
            summary.put("message", "今日推荐暂未生成");
            return summary;
        }
        
        summary.put("available", true);
        summary.put("date", todayRecommendation.getRecommendationDate());
        summary.put("summary", todayRecommendation.getSummary());
        
        // 获取前3只热门推荐
        List<StockRecommendationDetail> topStocks = todayRecommendation.getRecommendedStocks().stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(3)
                .collect(Collectors.toList());
        
        summary.put("topStocks", topStocks);
        summary.put("totalCount", todayRecommendation.getRecommendedStocks().size());
        
        return summary;
    }
}
