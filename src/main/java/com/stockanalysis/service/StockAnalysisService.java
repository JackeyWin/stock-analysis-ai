package com.stockanalysis.service;

import com.stockanalysis.model.*;
import com.stockanalysis.repository.StockAnalysisResultRepository;
import com.stockanalysis.service.DailyRecommendationStorageService;
import com.stockanalysis.entity.StockAnalysisResultEntity;
import com.stockanalysis.util.RetryTemplate;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.DisposableBean;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.time.Duration;
import java.util.Date;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Qualifier;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * 股票分析服务
 */
@Slf4j
@Service
public class StockAnalysisService implements DisposableBean {

    private final PythonScriptService pythonScriptService;
    private final AIAnalysisService aiAnalysisService;
    private final DailyRecommendationStorageService dailyRecommendationStorageService;
    private final StockAnalysisResultRepository stockAnalysisResultRepository;
    private final com.stockanalysis.repository.StockMonitoringRecordRepository stockMonitoringRecordRepository;
    private final com.stockanalysis.repository.StockMonitoringJobRepository stockMonitoringJobRepository;
    private final StockAnalysisAI stockAnalysisAI;
    private final ExecutorService executorService;
    private final ChatLanguageModel deepseekChatModel;
    
    // 缓存相关
    private final Map<String, CacheEntry<List<Map<String, Object>>>> stockDataCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<Map<String, Object>>> technicalIndicatorsCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<Map<String, Object>>>> newsDataCache = new ConcurrentHashMap<>();
    
    // AI分析任务状态管理
    private final Map<String, AIAnalysisTask> aiAnalysisTasks = new ConcurrentHashMap<>();
    private final Map<String, MonitoringJob> monitoringJobs = new ConcurrentHashMap<>();
    
    // 缓存过期时间配置
    private static final Duration STOCK_DATA_CACHE_DURATION = Duration.ofMinutes(5);
    private static final Duration TECHNICAL_INDICATORS_CACHE_DURATION = Duration.ofMinutes(3);
    private static final Duration NEWS_DATA_CACHE_DURATION = Duration.ofMinutes(10);
    
    // 性能监控相关
    private final Map<String, Long> performanceMetrics = new ConcurrentHashMap<>();
    
    /**
     * 缓存条目类
     */
    private static class CacheEntry<T> {
        private final T data;
        private final LocalDateTime timestamp;
        
        public CacheEntry(T data) {
            this.data = data;
            this.timestamp = LocalDateTime.now();
        }
        
        public T getData() {
            return data;
        }
        
        public boolean isExpired(Duration duration) {
            return LocalDateTime.now().isAfter(timestamp.plus(duration));
        }
    }

    public StockAnalysisService(PythonScriptService pythonScriptService, 
                               AIAnalysisService aiAnalysisService,
                               DailyRecommendationStorageService dailyRecommendationStorageService,
                               StockAnalysisResultRepository stockAnalysisResultRepository,
                               StockAnalysisAI stockAnalysisAI,
                               @Qualifier("deepseekChatModel") ChatLanguageModel deepseekChatModel,
                               com.stockanalysis.repository.StockMonitoringRecordRepository stockMonitoringRecordRepository,
                               com.stockanalysis.repository.StockMonitoringJobRepository stockMonitoringJobRepository) {
        this.pythonScriptService = pythonScriptService;
        this.aiAnalysisService = aiAnalysisService;
        this.dailyRecommendationStorageService = dailyRecommendationStorageService;
        this.stockAnalysisResultRepository = stockAnalysisResultRepository;
        this.stockAnalysisAI = stockAnalysisAI;
        this.executorService = Executors.newFixedThreadPool(6);
        this.deepseekChatModel = deepseekChatModel;
        this.stockMonitoringRecordRepository = stockMonitoringRecordRepository;
        this.stockMonitoringJobRepository = stockMonitoringJobRepository;
    }
    
    public DailyRecommendationStorageService getDailyRecommendationStorageService() {
        return dailyRecommendationStorageService;
    }
    
    public StockAnalysisResultRepository getStockAnalysisResultRepository() {
        return stockAnalysisResultRepository;
    }

