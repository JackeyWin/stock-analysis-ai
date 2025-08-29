package com.stockanalysis.controller;

import com.stockanalysis.model.DailyRecommendation;
import com.stockanalysis.model.StockAnalysisRequest;
import com.stockanalysis.model.StockAnalysisResponse;
import com.stockanalysis.model.StockRecommendationDetail;
import com.stockanalysis.entity.DailyRecommendationEntity;
import com.stockanalysis.entity.StockRecommendationDetailEntity;
import com.stockanalysis.entity.StockAnalysisResultEntity;
import com.stockanalysis.service.StockAnalysisService;
import com.stockanalysis.service.SimplifiedStockPickerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import org.springframework.http.HttpStatus;
import java.util.Collections;

/**
 * 股票分析API控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/stocks")
@Validated
@CrossOrigin(origins = "*")
public class StockAnalysisController {

    private final StockAnalysisService stockAnalysisService;
    private final SimplifiedStockPickerService simplifiedStockPickerService;

    public StockAnalysisController(StockAnalysisService stockAnalysisService, SimplifiedStockPickerService simplifiedStockPickerService) {
        this.stockAnalysisService = stockAnalysisService;
        this.simplifiedStockPickerService = simplifiedStockPickerService;
    }

    /**
     * 股票分析接口
     */
    @PostMapping("/analyze")
    public ResponseEntity<StockAnalysisResponse> analyzeStock(@Valid @RequestBody StockAnalysisRequest request) {
        log.info("收到股票分析请求: {}", request.getStockCode());
        
        try {
            // 记录分析开始时间
            log.info("分析开始时间: {}", request.getAnalysisStartTime());
            
            StockAnalysisResponse response = stockAnalysisService.analyzeStock(request);
            
            if (response.isSuccess()) {
                log.info("股票分析成功: {}", request.getStockCode());
                return ResponseEntity.ok(response);
            } else {
                log.warn("股票分析失败: {}, 原因: {}", request.getStockCode(), response.getMessage());
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            log.error("股票分析异常: {}", e.getMessage(), e);
            
            StockAnalysisResponse errorResponse = new StockAnalysisResponse();
            errorResponse.setStockCode(request.getStockCode());
            errorResponse.setSuccess(false);
            errorResponse.setMessage("系统异常: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 简单的股票分析接口（GET方式）
     */
    @GetMapping("/analyze/{stockCode}")
    public ResponseEntity<StockAnalysisResponse> analyzeStockSimple(@PathVariable String stockCode,
                                                                    @RequestParam(defaultValue = "default") String machineId) {
        log.info("收到简单股票分析请求: {}, machineId: {}", stockCode, machineId);
        
        StockAnalysisRequest request = new StockAnalysisRequest();
        request.setStockCode(stockCode);
        request.setMachineId(machineId);
        
        return analyzeStock(request);
    }

    /**
     * 快速分析接口
     */
    @PostMapping("/quick-analyze")
    public ResponseEntity<Map<String, Object>> quickAnalyze(@Valid @RequestBody StockAnalysisRequest request) {
        log.info("收到快速分析请求: {}", request.getStockCode());
        
        try {
            // 只获取技术指标进行快速分析
            Map<String, Object> technicalIndicators = stockAnalysisService.getTechnicalIndicators(request.getStockCode());
            
            // 进行快速AI分析
            var aiResult = stockAnalysisService.quickAnalyze(request.getStockCode(), technicalIndicators);
            
            Map<String, Object> response = Map.of(
                    "stockCode", request.getStockCode(),
                    "success", true,
                    "technicalIndicators", technicalIndicators,
                    "aiAnalysisResult", aiResult
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("快速分析异常: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = Map.of(
                    "stockCode", request.getStockCode(),
                    "success", false,
                    "message", "快速分析失败: " + e.getMessage()
            );
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 风险评估接口
     */
    @PostMapping("/risk-assessment")
    public ResponseEntity<Map<String, Object>> assessRisk(@Valid @RequestBody StockAnalysisRequest request) {
        log.info("收到风险评估请求: {}", request.getStockCode());
        
        try {
            String riskAssessment = stockAnalysisService.assessRisk(request.getStockCode());
            
            Map<String, Object> response = Map.of(
                    "stockCode", request.getStockCode(),
                    "success", true,
                    "riskAssessment", riskAssessment
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("风险评估异常: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = Map.of(
                    "stockCode", request.getStockCode(),
                    "success", false,
                    "message", "风险评估失败: " + e.getMessage()
            );
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    /**
     * 获取最新每日推荐
     */
    @GetMapping("/daily-recommendation/latest")
    public ResponseEntity<DailyRecommendation> getLatestDailyRecommendation() {
        log.info("收到获取最新每日推荐请求");
        
        try {
            Optional<DailyRecommendationEntity> entity = stockAnalysisService.getDailyRecommendationStorageService().getLatestDailyRecommendation();
            if (entity.isPresent()) {
                log.info("成功获取最新每日推荐");
                DailyRecommendation recommendation = convertToModel(entity.get());
                return ResponseEntity.ok(recommendation);
            } else {
                log.warn("未找到每日推荐数据");
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("获取最新每日推荐异常: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 根据日期获取每日推荐
     */
    @GetMapping("/daily-recommendation/{date}")
    public ResponseEntity<DailyRecommendation> getDailyRecommendationByDate(@PathVariable String date) {
        log.info("收到获取指定日期每日推荐请求: {}", date);
        
        try {
            Optional<DailyRecommendationEntity> entity = stockAnalysisService.getDailyRecommendationStorageService().getDailyRecommendationByDate(date);
            if (entity.isPresent()) {
                log.info("成功获取指定日期每日推荐: {}", date);
                DailyRecommendation recommendation = convertToModel(entity.get());
                return ResponseEntity.ok(recommendation);
            } else {
                log.warn("未找到指定日期的每日推荐数据: {}", date);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("获取指定日期每日推荐异常: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 根据股票代码获取推荐详情
     */
    @GetMapping("/recommendation/{stockCode}")
    public ResponseEntity<StockRecommendationDetail> getRecommendationByStockCode(@PathVariable String stockCode) {
        log.info("收到获取股票推荐详情请求: {}", stockCode);
        
        try {
            List<StockRecommendationDetailEntity> recommendations = stockAnalysisService.getDailyRecommendationStorageService().getRecommendationDetailsByStockCode(stockCode);
                StockRecommendationDetail recommendation = recommendations.isEmpty() ? null : convertToModel(recommendations.get(0));
            if (recommendation != null) {
                log.info("成功获取股票推荐详情: {}", stockCode);
                return ResponseEntity.ok(recommendation);
            } else {
                log.warn("未找到指定股票的推荐详情: {}", stockCode);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("获取股票推荐详情异常: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Stock Analysis Service is running");
    }
    
    /**
     * 查询按机器号存储的分析结果
     */
    @GetMapping("/analysis-results/{machineId}")
    public ResponseEntity<List<StockAnalysisResultEntity>> getAnalysisResultsByMachineId(@PathVariable String machineId) {
        log.info("收到查询分析结果请求: machineId: {}", machineId);
        
        try {
            List<StockAnalysisResultEntity> results = stockAnalysisService.getStockAnalysisResultRepository().findByMachineId(machineId);
            log.info("成功查询到 {} 条分析结果", results.size());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("查询分析结果异常: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 简化版选股接口
     */
    @GetMapping("/simplified-pick")
    public ResponseEntity<Map<String, Object>> simplifiedPick() {
        log.info("收到简化版选股请求");
        
        try {
            Map<String, Object> result = simplifiedStockPickerService.pickStocks().get();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("简化版选股异常: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "message", "简化版选股失败: " + e.getMessage()
            );
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 获取政策热点接口
     */
    @GetMapping("/policy-hotspots")
    public ResponseEntity<Map<String, String>> getPolicyHotspots() {
        log.info("收到获取政策热点请求");
        
        try {
            Map<String, String> result = simplifiedStockPickerService.getPolicyHotspotService().getPolicyAndIndustryHotspots().get();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取政策热点异常: {}", e.getMessage(), e);
            
            Map<String, String> errorResponse = Map.of(
                    "success", "false",
                    "message", "获取政策热点失败: " + e.getMessage()
            );
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 获取用户分析任务列表（根据设备指纹）
     */
    @GetMapping("/analysis/tasks")
    public ResponseEntity<List<StockAnalysisResultEntity>> getUserAnalysisTasks(@RequestParam(name = "machineId", required = false) String machineId) {
        log.info("收到获取用户分析任务请求: machineId: {}", machineId);
        
        try {
            // 如果没有提供machineId，返回空列表
            if (machineId == null || machineId.trim().isEmpty()) {
                return ResponseEntity.ok(List.of());
            }
            
            List<StockAnalysisResultEntity> results = stockAnalysisService.getStockAnalysisResultRepository().findByMachineId(machineId);
            log.info("成功查询到 {} 条用户分析任务", results.size());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("获取用户分析任务异常: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取所有分析任务（包括他人的）
     */
    @GetMapping("/analysis/tasks/all")
    public ResponseEntity<Map<String, Object>> getAllAnalysisTasks(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        log.info("收到获取所有分析任务请求: page={}, size={}", page, size);
        
        try {
            // 获取所有分析结果并按分析时间倒序排列
            List<StockAnalysisResultEntity> allResults = stockAnalysisService.getStockAnalysisResultRepository().findAllByOrderByAnalysisTimeDesc();
            
            // 实现简单的分页逻辑
            int total = allResults.size();
            int start = page * size;
            int end = Math.min(start + size, total);
            
            if (start >= total) {
                return ResponseEntity.ok(Map.of(
                    "content", List.of(),
                    "totalElements", total,
                    "totalPages", (int) Math.ceil((double) total / size),
                    "size", size,
                    "number", page,
                    "first", page == 0,
                    "last", end >= total
                ));
            }
            
            List<StockAnalysisResultEntity> pageResults = allResults.subList(start, end);
            
            return ResponseEntity.ok(Map.of(
                "content", pageResults,
                "totalElements", total,
                "totalPages", (int) Math.ceil((double) total / size),
                "size", size,
                "number", page,
                "first", page == 0,
                "last", end >= total
            ));
        } catch (Exception e) {
            log.error("获取所有分析任务异常: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * AI详细分析股票数据并生成策略推荐
     */
        /**
     * 启动AI详细分析（异步）
     */
    @PostMapping("/ai-detailed/{stockCode}/start")
    public ResponseEntity<Map<String, Object>> startAIDetailedAnalysis(@PathVariable String stockCode) {
        try {
            log.info("启动股票 {} 的AI详细分析", stockCode);
            
            // 生成分析任务ID
            String taskId = "ai_analysis_" + stockCode + "_" + System.currentTimeMillis();
            
            // 异步启动分析任务
            stockAnalysisService.startAIDetailedAnalysisAsync(stockCode, taskId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("taskId", taskId);
            response.put("message", "AI详细分析已启动");
            response.put("status", "PROCESSING");
            
            log.info("股票 {} AI详细分析任务已启动，任务ID: {}", stockCode, taskId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("启动股票 {} AI详细分析失败: {}", stockCode, e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "启动AI详细分析失败: " + e.getMessage());
            errorResponse.put("code", "START_ANALYSIS_ERROR");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * 查询AI详细分析状态
     */
    @GetMapping("/ai-detailed/status/{taskId}")
    public ResponseEntity<Map<String, Object>> getAIAnalysisStatus(@PathVariable String taskId) {
        try {
            log.info("查询AI详细分析状态，任务ID: {}", taskId);
            
            Map<String, Object> status = stockAnalysisService.getAIAnalysisStatus(taskId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", status);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("查询AI详细分析状态失败，任务ID: {}: {}", taskId, e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "查询分析状态失败: " + e.getMessage());
            errorResponse.put("code", "STATUS_QUERY_ERROR");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * 获取AI详细分析结果（同步，用于兼容性）
     */
    @GetMapping("/ai-detailed/{stockCode}")
    public ResponseEntity<Map<String, Object>> getAIDetailedAnalysis(@PathVariable String stockCode) {
        try {
            log.info("开始AI详细分析股票: {}", stockCode);

            Map<String, Object> aiAnalysis = stockAnalysisService.generateAIDetailedAnalysis(stockCode);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", aiAnalysis);
            response.put("message", "AI详细分析完成");

            log.info("股票 {} AI详细分析完成", stockCode);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("AI详细分析股票 {} 失败: {}", stockCode, e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "AI详细分析失败: " + e.getMessage());
            errorResponse.put("code", "AI_ANALYSIS_ERROR");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ========== 盘中盯盘：启动/停止/状态 ==========
    @PostMapping("/analysis/monitor/start")
    public ResponseEntity<Map<String, Object>> startMonitoring(
            @RequestParam String stockCode,
            @RequestParam(name = "intervalMinutes") int intervalMinutes,
            @RequestParam(name = "analysisId", required = false) String analysisId,
            @RequestParam(name = "machineId", required = false, defaultValue = "default") String machineId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            String jobId = stockAnalysisService.startIntradayMonitoring(stockCode, intervalMinutes, analysisId, machineId);
            resp.put("success", true);
            resp.put("jobId", jobId);
            resp.put("message", "盯盘已启动");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("启动盯盘失败: {}", e.getMessage(), e);
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.ok(resp);
        }
    }

    @PostMapping("/analysis/monitor/stop")
    public ResponseEntity<Map<String, Object>> stopMonitoring(@RequestParam String jobId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            boolean stopped = stockAnalysisService.stopIntradayMonitoring(jobId);
            resp.put("success", stopped);
            resp.put("message", stopped ? "盯盘已停止" : "任务不存在或已停止");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("停止盯盘失败: {}", e.getMessage(), e);
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.ok(resp);
        }
    }

    @GetMapping("/analysis/monitor/status/{jobId}")
    public ResponseEntity<Map<String, Object>> getMonitoringStatus(@PathVariable String jobId) {
        try {
            Map<String, Object> status = stockAnalysisService.getIntradayMonitoringStatus(jobId);
            return ResponseEntity.ok(Map.of("success", true, "data", status));
        } catch (Exception e) {
            log.error("获取盯盘状态失败: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/analysis/monitor/stock-status/{stockCode}")
    public ResponseEntity<Map<String, Object>> getStockMonitoringStatus(@PathVariable String stockCode) {
        try {
            Map<String, Object> status = stockAnalysisService.getStockMonitoringStatus(stockCode);
            return ResponseEntity.ok(Map.of("success", true, "data", status));
        } catch (Exception e) {
            log.error("获取股票监控状态失败: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/analysis/monitor/cleanup-all")
    public ResponseEntity<Map<String, Object>> cleanupAllMonitoringJobs() {
        try {
            log.info("收到清理所有盯盘任务的请求");
            stockAnalysisService.cleanupAllMonitoringJobs();
            return ResponseEntity.ok(Map.of("success", true, "message", "所有盯盘任务已清理完成"));
        } catch (Exception e) {
            log.error("清理所有盯盘任务失败: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/analysis/monitor/records/today/{stockCode}")
    public ResponseEntity<Map<String, Object>> getTodayMonitoringRecords(@PathVariable String stockCode) {
        try {
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalDateTime start = today.atStartOfDay();
            java.time.LocalDateTime end = today.atTime(23, 59, 59);
            var list = stockAnalysisService.getTodayMonitoringRecords(stockCode, start, end);
            return ResponseEntity.ok(Map.of("success", true, "data", list));
        } catch (Exception e) {
            log.error("获取今日盯盘记录失败: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    /**
     * 将StockRecommendationDetailEntity转换为StockRecommendationDetail模型
     */
    private StockRecommendationDetail convertToModel(com.stockanalysis.entity.StockRecommendationDetailEntity entity) {
        StockRecommendationDetail model = new StockRecommendationDetail();
        model.setStockCode(entity.getStockCode());
        model.setStockName(entity.getStockName());
        model.setSector(entity.getSector());
        model.setRecommendationReason(entity.getRecommendationReason());
        model.setRating(entity.getRating());
        model.setScore(entity.getScore());
        model.setTargetPrice(entity.getTargetPrice());
        model.setCurrentPrice(entity.getCurrentPrice());
        model.setExpectedReturn(entity.getExpectedReturn());
        model.setRiskLevel(entity.getRiskLevel());
        model.setInvestmentPeriod(entity.getInvestmentPeriod());
        model.setTechnicalAnalysis(entity.getTechnicalAnalysis());
        model.setFundamentalAnalysis(entity.getFundamentalAnalysis());
        model.setNewsAnalysis(entity.getNewsAnalysis());
        model.setKeyMetrics(entity.getKeyMetrics());
        model.setRecommendTime(entity.getRecommendTime());
        model.setSortOrder(entity.getSortOrder());
        model.setIsHot(entity.getIsHot());
        return model;
    }
    
    /**
     * 将DailyRecommendationEntity转换为DailyRecommendation模型
     */
    private DailyRecommendation convertToModel(DailyRecommendationEntity entity) {
        DailyRecommendation model = new DailyRecommendation();
        model.setRecommendationId(entity.getRecommendationId());
        model.setRecommendationDate(entity.getRecommendationDate());
        model.setCreateTime(entity.getCreateTime());
        // 使用新的policyHotspotsAndIndustryHotspots字段
        if (entity.getPolicyHotspotsAndIndustryHotspots() != null && !entity.getPolicyHotspotsAndIndustryHotspots().isEmpty()) {
            // 从JSON字符串转换为Map
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                TypeReference<Map<String, String>> typeRef = new TypeReference<Map<String, String>>() {};
                Map<String, String> policyHotspotsAndIndustryHotspots = objectMapper.readValue(
                    entity.getPolicyHotspotsAndIndustryHotspots(), typeRef);
                model.setPolicyHotspotsAndIndustryHotspots(policyHotspotsAndIndustryHotspots);
            } catch (Exception e) {
                log.warn("解析policyHotspotsAndIndustryHotspots失败: {}", e.getMessage());
                // 如果解析失败，使用简化处理
                model.setPolicyHotspotsAndIndustryHotspots(
                    Map.of("data", entity.getPolicyHotspotsAndIndustryHotspots()));
            }
        }
        model.setSummary(entity.getSummary());
        model.setAnalystView(entity.getAnalystView());
        model.setRiskWarning(entity.getRiskWarning());
        model.setStatus(entity.getStatus());
        model.setVersion(entity.getVersion());
        
        // 转换推荐的股票详情
        if (entity.getRecommendedStocks() != null) {
            List<StockRecommendationDetail> details = entity.getRecommendedStocks()
                    .stream()
                    .map(this::convertToModel)
                    .collect(Collectors.toList());
            model.setRecommendedStocks(details);
        }
        
        return model;
    }

  /**
   * 获取所有正在盯盘的任务
   */
  @GetMapping("/monitor/all-jobs")
  public ResponseEntity<List<Map<String, Object>>> getAllMonitoringJobs() {
    try {
      List<Map<String, Object>> jobs = stockAnalysisService.getAllMonitoringJobs();
      return ResponseEntity.ok(jobs);
    } catch (Exception e) {
      log.error("获取所有盯盘任务失败", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Collections.emptyList());
    }
  }
}