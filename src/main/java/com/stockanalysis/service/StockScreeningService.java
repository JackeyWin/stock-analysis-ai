package com.stockanalysis.service;

import com.stockanalysis.tools.StockPoolTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 股票筛选服务
 */
@Slf4j
@Service
public class StockScreeningService {

    private final PythonScriptService pythonScriptService;

    public StockScreeningService(PythonScriptService pythonScriptService) {
        this.pythonScriptService = pythonScriptService;
    }
    
    /**
     * 根据AI分析内容筛选优质股票
     * @param hotspots 政策、行业、市场热点
     */
    public CompletableFuture<Map<String, List<Map<String, Object>>>> screenQualityStocks(Map<String, String> hotspots) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("开始筛选优质股票");
                
                // 1. 获取按行业分组的股票池（根据AI分析内容）
                Map<String, List<String>> stockPoolMap = getStockPoolWithAIAnalysis(hotspots);
                log.info("获取到{}个行业的股票池", stockPoolMap.size());
                
                // 2. 对每个行业的股票进行评估并选出优质股票
                Map<String, List<Map<String, Object>>> qualityStocksMap = new HashMap<>();
                
                for (Map.Entry<String, List<String>> entry : stockPoolMap.entrySet()) {
                    String sector = entry.getKey();
                    List<String> sectorStocks = entry.getValue();
                    
                    // 评估该行业的股票
                    List<Map<String, Object>> evaluatedStocks = sectorStocks.parallelStream()
                            .map(stockCode -> evaluateStock(stockCode, convertToMapObject(hotspots)))
                            .filter(Objects::nonNull)
                            .filter(stock -> (Double) stock.get("score") >= 7.0) // 评分7分以上
                            .sorted((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")))
                            .limit(10) // 每个行业取前10只
                            .collect(Collectors.toList());
                    
                    qualityStocksMap.put(sector, evaluatedStocks);
                    log.info("行业{}筛选完成，选出{}只优质股票", sector, evaluatedStocks.size());
                }
                
                log.info("筛选完成，共处理{}个行业", qualityStocksMap.size());
                return qualityStocksMap;
                
            } catch (Exception e) {
                log.error("筛选优质股票失败: {}", e.getMessage(), e);
                return new HashMap<>();
            }
        });
    }

    /**
     * 根据行业筛选股票
     */
    public CompletableFuture<List<Map<String, Object>>> screenStocksBySector(String sector) {
        return screenStocksBySector(sector, null);
    }
    
    /**
     * 根据行业筛选股票（带热点内容）
     * @param sector 行业
     * @param hotspots 热点内容
     */
    public CompletableFuture<List<Map<String, Object>>> screenStocksBySector(String sector, Map<String, Object> hotspots) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("开始筛选{}行业股票", sector);
                
                // 获取行业相关股票
                List<String> sectorStocks = getSectorStocks(sector);
                
                // 评估并筛选
                List<Map<String, Object>> qualityStocks = sectorStocks.parallelStream()
                        .map(stockCode -> evaluateStock(stockCode, hotspots))
                        .filter(Objects::nonNull)
                        .filter(stock -> (Double) stock.get("score") >= 6.5) // 行业筛选标准稍低
                        .sorted((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")))
                        .limit(20)
                        .collect(Collectors.toList());
                
                log.info("{}行业筛选完成，共找到{}只优质股票", sector, qualityStocks.size());
                return qualityStocks;
                
            } catch (Exception e) {
                log.error("筛选{}行业股票失败: {}", sector, e.getMessage(), e);
                return new ArrayList<>();
            }
        });
    }
    
    /**
     * 根据AI分析内容获取股票池
     * @param hotspots 热点内容
     */
    public Map<String,List<String>> getStockPoolWithAIAnalysis(Map<String, String> hotspots) {
        return getStockPoolWithAIAnalysisInternal(hotspots);
    }
    
    /**
     * 根据AI分析内容获取股票池
     * @param hotspots AI分析的热点内容
     */
    private Map<String,List<String>> getStockPoolWithAIAnalysisInternal(Map<String, String> hotspots) {
        // 这里可以从多个来源获取股票池
        Map<String,List<String>> stockPoolMap = new HashMap<>();
        
        // 如果hotspots不为空，遍历其中的行业名称并获取成分股
        if (hotspots != null) {
            for (Map.Entry<String, String> entry : hotspots.entrySet()) {
                String sector = entry.getKey();
                String description = entry.getValue();
                List<String> secList = new ArrayList<>();
                
                // 检查描述中是否包含股票代码，如果包含则直接加入股票池
                List<String> stockCodesFromDescription = parseAIStockPoolResult(description);
                if (!stockCodesFromDescription.isEmpty()) {
                    secList.addAll(stockCodesFromDescription);
                }

                // 获取行业成分股
                // List<String> sectorStocks = getSectorStocks(sector);
                // secList.addAll(sectorStocks);
                stockPoolMap.put(sector, secList);
            }
        }
        
        // 去重
        return stockPoolMap;
    }
    
    /**
     * 将Map<String, String>转换为Map<String, Object>
     * @param stringMap 输入的字符串映射
     * @return 转换后的对象映射
     */
    private Map<String, Object> convertToMapObject(Map<String, String> stringMap) {
        if (stringMap == null) {
            return null;
        }
        
        Map<String, Object> objectMap = new HashMap<>();
        for (Map.Entry<String, String> entry : stringMap.entrySet()) {
            objectMap.put(entry.getKey(), entry.getValue());
        }
        
        return objectMap;
    }

    /**
     * 获取热门股票列表
     */
    private List<String> getPopularStocks() {
        // 返回一些热门股票代码
        return Arrays.asList(
            "000001", "000002", "000858", "002415", "002594", "002714",
            "300059", "300750", "600036", "600519", "600887", "688981",
            "000725", "002475", "300014", "600276", "600309", "600585",
            "000063", "002352", "300496", "600031", "600690", "688599"
        );
    }

    /**
     * 获取活跃股票列表
     */
    private List<String> getActiveStocks() {
        // 返回一些活跃股票代码
        return Arrays.asList(
            "000876", "002230", "002371", "300033", "300122", "300274",
            "600104", "600132", "600438", "600703", "600745", "688005",
            "000100", "002142", "300408", "600188", "600256", "600588"
        );
    }

    /**
     * 获取概念股票列表
     */
    private List<String> getConceptStocks() {
        // 返回一些概念股票代码
        return Arrays.asList(
            "000977", "002049", "002153", "300015", "300253", "300347",
            "600570", "600660", "600893", "600958", "688111", "688169"
        );
    }

    /**
     * 获取行业成分股
     */
    private List<String> getSectorStocks(String sector) {
        try {
            // 调用Python脚本获取行业成分股数据
            List<Map<String, Object>> stocks = pythonScriptService.getSectorStocks(sector);
            
            if (stocks == null || stocks.isEmpty()) {
                log.warn("未获取到行业{}的成分股数据", sector);
                return new ArrayList<>();
            }
            
            // 提取股票代码列表
            List<String> stockCodes = new ArrayList<>();
            for (Map<String, Object> stock : stocks) {
                Object code = stock.get("code");
                if (code != null) {
                    stockCodes.add(code.toString());
                }
            }
            
            return stockCodes;
            
        } catch (Exception e) {
            log.error("获取行业{}成分股失败: {}", sector, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 解析AI工具返回的股票池结果
     * @param result AI工具返回的结果
     * @return 股票代码列表
     */
    private List<String> parseAIStockPoolResult(String result) {
        List<String> stockCodes = new ArrayList<>();
        
        // 简单解析结果，提取股票代码
        // 这里可以根据实际返回格式进行调整
        if (result != null && !result.isEmpty()) {
            // 尝试从结果中提取6位数字的股票代码
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b(\\d{6})\\b");
            java.util.regex.Matcher matcher = pattern.matcher(result);
            
            while (matcher.find()) {
                String stockCode = matcher.group(1);
                if (!stockCodes.contains(stockCode)) {
                    stockCodes.add(stockCode);
                }
            }
        }
        
        log.info("从AI工具结果中解析出{}只股票", stockCodes.size());
        return stockCodes;
    }

    /**
     * 评估单只股票
     */
    public Map<String, Object> evaluateStock(String stockCode) {
        return evaluateStock(stockCode, null);
    }
    
    /**
     * 评估单只股票（带热点内容）
     * @param stockCode 股票代码
     * @param hotspots 热点内容
     */
    public Map<String, Object> evaluateStock(String stockCode, Map<String, Object> hotspots) {
        try {
            log.debug("评估股票: {}", stockCode);
            
            // 1. 获取股票基本信息
            Map<String, Object> stockInfo = getStockBasicInfo(stockCode);
            if (stockInfo == null || stockInfo.isEmpty()) {
                log.warn("无法获取股票{}的基本信息", stockCode);
                return null;
            }
            
            // 2. 技术指标评估
            Map<String, Object> technicalResult = evaluateTechnicalIndicators(stockCode);
            double technicalScore = (Double) technicalResult.get("score");
            
            // 3. 基本面评估
            double fundamentalScore = (Double) evaluateFundamentals(stockCode).get("score");
            
            // 4. 资金面评估
            Map<String, Object> moneyFlowResult = evaluateMoneyFlow(stockCode);
            double moneyFlowScore = moneyFlowResult != null ? (Double) moneyFlowResult.get("score") : 5.0;
            
            // 5. 根据热点内容调整评分
            if (hotspots != null) {
                technicalScore = adjustScoreByHotspots(technicalScore, stockInfo, hotspots, "technical");
                fundamentalScore = adjustScoreByHotspots(fundamentalScore, stockInfo, hotspots, "fundamental");
                moneyFlowScore = adjustScoreByHotspots(moneyFlowScore, stockInfo, hotspots, "moneyFlow");
            }
            
            // 6. 计算综合评分
            double totalScore = calculateTotalScore(technicalScore, fundamentalScore, moneyFlowScore);
            
            // 7. 组装结果
            Map<String, Object> result = new HashMap<>(stockInfo);
            result.put("technicalScore", technicalScore);
            result.put("fundamentalScore", fundamentalScore);
            result.put("moneyFlowScore", moneyFlowScore);
            result.put("score", totalScore);
            
            log.debug("股票{}评估完成, 综合评分: {}", stockCode, totalScore);
            return result;
            
        } catch (Exception e) {
            log.warn("评估股票{}时发生异常: {}", stockCode, e.getMessage());
            return null;
        }
    }
    
    /**
     * 根据热点内容调整评分
     * @param originalScore 原始评分
     * @param stockInfo 股票信息
     * @param hotspots 热点内容
     * @param scoreType 评分类型
     * @return 调整后的评分
     */
    private double adjustScoreByHotspots(double originalScore, Map<String, Object> stockInfo, Map<String, Object> hotspots, String scoreType) {
        double adjustedScore = originalScore;
        
        // 获取股票的行业/概念信息
        String sector = (String) stockInfo.get("sector");
        String concept = (String) stockInfo.get("concept");
        
        // 检查是否与热点相关
        if (sector != null && !sector.isEmpty()) {
            String policyHotspots = (String) hotspots.getOrDefault("policyHotspots", "");
            String industryHotspots = (String) hotspots.getOrDefault("industryHotspots", "");
            String marketHotspots = (String) hotspots.getOrDefault("marketHotspots", "");
            
            // 如果股票所属行业/概念与热点相关，则适当提高评分
            if (policyHotspots.contains(sector) || industryHotspots.contains(sector) || marketHotspots.contains(sector)) {
                adjustedScore = Math.min(10.0, originalScore + 0.5); // 最多提高0.5分，不超过10分
            }
            
            if (concept != null && !concept.isEmpty()) {
                if (policyHotspots.contains(concept) || industryHotspots.contains(concept) || marketHotspots.contains(concept)) {
                    adjustedScore = Math.min(10.0, adjustedScore + 0.3); // 最多再提高0.3分
                }
            }
        }
        
        return adjustedScore;
    }

    /**
     * 获取股票基本信息
     */
    private Map<String, Object> getStockBasicInfo(String stockCode) {
        try {
            // 使用Python脚本获取股票基本信息
            Map<String, Object> intradayAnalysis = pythonScriptService.getIntradayAnalysis(stockCode);
            if (intradayAnalysis != null && intradayAnalysis.containsKey("stockBasic")) {
                return (Map<String, Object>) intradayAnalysis.get("stockBasic");
            }
            return null;
        } catch (Exception e) {
            log.warn("获取股票{}基本信息失败: {}", stockCode, e.getMessage());
            return null;
        }
    }

    /**
     * 评估技术指标
     */
    private Map<String, Object> evaluateTechnicalIndicators(String stockCode) {
        try {
            List<Map<String, Object>> stockData = pythonScriptService.getStockKlineData(stockCode);
            Map<String, Object> indicators = pythonScriptService.calculateTechnicalIndicators(stockData);
            
            double score = 5.0; // 基础分
            Map<String, Object> result = new HashMap<>();
            
            // 评估趋势
            if (indicators.containsKey("近5日指标")) {
                List<Map<String, Object>> recentData = (List<Map<String, Object>>) indicators.get("近5日指标");
                if (!recentData.isEmpty()) {
                    Map<String, Object> latest = recentData.get(recentData.size() - 1);
                    
                    // MA趋势评分
                    Double ma5 = getDoubleValue(latest.get("ma5"));
                    Double ma20 = getDoubleValue(latest.get("ma20"));
                    Double close = getDoubleValue(latest.get("close"));
                    
                    if (ma5 != null && ma20 != null && close != null) {
                        if (close > ma5 && ma5 > ma20) {
                            score += 1.5; // 多头排列
                        } else if (close > ma5) {
                            score += 0.5; // 短期向上
                        }
                    }
                    
                    // RSI评分
                    Double rsi = getDoubleValue(latest.get("rsi"));
                    if (rsi != null) {
                        if (rsi > 30 && rsi < 70) {
                            score += 1.0; // RSI在合理区间
                        } else if (rsi < 30) {
                            score += 0.5; // 超卖
                        }
                    }
                    
                    // MACD评分
                    Double macd = getDoubleValue(latest.get("macd"));
                    Double macdSignal = getDoubleValue(latest.get("macd_signal"));
                    if (macd != null && macdSignal != null && macd > macdSignal) {
                        score += 1.0; // MACD金叉
                    }
                }
            }
            
            result.put("score", Math.min(score, 10.0));
            result.put("details", "技术面评估");
            return result;
            
        } catch (Exception e) {
            log.warn("评估股票{}技术指标失败: {}", stockCode, e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("score", 5.0);
            result.put("details", "技术面评估失败");
            return result;
        }
    }

    /**
     * 评估基本面
     */
    private Map<String, Object> evaluateFundamentals(String stockCode) {
        try {
            Map<String, Object> financialData = pythonScriptService.getFinancialAnalysisData(stockCode);
            
            double score = 5.0; // 基础分
            Map<String, Object> result = new HashMap<>();
            
            // 这里可以根据财务数据进行评分
            // 简化处理，给一个基础评分
            score += 1.0;
            
            result.put("score", Math.min(score, 10.0));
            result.put("details", "基本面评估");
            return result;
            
        } catch (Exception e) {
            log.warn("评估股票{}基本面失败: {}", stockCode, e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("score", 5.0);
            result.put("details", "基本面评估失败");
            return result;
        }
    }

    /**
     * 评估资金面
     */
    private Map<String, Object> evaluateMoneyFlow(String stockCode) {
        try {
            List<Map<String, Object>> moneyFlowData = pythonScriptService.getMoneyFlowData(stockCode);
            
            double score = 5.0; // 基础分
            Map<String, Object> result = new HashMap<>();
            
            // 评估资金流向
            if (!moneyFlowData.isEmpty()) {
                // 简化处理，检查主力资金流向
                score += 1.0;
            }
            
            result.put("score", Math.min(score, 10.0));
            result.put("details", "资金面评估");
            return result;
            
        } catch (Exception e) {
            log.warn("评估股票{}资金面失败: {}", stockCode, e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("score", 5.0);
            result.put("details", "资金面评估失败");
            return result;
        }
    }

    /**
     * 计算综合评分
     */
    private double calculateTotalScore(double technicalScore, 
                                     double fundamentalScore, 
                                     double moneyFlowScore) {
        // 加权平均：技术面40%，基本面35%，资金面25%
        return technicalScore * 0.4 + fundamentalScore * 0.35 + moneyFlowScore * 0.25;
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
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        return null;
    }
}