    /**
     * 执行完整的股票分析
     */
    public StockAnalysisResponse analyzeStock(StockAnalysisRequest request) {
        String stockCode = request.getStockCode();
        long startTime = System.currentTimeMillis();
        log.info("开始分析股票: {}", stockCode);

        StockAnalysisResponse response = new StockAnalysisResponse();
        response.setStockCode(stockCode);

        try {
            // 记录各阶段开始时间
            long dataFetchStartTime = System.currentTimeMillis();
            // 清理过期缓存
            cleanExpiredCache();
            
            // 并行获取各种数据（使用缓存优化）
            CompletableFuture<List<Map<String, Object>>> stockDataFuture = 
                CompletableFuture.supplyAsync(() -> getCachedStockData(stockCode), executorService);
            
            CompletableFuture<List<Map<String, Object>>> marketDataFuture = 
                CompletableFuture.supplyAsync(() -> pythonScriptService.getMarketKlineData(stockCode), executorService);
            
            CompletableFuture<List<Map<String, Object>>> boardDataFuture = 
                CompletableFuture.supplyAsync(() -> pythonScriptService.getBoardKlineData(stockCode), executorService);
            
            CompletableFuture<List<Map<String, Object>>> newsDataFuture = 
                CompletableFuture.supplyAsync(() -> getCachedNewsData(stockCode), executorService);
            
            CompletableFuture<List<Map<String, Object>>> moneyFlowDataFuture =
                CompletableFuture.supplyAsync(() -> pythonScriptService.getMoneyFlowData(stockCode), executorService);
            
            CompletableFuture<List<Map<String, Object>>> marginTradingDataFuture =
                CompletableFuture.supplyAsync(() -> pythonScriptService.getMarginTradingData(stockCode), executorService);
            
            CompletableFuture<Map<String, Object>> intradayAnalysisFuture =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return pythonScriptService.getIntradayAnalysis(stockCode);
                    } catch (Exception e) {
                        log.warn("获取股票 {} 分时数据分析失败: {}", stockCode, e.getMessage(), e);
                        // 返回一个包含错误信息的Map，而不是抛出异常导致整个分析失败
                        Map<String, Object> errorResult = new HashMap<>();
                        errorResult.put("error", "分时数据分析失败: " + e.getMessage());
                        return errorResult;
                    }
                }, executorService);
            
            CompletableFuture<Map<String, Object>> peerComparisonFuture =
                CompletableFuture.supplyAsync(() -> RetryTemplate.executeWithRetry(() -> 
                    pythonScriptService.getPeerComparisonData(stockCode),
                    3, 1000), executorService);
            
            CompletableFuture<Map<String, Object>> financialAnalysisFuture =
                CompletableFuture.supplyAsync(() -> pythonScriptService.getFinancialAnalysisData(stockCode), executorService);

            CompletableFuture<Map<String, Object>> coreTagsFuture =
                CompletableFuture.supplyAsync(() -> pythonScriptService.getCoreTagsData(stockCode), executorService);

            // 注释：原本的 allDataFuture 已被后续的 allDataFuture2 替代

            // 在数据获取的同时，准备技术指标计算的异步任务（使用高效方式）
            CompletableFuture<Map<String, Object>> technicalIndicatorsFuture = 
                CompletableFuture.supplyAsync(() -> {
                    log.debug("开始计算股票技术指标");
                    return pythonScriptService.calculateTechnicalIndicatorsDirect(stockCode);
                }, executorService);

            CompletableFuture<Map<String, Object>> marketTechnicalIndicatorsFuture = 
                CompletableFuture.supplyAsync(() -> {
                    log.debug("开始计算大盘技术指标");
                    return pythonScriptService.calculateMarketTechnicalIndicatorsDirect(stockCode);
                }, executorService);

            CompletableFuture<Map<String, Object>> boardTechnicalIndicatorsFuture = 
                CompletableFuture.supplyAsync(() -> {
                    log.debug("开始计算板块技术指标");
                    return pythonScriptService.calculateBoardTechnicalIndicatorsDirect(stockCode);
                }, executorService);

            // 准备AI分析的流式处理 - 当核心数据就绪时就开始分析
            CompletableFuture<AIAnalysisResult> aiAnalysisFuture = CompletableFuture.allOf(
                stockDataFuture, technicalIndicatorsFuture, marketTechnicalIndicatorsFuture, 
                boardTechnicalIndicatorsFuture, newsDataFuture, moneyFlowDataFuture, 
                marginTradingDataFuture, intradayAnalysisFuture, peerComparisonFuture, 
                financialAnalysisFuture, coreTagsFuture
            ).thenApplyAsync(ignored -> {
                try {
                    log.info("核心数据就绪，开始AI分析");
                    
                    // 获取核心分析所需的数据
                    List<Map<String, Object>> stockData = stockDataFuture.get();
                    Map<String, Object> technicalIndicators = technicalIndicatorsFuture.get();
                    Map<String, Object> marketTechnicalIndicators = marketTechnicalIndicatorsFuture.get();
                    Map<String, Object> boardTechnicalIndicators = boardTechnicalIndicatorsFuture.get();
                    List<Map<String, Object>> newsData = newsDataFuture.get();
                    List<Map<String, Object>> moneyFlowData = moneyFlowDataFuture.get();
                    List<Map<String, Object>> marginTradingData = marginTradingDataFuture.get();
                    Map<String, Object> intradayAnalysis = intradayAnalysisFuture.get();
                    Map<String, Object> peerComparison = peerComparisonFuture.get();
                    Map<String, Object> financialAnalysis = financialAnalysisFuture.get();
                    Map<String, Object> coreTags = coreTagsFuture.get();
                    
                    // 将核心概念和行业标签信息合并到财务分析数据中
                    if (coreTags != null && !coreTags.isEmpty()) {
                        if (financialAnalysis == null) {
                            financialAnalysis = new HashMap<>();
                        }
                        financialAnalysis.putAll(coreTags);
                    }

                    // 进行AI分析
                    return aiAnalysisService.analyzeStock(
                            stockCode, stockData, marketTechnicalIndicators, boardTechnicalIndicators,
                            technicalIndicators, newsData, moneyFlowData, marginTradingData, 
                            intradayAnalysis, peerComparison, financialAnalysis);
                } catch (Exception e) {
                    log.error("AI分析过程中发生错误: {}", e.getMessage(), e);
                    throw new RuntimeException("AI分析失败", e);
                }
            }, executorService);

            // 等待所有数据获取完成（用于响应组装）
            CompletableFuture<Void> allDataFuture2 = CompletableFuture.allOf(
                stockDataFuture, marketDataFuture, boardDataFuture, newsDataFuture,
                moneyFlowDataFuture, marginTradingDataFuture, intradayAnalysisFuture,
                peerComparisonFuture, financialAnalysisFuture, coreTagsFuture,
                technicalIndicatorsFuture, marketTechnicalIndicatorsFuture, boardTechnicalIndicatorsFuture
            );

            // 等待所有任务完成（包括AI分析）
            CompletableFuture.allOf(allDataFuture2, aiAnalysisFuture).get();
            
            long dataFetchEndTime = System.currentTimeMillis();
            long dataFetchDuration = dataFetchEndTime - dataFetchStartTime;
            recordPerformanceMetric("data_fetch_duration_ms", dataFetchDuration);
            log.info("数据获取和处理完成，耗时: {}ms", dataFetchDuration);

            // 获取所有结果
            List<Map<String, Object>> stockData = stockDataFuture.get();
            List<Map<String, Object>> marketData = marketDataFuture.get();
            List<Map<String, Object>> boardData = boardDataFuture.get();
            List<Map<String, Object>> newsData = newsDataFuture.get();
            List<Map<String, Object>> moneyFlowData = moneyFlowDataFuture.get();
            List<Map<String, Object>> marginTradingData = marginTradingDataFuture.get();
            Map<String, Object> intradayAnalysis = intradayAnalysisFuture.get();
            Map<String, Object> peerComparison = peerComparisonFuture.get();
            Map<String, Object> financialAnalysis = financialAnalysisFuture.get();
            // coreTags 已在 AI 分析中使用，无需重复获取

            // 获取技术指标计算结果
            Map<String, Object> technicalIndicators = technicalIndicatorsFuture.get();
            // marketTechnicalIndicators 和 boardTechnicalIndicators 已在 AI 分析中使用，无需重复获取
            
            // 获取AI分析结果
            AIAnalysisResult aiAnalysisResult = aiAnalysisFuture.get();

            log.info("所有数据获取、技术指标计算和AI分析完成");

            // 组装响应数据
            response.setStockData(convertToStockDataList(stockData));
            response.setMarketData(convertToStockDataList(marketData));
            response.setBoardData(convertToStockDataList(boardData));
            response.setTechnicalIndicators(convertToTechnicalIndicators(technicalIndicators));
            response.setNewsData(convertToNewsDataList(newsData));
            response.setMoneyFlowData(convertToMoneyFlowData(moneyFlowData));
            response.setMarginTradingData(convertToMarginTradingData(marginTradingData));
            response.setPeerComparison(peerComparison);
            response.setFinancialAnalysis(financialAnalysis);
            response.setAiAnalysisResult(aiAnalysisResult);

            // 从分时分析的结果中带回 stockBasic（Python 已整合），并设置 stockName
            if (intradayAnalysis != null && intradayAnalysis.containsKey("stockBasic")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stockBasic = (Map<String, Object>) intradayAnalysis.get("stockBasic");
                response.setStockBasic(stockBasic);
                
                // 多重保障：确保股票名称被正确设置
                if (stockBasic != null) {
                    // 尝试从多个可能的字段获取股票名称
                    String stockName = null;
                    if (stockBasic.get("stockName") instanceof String) {
                        stockName = (String) stockBasic.get("stockName");
                    } else if (stockBasic.get("name") instanceof String) {
                        stockName = (String) stockBasic.get("name");
                    } else if (stockBasic.get("companyName") instanceof String) {
                        stockName = (String) stockBasic.get("companyName");
                    }
                    
                    if (stockName != null && !stockName.trim().isEmpty()) {
                        response.setStockName(stockName);
                        log.debug("成功设置股票名称: {} -> {}", stockCode, stockName);
                    } else {
                        log.warn("股票{}的基础数据中未找到有效的股票名称", stockCode);
                    }
                }
            }
            
            // 如果还没有设置股票名称，尝试从其他数据源获取
            if (response.getStockName() == null || response.getStockName().trim().isEmpty()) {
                // 尝试从技术指标数据中获取
                if (technicalIndicators != null && technicalIndicators.containsKey("stockBasic")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> techStockBasic = (Map<String, Object>) technicalIndicators.get("stockBasic");
                    if (techStockBasic != null) {
                        String stockName = extractStockName(techStockBasic);
                        if (stockName != null && !stockName.trim().isEmpty()) {
                            response.setStockName(stockName);
                            log.debug("从技术指标数据中获取股票名称: {} -> {}", stockCode, stockName);
                        }
                    }
                }
                
                // 如果还是没有，尝试从AI分析结果中获取
                if ((response.getStockName() == null || response.getStockName().trim().isEmpty()) && 
                    aiAnalysisResult != null) {
                    String stockName = aiAnalysisResult.getStockName();
                    if (stockName != null && !stockName.trim().isEmpty()) {
                        response.setStockName(stockName);
                        log.debug("从AI分析结果中获取股票名称: {} -> {}", stockCode, stockName);
                    }
                }
                
                // 如果还是没有，设置一个默认名称
                if (response.getStockName() == null || response.getStockName().trim().isEmpty()) {
                    String defaultName = "股票" + stockCode;
                    response.setStockName(defaultName);
                    log.warn("使用默认股票名称: {} -> {}", stockCode, defaultName);
                }
            }

            long totalDuration = System.currentTimeMillis() - startTime;
            recordPerformanceMetric("total_analysis_duration_ms", totalDuration);
            recordPerformanceMetric("cache_hit_count", getCacheHitCount());
            
            log.info("股票分析完成: {}，总耗时: {}ms", stockCode, totalDuration);
            logPerformanceMetrics(stockCode);
            
            // 保存分析结果到数据库
            saveAnalysisResult(request, response, aiAnalysisResult);

        } catch (Exception e) {
            long totalDuration = System.currentTimeMillis() - startTime;
            recordPerformanceMetric("failed_analysis_duration_ms", totalDuration);
            log.error("股票分析失败: {}，耗时: {}ms，错误: {}", stockCode, totalDuration, e.getMessage(), e);
            response.setSuccess(false);
            response.setMessage("分析失败: " + e.getMessage());
        }

        return response;
    }
    
    /**
     * 获取缓存的股票数据，如果缓存过期或不存在则重新获取
     */
    private List<Map<String, Object>> getCachedStockData(String stockCode) {
        String cacheKey = "stock_" + stockCode;
        CacheEntry<List<Map<String, Object>>> cached = stockDataCache.get(cacheKey);
        
        if (cached != null && !cached.isExpired(STOCK_DATA_CACHE_DURATION)) {
            log.debug("使用缓存的股票数据: {}", stockCode);
            recordCacheHit();
            return cached.getData();
        }
        
        log.debug("获取新的股票数据: {}", stockCode);
        List<Map<String, Object>> data = pythonScriptService.getStockKlineData(stockCode);
        stockDataCache.put(cacheKey, new CacheEntry<>(data));
        return data;
    }
    
    /**
     * 获取缓存的技术指标，如果缓存过期或不存在则重新计算
     */
    private Map<String, Object> getCachedTechnicalIndicators(String stockCode, List<Map<String, Object>> stockData) {
        String cacheKey = "tech_" + stockCode;
        CacheEntry<Map<String, Object>> cached = technicalIndicatorsCache.get(cacheKey);
        
        if (cached != null && !cached.isExpired(TECHNICAL_INDICATORS_CACHE_DURATION)) {
            log.debug("使用缓存的技术指标: {}", stockCode);
            recordCacheHit();
            return cached.getData();
        }
        
        log.debug("计算新的技术指标: {}", stockCode);
        Map<String, Object> indicators = pythonScriptService.calculateTechnicalIndicators(stockData);
        technicalIndicatorsCache.put(cacheKey, new CacheEntry<>(indicators));
        return indicators;
    }
    
    /**
     * 获取缓存的新闻数据，如果缓存过期或不存在则重新获取
     */
    private List<Map<String, Object>> getCachedNewsData(String stockCode) {
        String cacheKey = "news_" + stockCode;
        CacheEntry<List<Map<String, Object>>> cached = newsDataCache.get(cacheKey);
        
        if (cached != null && !cached.isExpired(NEWS_DATA_CACHE_DURATION)) {
            log.debug("使用缓存的新闻数据: {}", stockCode);
            recordCacheHit();
            return cached.getData();
        }
        
        log.debug("获取新的新闻数据: {}", stockCode);
        List<Map<String, Object>> data = pythonScriptService.getNewsData(stockCode);
        newsDataCache.put(cacheKey, new CacheEntry<>(data));
        return data;
    }
    
    /**
     * 清理过期的缓存条目
     */
    private void cleanExpiredCache() {
        stockDataCache.entrySet().removeIf(entry -> 
            entry.getValue().isExpired(STOCK_DATA_CACHE_DURATION));
        technicalIndicatorsCache.entrySet().removeIf(entry -> 
            entry.getValue().isExpired(TECHNICAL_INDICATORS_CACHE_DURATION));
        newsDataCache.entrySet().removeIf(entry -> 
            entry.getValue().isExpired(NEWS_DATA_CACHE_DURATION));
    }
    
    /**
     * 记录性能指标
     */
    private void recordPerformanceMetric(String metricName, long value) {
        performanceMetrics.put(metricName, value);
    }
    
    /**
     * 获取缓存命中次数
     */
    private long getCacheHitCount() {
        return performanceMetrics.getOrDefault("cache_hit_count", 0L);
    }
    
    /**
     * 记录缓存命中
     */
    private void recordCacheHit() {
        performanceMetrics.merge("cache_hit_count", 1L, Long::sum);
    }
    
    /**
     * 输出性能指标日志
     */
    private void logPerformanceMetrics(String stockCode) {
        StringBuilder metricsLog = new StringBuilder();
        metricsLog.append("股票 ").append(stockCode).append(" 性能指标: ");
        
        performanceMetrics.forEach((key, value) -> {
            metricsLog.append(key).append("=").append(value).append("ms, ");
        });
        
        // 计算缓存命中率
        long cacheHits = performanceMetrics.getOrDefault("cache_hit_count", 0L);
        long totalCacheRequests = cacheHits + 3; // 假设有3个主要缓存请求
        double hitRate = totalCacheRequests > 0 ? (double) cacheHits / totalCacheRequests * 100 : 0;
        metricsLog.append("缓存命中率=").append(String.format("%.1f%%", hitRate));
        
        log.info(metricsLog.toString());
        
        // 清理当前分析的性能指标
        performanceMetrics.clear();
    }

    /**
     * 获取技术指标
     */
    public Map<String, Object> getTechnicalIndicators(String stockCode) {
        return pythonScriptService.calculateTechnicalIndicatorsDirect(stockCode);
    }

    /**
     * 快速分析
     */
    public AIAnalysisResult quickAnalyze(String stockCode, Map<String, Object> technicalIndicators) {
        return aiAnalysisService.quickAnalyze(stockCode, technicalIndicators);
    }

    /**
     * 风险评估
     */
    public String assessRisk(String stockCode) {
        try {
            // 获取必要数据
            Map<String, Object> technicalIndicators = pythonScriptService.calculateTechnicalIndicatorsDirect(stockCode);
            List<Map<String, Object>> moneyFlowData = pythonScriptService.getMoneyFlowData(stockCode);
            List<Map<String, Object>> marginTradingData = pythonScriptService.getMarginTradingData(stockCode);

            return aiAnalysisService.assessRisk(stockCode, technicalIndicators, moneyFlowData, marginTradingData);
        } catch (Exception e) {
            log.error("风险评估失败: {}", e.getMessage(), e);
            throw new RuntimeException("风险评估失败: " + e.getMessage());
        }
    }

    /**
     * 获取股票详细分析数据
     * 包含技术指标、资金流向、AI分析等全方位数据
     */
    public Map<String, Object> getDetailedStockAnalysis(String stockCode) {
        Map<String, Object> detailedAnalysis = new HashMap<>();
        
        try {
            log.info("开始获取股票 {} 的详细分析数据", stockCode);
            
            // 1. 获取股票基础数据
            try {
                Map<String, Object> basicData = pythonScriptService.getStockBasicData(stockCode);
                if (basicData != null && !basicData.isEmpty()) {
                    detailedAnalysis.put("stockBasic", basicData);
                    log.debug("股票 {} 基础数据获取成功", stockCode);
                }
            } catch (Exception e) {
                log.warn("获取股票 {} 基础数据失败: {}", stockCode, e.getMessage());
            }
            
            // 2. 获取技术指标数据
            try {
                Map<String, Object> technicalData = getTechnicalIndicators(stockCode);
                if (technicalData != null && !technicalData.isEmpty()) {
                    detailedAnalysis.put("technicalIndicators", technicalData);
                    log.debug("股票 {} 技术指标获取成功", stockCode);
                }
            } catch (Exception e) {
                log.warn("获取股票 {} 技术指标失败: {}", stockCode, e.getMessage());
            }
            
            // 3. 获取资金流向数据
            try {
                List<Map<String, Object>> moneyFlowData = pythonScriptService.getMoneyFlowData(stockCode);
                if (moneyFlowData != null && !moneyFlowData.isEmpty()) {
                    detailedAnalysis.put("moneyFlowData", moneyFlowData);
                    log.debug("股票 {} 资金流向数据获取成功", stockCode);
                }
            } catch (Exception e) {
                log.warn("获取股票 {} 资金流向数据失败: {}", stockCode, e.getMessage());
            }
            
            // 4. 获取融资融券数据
            try {
                List<Map<String, Object>> marginData = pythonScriptService.getMarginTradingData(stockCode);
                if (marginData != null && !marginData.isEmpty()) {
                    detailedAnalysis.put("marginTradingData", marginData);
                    log.debug("股票 {} 融资融券数据获取成功", stockCode);
                }
            } catch (Exception e) {
                log.warn("获取股票 {} 融资融券数据失败: {}", stockCode, e.getMessage());
            }
            
            // 5. 获取同行比较数据
            try {
                Map<String, Object> peerData = pythonScriptService.getPeerComparisonData(stockCode);
                if (peerData != null && !peerData.isEmpty()) {
                    detailedAnalysis.put("peerComparison", peerData);
                    log.debug("股票 {} 同行比较数据获取成功", stockCode);
                }
            } catch (Exception e) {
                log.warn("获取股票 {} 同行比较数据失败: {}", stockCode, e.getMessage());
            }
            
            // 7. 添加分析时间戳
            detailedAnalysis.put("analysisTimestamp", new Date().toString());
            detailedAnalysis.put("stockCode", stockCode);
            
            log.info("股票 {} 详细分析数据获取完成，包含 {} 个数据模块", stockCode, detailedAnalysis.size() - 2); // 减去stockCode和timestamp
            return detailedAnalysis;
            
        } catch (Exception e) {
            log.error("获取股票 {} 详细分析数据时发生异常: {}", stockCode, e.getMessage(), e);
            throw new RuntimeException("获取详细分析数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 转换为StockData列表
     */
    private List<StockData> convertToStockDataList(List<Map<String, Object>> rawData) {
        return rawData.stream()
                .map(this::convertToStockData)
                .toList();
    }

    /**
     * 转换为StockData对象
     */
    private StockData convertToStockData(Map<String, Object> rawData) {
        StockData stockData = new StockData();
        stockData.setDate((String) rawData.get("d"));
        stockData.setOpen(((Number) rawData.get("o")).doubleValue());
        stockData.setClose(((Number) rawData.get("c")).doubleValue());
        stockData.setHigh(((Number) rawData.get("h")).doubleValue());
        stockData.setLow(((Number) rawData.get("l")).doubleValue());
        stockData.setVolume((String) rawData.get("v"));
        stockData.setTurnover((String) rawData.get("tu"));
        return stockData;
    }

    /**
     * 转换为TechnicalIndicators对象
     */
    private TechnicalIndicators convertToTechnicalIndicators(Map<String, Object> rawData) {
        TechnicalIndicators indicators = new TechnicalIndicators();

        try {
            // 处理新的技术指标数据格式
            if (rawData == null) {
                log.warn("技术指标数据为空");
                return indicators;
            }

            // 检查是否是新的数据格式（包含moving_averages等字段）
            if (rawData.containsKey("moving_averages") || rawData.containsKey("rsi")) {
                // 新的数据格式
                log.debug("使用新的技术指标数据格式");
                
                // 设置移动平均线
                @SuppressWarnings("unchecked")
                Map<String, Object> movingAverages = (Map<String, Object>) rawData.get("moving_averages");
                if (movingAverages != null) {
                    indicators.setMa5(getDoubleValue(movingAverages.get("MA5")));
                    indicators.setMa10(getDoubleValue(movingAverages.get("MA10")));
                    indicators.setMa20(getDoubleValue(movingAverages.get("MA20")));
                    indicators.setMa30(getDoubleValue(movingAverages.get("MA30")));
                    indicators.setMa60(getDoubleValue(movingAverages.get("MA60")));
                }

                // 设置布林带
                @SuppressWarnings("unchecked")
                Map<String, Object> bollingerBands = (Map<String, Object>) rawData.get("bollinger_bands");
                if (bollingerBands != null) {
                    indicators.setBollingerUpper(getDoubleValue(bollingerBands.get("upper_band")));
                    indicators.setBollingerMiddle(getDoubleValue(bollingerBands.get("middle_band")));
                    indicators.setBollingerLower(getDoubleValue(bollingerBands.get("lower_band")));
                }

                // 设置RSI
                indicators.setRsi(getDoubleValue(rawData.get("rsi")));

                // 设置MACD
                @SuppressWarnings("unchecked")
                Map<String, Object> macd = (Map<String, Object>) rawData.get("macd");
                if (macd != null) {
                    indicators.setMacd(getDoubleValue(macd.get("macd")));
                    indicators.setMacdSignal(getDoubleValue(macd.get("signal")));
                    indicators.setMacdHistogram(getDoubleValue(macd.get("histogram")));
                }

                // 设置KDJ
                @SuppressWarnings("unchecked")
                Map<String, Object> kdj = (Map<String, Object>) rawData.get("kdj");
                if (kdj != null) {
                    indicators.setKdjK(getDoubleValue(kdj.get("k")));
                    indicators.setKdjD(getDoubleValue(kdj.get("d")));
                    indicators.setKdjJ(getDoubleValue(kdj.get("j")));
                }

                // 设置成交量指标
                @SuppressWarnings("unchecked")
                Map<String, Object> volumeIndicators = (Map<String, Object>) rawData.get("volume_indicators");
                if (volumeIndicators != null) {
                    indicators.setVolumeMa(getDoubleValue(volumeIndicators.get("volume_ma")));
                    indicators.setVolumeRatio(getDoubleValue(volumeIndicators.get("volume_ratio")));
                }

                // 设置支撑阻力位
                @SuppressWarnings("unchecked")
                Map<String, Object> supportResistance = (Map<String, Object>) rawData.get("support_resistance");
                if (supportResistance != null) {
                    indicators.setSupport(getDoubleValue(supportResistance.get("support")));
                    indicators.setResistance(getDoubleValue(supportResistance.get("resistance")));
                    indicators.setCurrentPrice(getDoubleValue(supportResistance.get("current_price")));
                }

                log.debug("成功转换新的技术指标数据格式");
                
            } else {
                // 旧的数据格式（兼容性处理）
                log.debug("使用旧的技术指标数据格式");
            
            // 提取核心指标
            @SuppressWarnings("unchecked")
            Map<String, Object> coreIndicators = (Map<String, Object>) rawData.get("核心指标");
            
            if (coreIndicators != null) {
                // 注意：这些数据主要用于AI分析，不直接映射到DailyIndicators
                // DailyIndicators的数据来源于"近5日指标"
            }
            
            // 提取"近5日指标"数据
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> recent5DaysIndicators = (List<Map<String, Object>>) rawData.get("近5日指标");
            
            if (recent5DaysIndicators != null && !recent5DaysIndicators.isEmpty()) {
                extractFromRecentDaysData(indicators, recent5DaysIndicators);
            } else {
                log.warn("未找到近5日指标数据，技术指标将为空");
                }
            }
            
        } catch (Exception e) {
            log.error("转换技术指标数据失败: {}", e.getMessage(), e);
        }
        
        return indicators;
    }

    /**
     * 安全地获取Double值
     */
    private Double getDoubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 转换为NewsData列表
     */
    private List<NewsData> convertToNewsDataList(List<Map<String, Object>> rawData) {
        return rawData.stream()
                .map(this::convertToNewsData)
                .toList();
    }

    /**
     * 转换为NewsData对象
     */
    private NewsData convertToNewsData(Map<String, Object> rawData) {
        NewsData newsData = new NewsData();
        
        // 基础信息（去掉新闻摘要，以AI情感为主）
        newsData.setTitle((String) rawData.get("新闻标题"));
        newsData.setContent((String) rawData.get("新闻内容"));
        // 不再填充摘要
        newsData.setSummary(null);
        newsData.setSource((String) rawData.get("来源媒体"));
        newsData.setUrl((String) rawData.get("原文链接"));
        
        // 发布时间处理
        String publishDateStr = (String) rawData.get("发布日期");
        if (publishDateStr != null && !publishDateStr.isEmpty()) {
            try {
                LocalDateTime publishTime;
                if (publishDateStr.length() == 10) {
                    // 格式: yyyy-MM-dd
                    LocalDate date = LocalDate.parse(publishDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    publishTime = date.atStartOfDay(); // 设置为当天的00:00:00
                } else if (publishDateStr.length() == 19) {
                    // 格式: yyyy-MM-dd HH:mm:ss
                    publishTime = LocalDateTime.parse(publishDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                } else {
                    // 其他格式，尝试解析为日期
                    LocalDate date = LocalDate.parse(publishDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    publishTime = date.atStartOfDay();
                }
                newsData.setPublishTime(publishTime);
            } catch (Exception e) {
                log.warn("无法解析发布日期: {}", publishDateStr);
            }
        }
        
        // 情感分析信息
        newsData.setSentiment((String) rawData.get("情感标签"));
        
        Object sentimentScoreObj = rawData.get("情感评分");
        if (sentimentScoreObj instanceof Number) {
            newsData.setSentimentScore(((Number) sentimentScoreObj).doubleValue());
        }
        
        // 关键词列表
        @SuppressWarnings("unchecked")
        List<String> positiveKeywords = (List<String>) rawData.get("利好关键词");
        if (positiveKeywords != null) {
            newsData.setPositiveKeywords(positiveKeywords);
        }
        
        @SuppressWarnings("unchecked")
        List<String> negativeKeywords = (List<String>) rawData.get("利空关键词");
        if (negativeKeywords != null) {
            newsData.setNegativeKeywords(negativeKeywords);
        }
        
        newsData.setAnalysisSummary((String) rawData.get("分析摘要"));
        
        return newsData;
    }

    /**
     * 转换为MoneyFlowData对象
     */
    private MoneyFlowData convertToMoneyFlowData(List<Map<String, Object>> rawData) {
        MoneyFlowData moneyFlowData = new MoneyFlowData();

        try {
            if (rawData != null && !rawData.isEmpty()) {
                // 从第一个元素中提取数据（基于您提供的数据格式）
                Map<String, Object> stockData = rawData.get(0);
                
                // 提取资金数据
                @SuppressWarnings("unchecked")
                Map<String, Object> fundData = (Map<String, Object>) stockData.get("资金数据");
                
                if (fundData != null) {
                    List<MoneyFlowData.DailyMoneyFlow> dailyFlows = new java.util.ArrayList<>();
                    
                    // 处理今日数据
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> todayData = (List<Map<String, Object>>) fundData.get("今日");
                    if (todayData != null) {
                        MoneyFlowData.DailyMoneyFlow todayFlow = createDailyMoneyFlow("今日", todayData);
                        if (todayFlow != null) {
                            dailyFlows.add(todayFlow);
                        }
                    }
                    
                    // 处理5日数据
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> fiveDayData = (List<Map<String, Object>>) fundData.get("5日");
                    if (fiveDayData != null) {
                        MoneyFlowData.DailyMoneyFlow fiveDayFlow = createDailyMoneyFlow("5日", fiveDayData);
                        if (fiveDayFlow != null) {
                            dailyFlows.add(fiveDayFlow);
                        }
                    }
                    
                    // 处理10日数据
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> tenDayData = (List<Map<String, Object>>) fundData.get("10日");
                    if (tenDayData != null) {
                        MoneyFlowData.DailyMoneyFlow tenDayFlow = createDailyMoneyFlow("10日", tenDayData);
                        if (tenDayFlow != null) {
                            dailyFlows.add(tenDayFlow);
                        }
                    }
                    
                    moneyFlowData.setDailyFlows(dailyFlows);
                }
            }
        } catch (Exception e) {
            log.error("转换资金流向数据失败: {}", e.getMessage(), e);
        }
        
        return moneyFlowData;
    }
    
    /**
     * 创建每日资金流向对象
     */
    private MoneyFlowData.DailyMoneyFlow createDailyMoneyFlow(String period, List<Map<String, Object>> flowData) {
        if (flowData == null || flowData.isEmpty()) {
            return null;
        }
        
        MoneyFlowData.DailyMoneyFlow dailyFlow = new MoneyFlowData.DailyMoneyFlow();
        dailyFlow.setDate(period);
        
        try {
            // 查找主力资金数据
            for (Map<String, Object> item : flowData) {
                String fundType = (String) item.get("资金类型");
                if ("主力".equals(fundType)) {
                    Object netInflowObj = item.get("净流入额（万元）");
                    if (netInflowObj instanceof Number) {
                        dailyFlow.setMainInflow(((Number) netInflowObj).doubleValue());
                    }
                    Object ratioObj = item.get("净占比（%）");
                    if (ratioObj instanceof Number) {
                        dailyFlow.setInflowRatio(((Number) ratioObj).doubleValue());
                    }
                    break;
                }
            }
            
            // 计算总净流入（这里简化为主力净流入）
            if (dailyFlow.getMainInflow() != null) {
                dailyFlow.setTotalInflow(dailyFlow.getMainInflow());
            }
            
        } catch (Exception e) {
            log.error("创建每日资金流向对象失败: {}", e.getMessage(), e);
            return null;
        }
        
        return dailyFlow;
    }

    /**
     * 转换为MarginTradingData对象
     */
    private MarginTradingData convertToMarginTradingData(List<Map<String, Object>> rawData) {
        MarginTradingData marginTradingData = new MarginTradingData();

        try {
            if (rawData != null && !rawData.isEmpty()) {
                List<MarginTradingData.DailyMarginData> dailyDataList = new java.util.ArrayList<>();
                
                // 遍历每日数据
                for (Map<String, Object> dayData : rawData) {
                    MarginTradingData.DailyMarginData dailyMarginData = new MarginTradingData.DailyMarginData();
                    
                    // 提取日期
                    Object dateObj = dayData.get("日期");
                    if (dateObj != null) {
                        dailyMarginData.setDate(dateObj.toString());
                    }
                    
                    // 提取融资余额
                    Object marginBalanceObj = dayData.get("融资余额");
                    if (marginBalanceObj instanceof Number) {
                        dailyMarginData.setMarginBalance(((Number) marginBalanceObj).doubleValue());
                    }
                    
                    // 提取融券余额
                    Object shortBalanceObj = dayData.get("融券余额");
                    if (shortBalanceObj instanceof Number) {
                        dailyMarginData.setShortBalance(((Number) shortBalanceObj).doubleValue());
                    }
                    
                    // 提取融资买入额
                    Object marginBuyAmountObj = dayData.get("融资买入额");
                    if (marginBuyAmountObj instanceof Number) {
                        dailyMarginData.setMarginBuyAmount(((Number) marginBuyAmountObj).doubleValue());
                    }
                    
                    // 提取融券卖出量
                    Object shortSellAmountObj = dayData.get("融券卖出量");
                    if (shortSellAmountObj instanceof Number) {
                        dailyMarginData.setShortSellAmount(((Number) shortSellAmountObj).doubleValue());
                    }
                    
                    // 提取融资偿还额
                    Object marginRepayAmountObj = dayData.get("融资偿还额");
                    if (marginRepayAmountObj instanceof Number) {
                        dailyMarginData.setMarginRepayAmount(((Number) marginRepayAmountObj).doubleValue());
                    }
                    
                    // 提取融券偿还量
                    Object shortRepayAmountObj = dayData.get("融券偿还量");
                    if (shortRepayAmountObj instanceof Number) {
                        dailyMarginData.setShortRepayAmount(((Number) shortRepayAmountObj).doubleValue());
                    }
                    
                    // 提取融资净买入额
                    Object netMarginAmountObj = dayData.get("融资净买入额");
                    if (netMarginAmountObj instanceof Number) {
                        dailyMarginData.setNetMarginAmount(((Number) netMarginAmountObj).doubleValue());
                    }
                    
                    // 提取融券净卖出额
                    Object netShortAmountObj = dayData.get("融券净卖出");
                    if (netShortAmountObj instanceof Number) {
                        dailyMarginData.setNetShortAmount(((Number) netShortAmountObj).doubleValue());
                    }
                    
                    dailyDataList.add(dailyMarginData);
                }
                
                marginTradingData.setDailyData(dailyDataList);
            }
        } catch (Exception e) {
            log.error("转换融资融券数据失败: {}", e.getMessage(), e);
        }
        
        return marginTradingData;
    }
    

    
    /**
     * 从近5日指标数据中提取技术指标
     */
    private void extractFromRecentDaysData(TechnicalIndicators indicators, List<Map<String, Object>> recentDaysData) {
        try {
            List<TechnicalIndicators.DailyIndicators> dailyIndicatorsList = new java.util.ArrayList<>();
            
            for (Map<String, Object> dayData : recentDaysData) {
                // 创建每日指标对象
                TechnicalIndicators.DailyIndicators dailyIndicators = new TechnicalIndicators.DailyIndicators();
                
                // 设置基本信息
                dailyIndicators.setDate((String) dayData.get("date"));
                dailyIndicators.setClose(getDoubleValue(dayData.get("close")));
                dailyIndicators.setVolume(getDoubleValue(dayData.get("volume")));
                
                // 设置移动平均线
                dailyIndicators.setMa5(getDoubleValue(dayData.get("ma5")));
                dailyIndicators.setMa10(getDoubleValue(dayData.get("ma10")));
                dailyIndicators.setMa20(getDoubleValue(dayData.get("ma20")));
                dailyIndicators.setMa60(getDoubleValue(dayData.get("ma60")));
                
                // 设置技术指标
                dailyIndicators.setRsi(getDoubleValue(dayData.get("rsi")));
                dailyIndicators.setMacd(getDoubleValue(dayData.get("macd")));
                dailyIndicators.setMacdSignal(getDoubleValue(dayData.get("macd_signal")));
                dailyIndicators.setMacdHist(getDoubleValue(dayData.get("macd_hist")));
                
                // 设置布林带
                dailyIndicators.setBollingerUpper(getDoubleValue(dayData.get("bollinger_upper")));
                dailyIndicators.setBollingerMiddle(getDoubleValue(dayData.get("bollinger_middle")));
                dailyIndicators.setBollingerLower(getDoubleValue(dayData.get("bollinger_lower")));
                
                // 设置KDJ
                dailyIndicators.setKdjK(getDoubleValue(dayData.get("kdj_k")));
                dailyIndicators.setKdjD(getDoubleValue(dayData.get("kdj_d")));
                dailyIndicators.setKdjJ(getDoubleValue(dayData.get("kdj_j")));
                
                dailyIndicatorsList.add(dailyIndicators);
            }
            
            // 设置日期映射格式
            indicators.setDailyIndicators(dailyIndicatorsList);
            
        } catch (Exception e) {
            log.error("从近5日指标数据提取失败: {}", e.getMessage(), e);
        }
    }
    

    

    


    /**
     * 从Map中提取股票名称
     */
    private String extractStockName(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        String stockName = null;
        if (data.get("stockName") instanceof String) {
            stockName = (String) data.get("stockName");
        } else if (data.get("name") instanceof String) {
            stockName = (String) data.get("name");
        } else if (data.get("companyName") instanceof String) {
            stockName = (String) data.get("companyName");
        }
        return stockName;
    }
    
    /**
     * 保存分析结果到数据库
     */
    private void saveAnalysisResult(StockAnalysisRequest request, StockAnalysisResponse response, AIAnalysisResult aiAnalysisResult) {
        try {
            // 先删除同一台机器对同一支股票的旧记录
            try {
                stockAnalysisResultRepository.deleteByStockCode(request.getStockCode());
                log.info("已删除对股票 {} 的旧分析记录", request.getStockCode());
            } catch (Exception e) {
                log.warn("删除旧分析记录失败: {}", e.getMessage());
            }
            
            StockAnalysisResultEntity entity = new StockAnalysisResultEntity();
            entity.setMachineId(request.getMachineId());
            entity.setStockCode(request.getStockCode());
            entity.setStockName(response.getStockName());
            entity.setAnalysisTime(java.time.LocalDateTime.now());
            entity.setFullAnalysis(aiAnalysisResult.getFullAnalysis());
            entity.setCompanyFundamentalAnalysis(aiAnalysisResult.getCompanyFundamentalAnalysis());
            entity.setOperationStrategy(aiAnalysisResult.getOperationStrategy());
            entity.setIntradayOperations(aiAnalysisResult.getIntradayOperations());
            entity.setIndustryPolicyOrientation(aiAnalysisResult.getIndustryPolicyOrientation());
            entity.setStatus("completed");
            
            stockAnalysisResultRepository.save(entity);
            log.info("成功保存股票分析结果到数据库: {} - {}", request.getMachineId(), request.getStockCode());
        } catch (Exception e) {
            log.error("保存股票分析结果失败: {}", e.getMessage(), e);
        }
    }

    /**
     * AI详细分析股票数据并生成策略推荐
     */
    public Map<String, Object> generateAIDetailedAnalysis(String stockCode) {
        try {
            log.info("开始AI详细分析股票: {}", stockCode);
            
            // 1. 获取详细数据
            Map<String, Object> detailedData = getDetailedStockAnalysis(stockCode);
            
            // 2. 构建AI分析提示
            String aiPrompt = buildAIAnalysisPrompt(stockCode, detailedData);
            
            // 3. 调用AI进行分析
            String aiAnalysisResult = callAIForAnalysis(aiPrompt);
            
            // 4. 解析AI结果
            Map<String, Object> aiResult = parseAIAnalysisResult(aiAnalysisResult);
            
            // 5. 合并结果
            Map<String, Object> finalResult = new HashMap<>();
            finalResult.put("stockCode", stockCode);
            finalResult.put("analysisTimestamp", new Date().toString());
            finalResult.put("rawData", detailedData);
            finalResult.put("aiAnalysis", aiResult);
            
            log.info("AI详细分析完成，股票代码: {}", stockCode);
            return finalResult;
            
        } catch (Exception e) {
            log.error("AI详细分析失败: {}", e.getMessage(), e);
            throw new RuntimeException("AI详细分析失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 构建AI分析提示
     */
    private String buildAIAnalysisPrompt(String stockCode, Map<String, Object> data) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请对以下股票数据进行详细分析，并给出投资策略建议。\n\n");
        prompt.append("股票代码: ").append(stockCode).append("\n\n");
        
        // 添加基础数据
        if (data.containsKey("stockBasic")) {
            Map<String, Object> basic = (Map<String, Object>) data.get("stockBasic");
            prompt.append("基础信息:\n");
            basic.forEach((key, value) -> prompt.append("- ").append(key).append(": ").append(value).append("\n"));
            prompt.append("\n");
        }
        
        // 添加技术指标
        if (data.containsKey("technicalIndicators")) {
            Map<String, Object> technical = (Map<String, Object>) data.get("technicalIndicators");
            prompt.append("技术指标:\n");
            technical.forEach((key, value) -> prompt.append("- ").append(key).append(": ").append(value).append("\n"));
            prompt.append("\n");
        }
        
        // 添加资金流向
        if (data.containsKey("moneyFlowData")) {
            List<Map<String, Object>> moneyFlow = (List<Map<String, Object>>) data.get("moneyFlowData");
            prompt.append("资金流向数据:\n");
            if (!moneyFlow.isEmpty()) {
                Map<String, Object> latest = moneyFlow.get(0);
                latest.forEach((key, value) -> prompt.append("- ").append(key).append(": ").append(value).append("\n"));
            }
            prompt.append("\n");
        }
        
        // 添加融资融券数据
        if (data.containsKey("marginTradingData")) {
            List<Map<String, Object>> margin = (List<Map<String, Object>>) data.get("marginTradingData");
            prompt.append("融资融券数据:\n");
            if (!margin.isEmpty()) {
                Map<String, Object> latest = margin.get(0);
                latest.forEach((key, value) -> prompt.append("- ").append(key).append(": ").append(value).append("\n"));
            }
            prompt.append("\n");
        }
        
        // 添加同行比较
        if (data.containsKey("peerComparison")) {
            Map<String, Object> peer = (Map<String, Object>) data.get("peerComparison");
            prompt.append("同行比较数据:\n");
            peer.forEach((key, value) -> prompt.append("- ").append(key).append(": ").append(value).append("\n"));
            prompt.append("\n");
        }
        
        prompt.append("请基于以上数据，提供以下分析：\n");
        prompt.append("1. 技术面分析：包括趋势、支撑阻力位、技术指标解读\n");
        prompt.append("2. 资金面分析：资金流向、融资融券变化\n");
        prompt.append("3. 基本面分析：行业地位、估值水平\n");
        prompt.append("4. 风险评估：当前风险等级和主要风险点\n");
        prompt.append("5. 投资策略：买入/持有/卖出建议，目标价位，止损位\n");
        prompt.append("6. 操作建议：具体操作时机和注意事项\n\n");
        prompt.append("请以JSON格式返回，包含以下字段：\n");
        prompt.append("- technicalAnalysis: 技术面分析\n");
        prompt.append("- capitalAnalysis: 资金面分析\n");
        prompt.append("- fundamentalAnalysis: 基本面分析\n");
        prompt.append("- riskAssessment: 风险评估\n");
        prompt.append("- investmentStrategy: 投资策略\n");
        prompt.append("- operationAdvice: 操作建议\n");
        prompt.append("- summary: 总结\n");
        
        return prompt.toString();
    }
    
    /**
     * 调用AI进行分析
     */
    private String callAIForAnalysis(String prompt) {
        try {
            log.info("调用AI进行分析，提示长度: {}", prompt.length());
            
            // 调用现有的AI服务进行分析
            String aiAnalysisResult = stockAnalysisAI.analyzeGeneralMarket(prompt);
            
            log.info("AI分析完成，结果长度: {}", aiAnalysisResult.length());
            return aiAnalysisResult;
            
        } catch (Exception e) {
            log.error("AI分析调用失败: {}", e.getMessage(), e);
            // 如果AI服务调用失败，返回模拟结果作为备选
            log.warn("使用模拟AI分析结果作为备选");
            return generateMockAIAnalysis();
        }
    }
    
    /**
     * 生成模拟的AI分析结果
     */
    private String generateMockAIAnalysis() {
        return """
            {
                "technicalAnalysis": "从技术指标看，该股票MA5、MA10、MA20呈现多头排列，RSI处于50-70区间，MACD金叉向上，技术面偏强。布林带显示股价运行在上轨附近，KDJ指标显示超买信号。",
                "capitalAnalysis": "资金流向数据显示主力资金持续流入，融资余额稳步增长，融券余额相对较低，表明市场对该股票较为看好。",
                "fundamentalAnalysis": "该股票在行业内具有较强竞争力，估值水平合理，基本面稳健。",
                "riskAssessment": "当前风险等级为中等，主要风险包括：1）技术面超买风险；2）市场整体波动风险；3）行业政策变化风险。",
                "investmentStrategy": "建议采取分批建仓策略，当前价位可少量买入，回调时分批加仓。目标价位：当前价格+15%，止损位：当前价格-8%。",
                "operationAdvice": "1）关注大盘走势，避免系统性风险；2）设置止盈止损，严格执行；3）分批操作，控制仓位；4）关注公司公告和行业动态。",
                "summary": "该股票技术面偏强，资金面良好，基本面稳健，建议适度参与，注意风险控制。"
            }
            """;
    }
    
    /**
     * 解析AI分析结果
     */
    private Map<String, Object> parseAIAnalysisResult(String aiResult) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(aiResult, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("解析AI分析结果失败: {}", e.getMessage(), e);
            // 返回错误信息
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "AI分析结果解析失败: " + e.getMessage());
            errorResult.put("rawResult", aiResult);
            return errorResult;
        }
    }
    
    /**
     * 启动AI详细分析（异步）
     */
    public void startAIDetailedAnalysisAsync(String stockCode, String taskId) {
        AIAnalysisTask task = new AIAnalysisTask(taskId, stockCode);
        aiAnalysisTasks.put(taskId, task);
        
        // 异步执行分析任务
        CompletableFuture.runAsync(() -> {
            try {
                log.info("开始异步AI详细分析，任务ID: {}, 股票代码: {}", taskId, stockCode);
                
                // 更新进度
                task.setProgress(10);
                
                // 获取详细数据
                Map<String, Object> detailedData = getDetailedStockAnalysis(stockCode);
                task.setProgress(40);
                
                // 构建AI提示词
                String prompt = buildAIAnalysisPrompt(stockCode, detailedData);
                task.setProgress(60);
                
                // 调用AI分析
                String aiResult = callAIForAnalysis(prompt);
                task.setProgress(80);
                
                // 解析AI结果
                Map<String, Object> aiAnalysis = parseAIAnalysisResult(aiResult);
                task.setProgress(90);
                
                // 组装最终结果
                Map<String, Object> finalResult = new HashMap<>();
                finalResult.put("aiAnalysis", aiAnalysis);
                finalResult.put("rawData", detailedData);
                finalResult.put("analysisTimestamp", new Date().toString());
                finalResult.put("stockCode", stockCode);
                
                task.setResult(finalResult);
                task.setStatus("COMPLETED");
                task.setProgress(100);
                
                log.info("异步AI详细分析完成，任务ID: {}, 股票代码: {}", taskId, stockCode);
                
            } catch (Exception e) {
                log.error("异步AI详细分析失败，任务ID: {}, 股票代码: {}: {}", taskId, stockCode, e.getMessage(), e);
                task.setStatus("FAILED");
                task.setErrorMessage(e.getMessage());
            }
        }, executorService);
    }
    
    /**
     * 获取AI分析任务状态
     */
    public Map<String, Object> getAIAnalysisStatus(String taskId) {
        AIAnalysisTask task = aiAnalysisTasks.get(taskId);
        if (task == null) {
            Map<String, Object> errorStatus = new HashMap<>();
            errorStatus.put("error", "任务不存在");
            errorStatus.put("taskId", taskId);
            return errorStatus;
        }
        
        return task.toStatusMap();
    }

    // 查询今日盯盘记录
    public List<Map<String, Object>> getTodayMonitoringRecords(String stockCode, LocalDateTime start, LocalDateTime end) {
        var list = stockMonitoringRecordRepository.findTodayByStockCode(stockCode, start, end);
        List<Map<String, Object>> result = new ArrayList<>();
        for (var r : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("stockCode", r.getStockCode());
            m.put("jobId", r.getJobId());
            m.put("content", r.getContent());
            m.put("createdAt", r.getCreatedAt().toString());
            result.add(m);
        }
        return result;
    }
    
    /**
     * AI分析任务状态类
     */
    private static class AIAnalysisTask {
        private final String taskId;
        private final String stockCode;
        private final LocalDateTime startTime;
        private volatile String status; // PROCESSING, COMPLETED, FAILED
        private volatile Map<String, Object> result;
        private volatile String errorMessage;
        private volatile int progress; // 0-100
        
        public AIAnalysisTask(String taskId, String stockCode) {
            this.taskId = taskId;
            this.stockCode = stockCode;
            this.startTime = LocalDateTime.now();
            this.status = "PROCESSING";
            this.progress = 0;
        }
        
        // Getters and Setters
        public String getTaskId() { return taskId; }
        public String getStockCode() { return stockCode; }
        public LocalDateTime getStartTime() { return startTime; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Map<String, Object> getResult() { return result; }
        public void setResult(Map<String, Object> result) { this.result = result; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public int getProgress() { return this.progress; }
        public void setProgress(int progress) { this.progress = progress; }
        
        public Map<String, Object> toStatusMap() {
            Map<String, Object> statusMap = new HashMap<>();
            statusMap.put("taskId", taskId);
            statusMap.put("stockCode", stockCode);
            statusMap.put("startTime", startTime.toString());
            statusMap.put("status", status);
            statusMap.put("progress", progress);
            statusMap.put("elapsedTime", java.time.Duration.between(startTime, LocalDateTime.now()).toSeconds());
            
            if (result != null) {
                statusMap.put("result", result);
            }
            if (errorMessage != null) {
                statusMap.put("errorMessage", errorMessage);
            }
            
            return statusMap;
        }
    }

    // ========= 盘中盯盘 =========
    private static class MonitoringJob {
        private final String jobId;
        private final String stockCode;
        private final int intervalMinutes;
        private final String analysisId;
        private final String machineId;
        private final LocalDateTime startTime;
        private volatile String status; // running, stopped
        private volatile LocalDateTime lastRunTime;
        private volatile String lastMessage;

        public MonitoringJob(String jobId, String stockCode, int intervalMinutes, String analysisId, String machineId) {
            this.jobId = jobId;
            this.stockCode = stockCode;
            this.intervalMinutes = intervalMinutes;
            this.analysisId = analysisId;
            this.machineId = machineId;
            this.startTime = LocalDateTime.now();
            this.status = "running";
        }
    }

    public String startIntradayMonitoring(String stockCode, int intervalMinutes, String analysisId, String machineId) {
        if (intervalMinutes != 5 && intervalMinutes != 10 && intervalMinutes != 30 && intervalMinutes != 60) {
            throw new IllegalArgumentException("intervalMinutes must be one of 5,10,30,60");
        }
        
        // 检查是否已有该股票的运行中任务
        if (stockMonitoringJobRepository.existsByStockCodeAndStatus(stockCode, "running")) {
            throw new IllegalStateException("该股票已有运行中的盯盘任务");
        }
        
        String jobId = "monitor_" + stockCode + "_" + System.currentTimeMillis();
        
        // 保存到数据库
        com.stockanalysis.entity.StockMonitoringJobEntity jobEntity = new com.stockanalysis.entity.StockMonitoringJobEntity();
        jobEntity.setJobId(jobId);
        jobEntity.setStockCode(stockCode);
        jobEntity.setIntervalMinutes(intervalMinutes);
        jobEntity.setAnalysisId(analysisId);
        jobEntity.setMachineId(machineId);
        jobEntity.setStatus("running");
        jobEntity.setStartTime(java.time.LocalDateTime.now());
        stockMonitoringJobRepository.save(jobEntity);
        
        // 创建内存中的任务对象
        MonitoringJob job = new MonitoringJob(jobId, stockCode, intervalMinutes, analysisId, machineId);
        monitoringJobs.put(jobId, job);

        // 启动周期任务
        CompletableFuture.runAsync(() -> runMonitoringLoop(job), executorService);
        return jobId;
    }

    public boolean stopIntradayMonitoring(String jobId) {
        // 更新数据库中的状态
        stockMonitoringJobRepository.findByJobId(jobId).ifPresent(jobEntity -> {
            jobEntity.setStatus("stopped");
            jobEntity.setLastMessage("用户手动停止");
            stockMonitoringJobRepository.save(jobEntity);
        });
        
        // 更新内存中的状态
        MonitoringJob job = monitoringJobs.get(jobId);
        if (job != null) {
            job.status = "stopped";
        }
        
        return true;
    }

    public Map<String, Object> getIntradayMonitoringStatus(String jobId) {
        // 优先从数据库查询
        return stockMonitoringJobRepository.findByJobId(jobId)
            .map(jobEntity -> {
                Map<String, Object> status = new HashMap<>();
                status.put("exists", true);
                status.put("jobId", jobEntity.getJobId());
                status.put("stockCode", jobEntity.getStockCode());
                status.put("intervalMinutes", jobEntity.getIntervalMinutes());
                status.put("status", jobEntity.getStatus());
                status.put("startTime", jobEntity.getStartTime().toString());
                status.put("lastRunTime", jobEntity.getLastRunTime() == null ? null : jobEntity.getLastRunTime().toString());
                status.put("lastMessage", jobEntity.getLastMessage());
                return status;
            })
            .orElse(Map.of("exists", false));
    }

    public Map<String, Object> getStockMonitoringStatus(String stockCode) {
        return stockMonitoringJobRepository.findRunningJobByStockCode(stockCode)
            .map(jobEntity -> {
                Map<String, Object> status = new HashMap<>();
                status.put("exists", true);
                status.put("jobId", jobEntity.getJobId());
                status.put("stockCode", jobEntity.getStockCode());
                status.put("intervalMinutes", jobEntity.getIntervalMinutes());
                status.put("status", jobEntity.getStatus());
                status.put("startTime", jobEntity.getStartTime().toString());
                status.put("lastRunTime", jobEntity.getLastRunTime() == null ? null : jobEntity.getLastRunTime().toString());
                status.put("lastMessage", jobEntity.getLastMessage());
                return status;
            })
            .orElse(Map.of("exists", false));
    }

    /**
     * 手动清理所有运行中的盯盘任务
     */
    public void cleanupAllMonitoringJobs() {
        log.info("手动清理所有运行中的盯盘任务...");
        
        try {
            // 获取所有运行中的任务
            List<com.stockanalysis.entity.StockMonitoringJobEntity> runningJobs = 
                stockMonitoringJobRepository.findAllRunningJobs();
            
            if (runningJobs.isEmpty()) {
                log.info("没有运行中的盯盘任务需要清理");
                return;
            }
            
            log.info("发现 {} 个运行中的盯盘任务，开始清理...", runningJobs.size());
            
            for (com.stockanalysis.entity.StockMonitoringJobEntity jobEntity : runningJobs) {
                try {
                    // 更新数据库状态为已停止
                    jobEntity.setStatus("stopped");
                    jobEntity.setLastMessage("手动清理停止");
                    jobEntity.setLastRunTime(java.time.LocalDateTime.now());
                    stockMonitoringJobRepository.save(jobEntity);
                    
                    // 停止内存中的任务
                    MonitoringJob memoryJob = monitoringJobs.get(jobEntity.getJobId());
                    if (memoryJob != null) {
                        memoryJob.status = "stopped";
                        memoryJob.lastMessage = "手动清理停止";
                        log.info("已停止内存中的盯盘任务: {} - {}", jobEntity.getStockCode(), jobEntity.getJobId());
                    }
                    
                    log.info("已清理盯盘任务: {} - {} ({})", 
                        jobEntity.getStockCode(), jobEntity.getJobId(), jobEntity.getIntervalMinutes());
                        
                } catch (Exception e) {
                    log.error("清理盯盘任务失败: {} - {}", jobEntity.getStockCode(), jobEntity.getJobId(), e.getMessage());
                }
            }
            
            log.info("盯盘任务清理完成，共清理 {} 个任务", runningJobs.size());
            
        } catch (Exception e) {
            log.error("清理盯盘任务时发生错误", e);
        }
    }

    /**
     * 午间暂停：将所有 running 的盯盘任务标记为 paused（数据库与内存）
     */
    public void pauseAllMonitoringJobs() {
        try {
            List<com.stockanalysis.entity.StockMonitoringJobEntity> runningJobs =
                stockMonitoringJobRepository.findAllRunningJobs();
            if (runningJobs.isEmpty()) {
                log.info("没有运行中的盯盘任务需要暂停");
                return;
            }
            for (com.stockanalysis.entity.StockMonitoringJobEntity jobEntity : runningJobs) {
                jobEntity.setStatus("paused");
                jobEntity.setLastMessage("午间暂停");
                jobEntity.setLastRunTime(java.time.LocalDateTime.now());
                stockMonitoringJobRepository.save(jobEntity);

                MonitoringJob memoryJob = monitoringJobs.get(jobEntity.getJobId());
                if (memoryJob != null) {
                    memoryJob.status = "paused";
                    memoryJob.lastMessage = "午间暂停";
                }
            }
            log.info("午间暂停完成，共暂停 {} 个任务", runningJobs.size());
        } catch (Exception e) {
            log.error("午间暂停盯盘任务失败", e);
        }
    }

    /**
     * 午间恢复：将所有 paused 的盯盘任务标记为 running（数据库与内存），由循环继续执行
     */
    public void resumeAllMonitoringJobs() {
        try {
            List<com.stockanalysis.entity.StockMonitoringJobEntity> pausedJobs =
                stockMonitoringJobRepository.findAllPausedJobs();
            if (pausedJobs.isEmpty()) {
                log.info("没有暂停中的盯盘任务需要恢复");
                return;
            }
            for (com.stockanalysis.entity.StockMonitoringJobEntity jobEntity : pausedJobs) {
                jobEntity.setStatus("running");
                jobEntity.setLastMessage("午间恢复");
                jobEntity.setLastRunTime(java.time.LocalDateTime.now());
                stockMonitoringJobRepository.save(jobEntity);

                MonitoringJob memoryJob = monitoringJobs.get(jobEntity.getJobId());
                if (memoryJob != null) {
                    memoryJob.status = "running";
                    memoryJob.lastMessage = "午间恢复";
                } else {
                    // 内存中不存在（应用重启等情况），创建占位以便循环可感知
                    MonitoringJob job = new MonitoringJob(jobEntity.getJobId(), jobEntity.getStockCode(),
                            jobEntity.getIntervalMinutes(), null, null);
                    job.status = "running";
                    job.lastMessage = "午间恢复";
                    monitoringJobs.put(jobEntity.getJobId(), job);
                    java.util.concurrent.CompletableFuture.runAsync(() -> runMonitoringLoop(job), executorService);
                }
            }
            log.info("午间恢复完成，共恢复 {} 个任务", pausedJobs.size());
        } catch (Exception e) {
            log.error("午间恢复盯盘任务失败", e);
        }
    }

    /**
     * 获取所有正在盯盘的任务
     */
    public List<Map<String, Object>> getAllMonitoringJobs() {
        try {
            List<com.stockanalysis.entity.StockMonitoringJobEntity> jobs = 
                stockMonitoringJobRepository.findAllRunningJobs();
            List<Map<String, Object>> result = new ArrayList<>();
            
            for (com.stockanalysis.entity.StockMonitoringJobEntity job : jobs) {
                Map<String, Object> jobInfo = new HashMap<>();
                jobInfo.put("jobId", job.getJobId());
                jobInfo.put("stockCode", job.getStockCode());
                jobInfo.put("stockName", getStockName(job.getStockCode())); // 获取股票名称
                jobInfo.put("intervalMinutes", job.getIntervalMinutes());
                jobInfo.put("status", job.getStatus());
                jobInfo.put("startTime", job.getStartTime());
                jobInfo.put("lastRunTime", job.getLastRunTime());
                jobInfo.put("lastMessage", job.getLastMessage());
                jobInfo.put("createdAt", job.getCreatedAt());
                jobInfo.put("updatedAt", job.getUpdatedAt());
                
                result.add(jobInfo);
            }
            
            return result;
        } catch (Exception e) {
            log.error("获取所有盯盘任务失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取股票名称（简化实现）
     */
    private String getStockName(String stockCode) {
        // 这里可以根据需要实现股票名称获取逻辑
        // 暂时返回股票代码
        return stockCode;
    }

    /**
     * 应用关闭时清理所有运行中的盯盘任务
     */
    @Override
    public void destroy() throws Exception {
        log.info("应用正在关闭，开始清理所有运行中的盯盘任务...");
        
        try {
            // 获取所有运行中的任务
            List<com.stockanalysis.entity.StockMonitoringJobEntity> runningJobs = 
                stockMonitoringJobRepository.findAllRunningJobs();
            
            if (runningJobs.isEmpty()) {
                log.info("没有运行中的盯盘任务需要清理");
                return;
            }
            
            log.info("发现 {} 个运行中的盯盘任务，开始清理...", runningJobs.size());
            
            for (com.stockanalysis.entity.StockMonitoringJobEntity jobEntity : runningJobs) {
                try {
                    // 更新数据库状态为已停止
                    jobEntity.setStatus("stopped");
                    jobEntity.setLastMessage("应用关闭时自动停止");
                    jobEntity.setLastRunTime(java.time.LocalDateTime.now());
                    stockMonitoringJobRepository.save(jobEntity);
                    
                    // 停止内存中的任务
                    MonitoringJob memoryJob = monitoringJobs.get(jobEntity.getJobId());
                    if (memoryJob != null) {
                        memoryJob.status = "stopped";
                        memoryJob.lastMessage = "应用关闭时自动停止";
                        log.info("已停止内存中的盯盘任务: {} - {}", jobEntity.getStockCode(), jobEntity.getJobId());
                    }
                    
                    log.info("已清理盯盘任务: {} - {} ({})", 
                        jobEntity.getStockCode(), jobEntity.getJobId(), jobEntity.getIntervalMinutes());
                        
                } catch (Exception e) {
                    log.error("清理盯盘任务失败: {} - {}", jobEntity.getStockCode(), jobEntity.getJobId(), e.getMessage());
                }
            }
            
            // 关闭线程池
            if (executorService != null && !executorService.isShutdown()) {
                log.info("正在关闭线程池...");
                executorService.shutdown();
                
                // 等待线程池关闭，最多等待30秒
                if (!executorService.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.warn("线程池未能在30秒内完全关闭，强制关闭");
                    executorService.shutdownNow();
                }
            }
            
            log.info("盯盘任务清理完成，共清理 {} 个任务", runningJobs.size());
            
        } catch (Exception e) {
            log.error("清理盯盘任务时发生错误", e);
        }
    }

    private void runMonitoringLoop(MonitoringJob job) {
        while ("running".equals(job.status) || "paused".equals(job.status)) {
            try {
                // 午间暂停窗口：11:30-13:00（工作日）
                java.time.LocalTime nowTime = java.time.LocalTime.now();
                java.time.DayOfWeek dow2 = java.time.LocalDate.now().getDayOfWeek();
                boolean workDay2 = dow2 != java.time.DayOfWeek.SATURDAY && dow2 != java.time.DayOfWeek.SUNDAY;
                if (workDay2 && (nowTime.isAfter(java.time.LocalTime.of(11, 30)) && nowTime.isBefore(java.time.LocalTime.of(13, 0)))) {
                    job.status = "paused";
                    job.lastMessage = "午间暂停";
                    // 同步数据库为 paused
                    stockMonitoringJobRepository.findByJobId(job.jobId).ifPresent(e -> {
                        e.setStatus("paused");
                        e.setLastMessage("午间暂停");
                        e.setLastRunTime(java.time.LocalDateTime.now());
                        stockMonitoringJobRepository.save(e);
                    });
                }

                if ("paused".equals(job.status)) {
                    // 每30秒检查一次是否过了13:00或被外部恢复/停止
                    Thread.sleep(30_000);
                    // 若数据库被设置为 running，则切回运行
                    stockMonitoringJobRepository.findByJobId(job.jobId).ifPresent(e -> job.status = e.getStatus());
                    continue;
                }
                // 非交易时段（15:00后或非工作日）自动停止
                java.time.LocalDate today = java.time.LocalDate.now();
                java.time.LocalTime now = java.time.LocalTime.now();
                java.time.DayOfWeek dow = today.getDayOfWeek();
                boolean isWorkDay = dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY;
                if (!isWorkDay || now.isAfter(java.time.LocalTime.of(15, 0))) {
                    job.status = "stopped";
                    job.lastMessage = "非交易时段，已自动停止";
                    
                    // 更新数据库状态
                    stockMonitoringJobRepository.findByJobId(job.jobId).ifPresent(jobEntity -> {
                        jobEntity.setStatus("stopped");
                        jobEntity.setLastMessage("非交易时段，已自动停止");
                        stockMonitoringJobRepository.save(jobEntity);
                    });
                    break;
                }

                // 收集数据
                Map<String, Object> rawData = new HashMap<>();
                try { rawData.put("股票基础数据", pythonScriptService.getStockBasicData(job.stockCode)); } catch (Exception ignore) {}
                try { rawData.put("个股当日分时", pythonScriptService.getStockTrendsToday(job.stockCode)); } catch (Exception ignore) {}
                try { rawData.put("大盘当日分时", pythonScriptService.getMarketTrendsToday(job.stockCode)); } catch (Exception ignore) {}
                try { rawData.put("板块当日分时", pythonScriptService.getBoardTrendsToday(job.stockCode)); } catch (Exception ignore) {}
                try { rawData.put("当日资金流向", pythonScriptService.getMoneyFlowToday(job.stockCode)); } catch (Exception ignore) {}

                // 从数据库取最新综合分析作为system message（若有）
                Map<String, Object> context = new HashMap<>();
                try {
                    var latest = stockAnalysisResultRepository.findFirstByStockCodeOrderByAnalysisTimeDesc(job.stockCode);
                    if (latest != null && latest.getFullAnalysis() != null) {
                        context.put("综合分析", latest.getFullAnalysis());
                    }
                } catch (Exception ignore) {}

                String prompt = buildIntradayPrompt(job.stockCode, context, rawData);

                // 用deepseek-chat，尽量少token
                String aiResult;
                try {
                    aiResult = deepseekChatModel.generate(prompt);
                } catch (Exception e) {
                    aiResult = "{\"summary\":\"AI暂不可用，稍后重试\"}";
                }

                // 保存盯盘记录
                try {
                    com.stockanalysis.entity.StockMonitoringRecordEntity rec = new com.stockanalysis.entity.StockMonitoringRecordEntity();
                    rec.setStockCode(job.stockCode);
                    rec.setJobId(job.jobId);
                    rec.setContent(aiResult);
                    stockMonitoringRecordRepository.save(rec);
                } catch (Exception saveEx) {
                    log.warn("保存盯盘记录失败: {}", saveEx.getMessage());
                }

                job.lastRunTime = LocalDateTime.now();
                job.lastMessage = "OK";

                // 间隔等待
                Thread.sleep(job.intervalMinutes * 60L * 1000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                job.status = "stopped";
                job.lastMessage = "中断";
            } catch (Exception e) {
                job.lastMessage = "异常: " + e.getMessage();
                try { Thread.sleep(job.intervalMinutes * 60L * 1000L); } catch (InterruptedException ignored) {}
            }
        }
    }

    private String buildIntradayPrompt(String stockCode, Map<String, Object> context, Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名专业的盘中AI分析师，你的任务是给出当日操作建议。\n")
          .append("要求: 结合已有综合分析(若有)作为背景，结合实时数据，给出实时的操作建议，最多8-12行。\n")
          .append("要充分发挥你的专业性，要敢于下判断。\n\n")
          .append("尽量引用简短的关键数据点，不要重复大段背景，要尽量节省token。\n\n")
          .append("[股票代码] ").append(stockCode).append("\n");
        if (context != null && !context.isEmpty()) {
            sb.append("[综合分析摘要]\n ").append(context.get("综合分析"));
        }
        sb.append("[最新数据快照]\n").append(data);
        sb.append("输出: 简短要点，列出机会与风险，给出操作提示(若无把握请提示观望)。");
        sb.append("基于当前数据和市场情绪，直接给出操作建议：");
        sb.append("1. 当前操作：买入/卖出/观望");
        sb.append("2. 建议挂单价格：买入价XX.XX，卖出价XX.XX");
        sb.append("3. 理由：结合技术指标、资金流向、市场情绪等");
        sb.append("4. 风险提示：止损位XX.XX，止盈位XX.XX");
        sb.append("5. 置信度评估：高/中/低（基于信号强度、市场一致性等）");
        sb.append("格式：");
        sb.append("【当前建议】买入/卖出/观望");
        sb.append("【挂单价格】买入：XX.XX，卖出：XX.XX");
        sb.append("【操作理由】...");
        sb.append("【风险控制】止损：XX.XX，止盈：XX.XX");
        sb.append("【置信度】高/中/低");
        return sb.toString();
    }
}