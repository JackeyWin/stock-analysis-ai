package com.stockanalysis.controller;

import com.stockanalysis.model.StockAnalysisRequest;
import com.stockanalysis.model.StockAnalysisResponse;
import com.stockanalysis.service.StockAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.Map;

/**
 * 股票分析API控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/stock")
@Validated
@CrossOrigin(origins = "*")
public class StockAnalysisController {

    private final StockAnalysisService stockAnalysisService;

    public StockAnalysisController(StockAnalysisService stockAnalysisService) {
        this.stockAnalysisService = stockAnalysisService;
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
    public ResponseEntity<StockAnalysisResponse> analyzeStockSimple(@PathVariable String stockCode) {
        log.info("收到简单股票分析请求: {}", stockCode);
        
        StockAnalysisRequest request = new StockAnalysisRequest();
        request.setStockCode(stockCode);
        
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
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Stock Analysis Service is running");
    }
}