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
            // 获取所有分析结果
            List<StockAnalysisResultEntity> allResults = stockAnalysisService.getStockAnalysisResultRepository().findAll();
            
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
}