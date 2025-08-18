package com.stockanalysis.controller;

import com.stockanalysis.model.DailyRecommendation;
import com.stockanalysis.model.StockRecommendationDetail;
import com.stockanalysis.service.DailyRecommendationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 每日推荐API控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/recommendations")
@CrossOrigin(origins = "*")
public class DailyRecommendationController {

    private final DailyRecommendationService dailyRecommendationService;

    public DailyRecommendationController(DailyRecommendationService dailyRecommendationService) {
        this.dailyRecommendationService = dailyRecommendationService;
    }

    /**
     * 获取今日推荐
     */
    @GetMapping("/today")
    public ResponseEntity<Map<String, Object>> getTodayRecommendation() {
        try {
            log.info("获取今日推荐");
            
            DailyRecommendation recommendation = dailyRecommendationService.getTodayRecommendation();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", recommendation);
            response.put("message", "获取今日推荐成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取今日推荐失败: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取今日推荐失败: " + e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 获取推荐摘要（用于首页展示）
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getRecommendationSummary() {
        try {
            log.info("获取推荐摘要");
            
            Map<String, Object> summary = dailyRecommendationService.getRecommendationSummary();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", summary);
            response.put("message", "获取推荐摘要成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取推荐摘要失败: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取推荐摘要失败: " + e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 获取热门推荐
     */
    @GetMapping("/hot")
    public ResponseEntity<Map<String, Object>> getHotRecommendations() {
        try {
            log.info("获取热门推荐");
            
            List<StockRecommendationDetail> hotStocks = dailyRecommendationService.getHotRecommendations();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", hotStocks);
            response.put("message", "获取热门推荐成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取热门推荐失败: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取热门推荐失败: " + e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 按领域获取推荐
     */
    @GetMapping("/sector/{sector}")
    public ResponseEntity<Map<String, Object>> getRecommendationsBySector(@PathVariable String sector) {
        try {
            log.info("获取{}领域推荐", sector);
            
            List<StockRecommendationDetail> sectorStocks = dailyRecommendationService.getRecommendationsBySector(sector);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", sectorStocks);
            response.put("message", "获取" + sector + "领域推荐成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取{}领域推荐失败: {}", sector, e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取" + sector + "领域推荐失败: " + e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 获取推荐详情
     */
    @GetMapping("/detail/{stockCode}")
    public ResponseEntity<Map<String, Object>> getRecommendationDetail(@PathVariable String stockCode) {
        try {
            log.info("获取股票{}推荐详情", stockCode);
            
            StockRecommendationDetail detail = dailyRecommendationService.getRecommendationDetail(stockCode);
            
            Map<String, Object> response = new HashMap<>();
            if (detail != null) {
                response.put("success", true);
                response.put("data", detail);
                response.put("message", "获取推荐详情成功");
            } else {
                response.put("success", false);
                response.put("message", "未找到该股票的推荐信息");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取股票{}推荐详情失败: {}", stockCode, e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取推荐详情失败: " + e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 获取推荐历史
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getRecommendationHistory(
            @RequestParam(defaultValue = "7") int days) {
        try {
            log.info("获取推荐历史，天数: {}", days);
            
            List<DailyRecommendation> history = dailyRecommendationService.getRecommendationHistory(days);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", history);
            response.put("message", "获取推荐历史成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取推荐历史失败: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取推荐历史失败: " + e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 获取推荐统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getRecommendationStatistics() {
        try {
            log.info("获取推荐统计信息");
            
            Map<String, Object> statistics = dailyRecommendationService.getRecommendationStatistics();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", statistics);
            response.put("message", "获取推荐统计信息成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取推荐统计信息失败: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取推荐统计信息失败: " + e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 获取指定日期的推荐
     */
    @GetMapping("/date/{date}")
    public ResponseEntity<Map<String, Object>> getRecommendationByDate(@PathVariable String date) {
        try {
            log.info("获取{}的推荐", date);
            
            DailyRecommendation recommendation = dailyRecommendationService.getRecommendationByDate(date);
            
            Map<String, Object> response = new HashMap<>();
            if (recommendation != null) {
                response.put("success", true);
                response.put("data", recommendation);
                response.put("message", "获取推荐成功");
            } else {
                response.put("success", false);
                response.put("message", "未找到该日期的推荐");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取{}的推荐失败: {}", date, e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取推荐失败: " + e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 手动刷新推荐
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshRecommendation() {
        try {
            log.info("手动刷新推荐");
            
            DailyRecommendation recommendation = dailyRecommendationService.refreshRecommendation();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", recommendation);
            response.put("message", "刷新推荐成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("刷新推荐失败: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "刷新推荐失败: " + e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 获取推荐状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getRecommendationStatus() {
        try {
            log.info("获取推荐状态");
            
            Map<String, Object> status = dailyRecommendationService.getRecommendationStatus();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", status);
            response.put("message", "获取推荐状态成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取推荐状态失败: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取推荐状态失败: " + e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "推荐服务运行正常");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
}
