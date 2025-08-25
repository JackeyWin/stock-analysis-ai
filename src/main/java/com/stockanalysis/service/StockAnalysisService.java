package com.stockanalysis.service;

import com.stockanalysis.model.*;
import com.stockanalysis.service.DailyRecommendationStorageService;
import com.stockanalysis.util.RetryTemplate;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.time.Duration;

/**
 * 股票分析服务
 */
@Slf4j
@Service
public class StockAnalysisService {

    private final PythonScriptService pythonScriptService;
    private final AIAnalysisService aiAnalysisService;
    private final DailyRecommendationStorageService dailyRecommendationStorageService;
    private final ExecutorService executorService;
    
    // 缓存相关
    private final Map<String, CacheEntry<List<Map<String, Object>>>> stockDataCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<Map<String, Object>>> technicalIndicatorsCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<Map<String, Object>>>> newsDataCache = new ConcurrentHashMap<>();
    
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
                               DailyRecommendationStorageService dailyRecommendationStorageService) {
        this.pythonScriptService = pythonScriptService;
        this.aiAnalysisService = aiAnalysisService;
        this.dailyRecommendationStorageService = dailyRecommendationStorageService;
        this.executorService = Executors.newFixedThreadPool(6);
    }
    
    public DailyRecommendationStorageService getDailyRecommendationStorageService() {
        return dailyRecommendationStorageService;
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
}