package com.stockanalysis.service;

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
                            .map(stockCode -> evaluateStock(stockCode, sector, hotspots.get(sector)))
                            .filter(Objects::nonNull)
                            .filter(stock -> (Double) stock.get("score") >= 7.0) // 评分7分以上
                            .sorted((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")))
                            .limit(5) // 每个行业取前5只
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
    public CompletableFuture<List<Map<String, Object>>> screenStocksBySector(String sector, Map<String, String> hotspots) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("开始筛选{}行业股票", sector);
                
                // 获取行业相关股票
                List<String> sectorStocks = getSectorStocks(sector);
                
                // 评估并筛选
                List<Map<String, Object>> qualityStocks = sectorStocks.parallelStream()
                        .map(stockCode -> evaluateStock(stockCode, sector, hotspots.get(sector)))
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
                } else {
                    // 如果没有从描述中获取到股票代码，则获取行业的成分股
                    List<String> sectorStocks = getSectorStocks(sector);
                    secList.addAll(sectorStocks);
                }
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
        return evaluateStock(stockCode, null, null);
    }
    
    /**
     * 评估单只股票（带热点内容）
     * @param stockCode 股票代码
     * @param hotspots 热点内容
     */
    public Map<String, Object> evaluateStock(String stockCode, String sector, String goodReasonString) {
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
            double fundamentalScore = (Double) evaluateFundamentals(stockCode, stockInfo).get("score");
            
            // 4. 资金面评估
            Map<String, Object> moneyFlowResult = evaluateMoneyFlow(stockCode);
            double moneyFlowScore = moneyFlowResult != null ? (Double) moneyFlowResult.get("score") : 5.0;
            
            List<String> concepts = new ArrayList<>();
            // 通过EastMoneyCoreTags获取股票概念信息
            try {
                Map<String, Object> coreTagsData = pythonScriptService.getCoreTagsData(stockCode);
                if (coreTagsData != null && coreTagsData.containsKey("concepts")) {
                    List<String> conceptList = (List<String>) coreTagsData.get("concepts");
                    concepts.addAll(conceptList);
                }
            } catch (Exception e) {
                log.warn("获取股票{}概念信息失败: {}", stockCode, e.getMessage());
            }

            double goodReasonScore = evaluateScoreByGoodReason(concepts, goodReasonString);
            
            // 6. 计算综合评分
            double totalScore = calculateTotalScore(technicalScore, fundamentalScore, moneyFlowScore, goodReasonScore);
            
            // 7. 组装结果
            Map<String, Object> result = new HashMap<>(stockInfo);
            result.put("technicalScore", technicalScore);
            result.put("fundamentalScore", fundamentalScore);
            result.put("moneyFlowScore", moneyFlowScore);
            result.put("hotpotScore", goodReasonScore);
            result.put("score", totalScore);
            
            log.debug("股票{}评估完成, 综合评分: {}", stockCode, totalScore);
            return result;
            
        } catch (Exception e) {
            log.warn("评估股票{}时发生异常: {}", stockCode, e.getMessage());
            return null;
        }
    }
    
    private double evaluateScoreByGoodReason(List<String> concepts, String goodReasonString) {
        //基础分5分
        double score = 5.0;
        
        // 如果没有概念信息或利好内容为空，则不加分
        if (concepts == null || concepts.isEmpty() || goodReasonString == null || goodReasonString.isEmpty()) {
            return score;
        }
        
        // 遍历股票概念，检查是否在利好内容中出现
        for (String concept : concepts) {
            // 如果概念名称在利好内容中出现，则加分
            if (goodReasonString.contains(concept)) {
                score += 1.0;  // 每匹配一个概念加1分
            }
        }
        
        // 限制最大加分为5分
        return Math.min(score, 10.0);
    }

    /**
     * 获取股票基本信息
     */
    private Map<String, Object> getStockBasicInfo(String stockCode) {
        try {
            // 直接使用Python脚本获取股票基本信息
            return pythonScriptService.getStockBasicData(stockCode);
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
            StringBuilder details = new StringBuilder("技术面评估: ");
            Map<String, Object> result = new HashMap<>();
            
            // 1. 趋势评估 (权重30%)
            double trendScore = evaluateTrendIndicators(indicators);
            score += trendScore * 0.3;
            details.append(String.format("趋势%.1f分 ", trendScore));
            
            // 2. 动量评估 (权重25%)
            double momentumScore = evaluateMomentumIndicators(indicators);
            score += momentumScore * 0.25;
            details.append(String.format("动量%.1f分 ", momentumScore));
            
            // 3. 成交量评估 (权重25%)
            double volumeScore = evaluateVolumeIndicators(indicators);
            score += volumeScore * 0.25;
            details.append(String.format("成交量%.1f分 ", volumeScore));
            
            // 4. 波动性评估 (权重10%)
            double volatilityScore = evaluateVolatilityIndicators(indicators);
            score += volatilityScore * 0.1;
            details.append(String.format("波动性%.1f分 ", volatilityScore));
            
            // 5. 趋势强度评估 (权重10%)
            double strengthScore = evaluateStrengthIndicators(indicators);
            score += strengthScore * 0.1;
            details.append(String.format("趋势强度%.1f分 ", strengthScore));
            
            result.put("score", Math.min(score, 10.0));
            result.put("details", details.toString());
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
     * 评估趋势指标
     * @param indicators 技术指标数据
     * @return 评分 (0-10分)
     */
    private double evaluateTrendIndicators(Map<String, Object> indicators) {
        double score = 5.0; // 基础分
        
        try {
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
                            score += 2.0; // 多头排列
                        } else if (close > ma5) {
                            score += 1.0; // 短期向上
                        } else if (close < ma5 && ma5 < ma20) {
                            score -= 2.0; // 空头排列
                        } else if (close < ma5) {
                            score -= 1.0; // 短期向下
                        }
                    }
                    
                    // 布林带评分
                    Double bollingerUpper = getDoubleValue(latest.get("bollinger_upper"));
                    Double bollingerLower = getDoubleValue(latest.get("bollinger_lower"));
                    if (close != null && bollingerUpper != null && bollingerLower != null) {
                        if (close > bollingerUpper) {
                            score += 1.0; // 股价突破上轨
                        } else if (close < bollingerLower) {
                            score -= 1.0; // 股价跌破下轨
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("评估趋势指标时发生异常: {}", e.getMessage());
        }
        
        return Math.min(Math.max(score, 0.0), 10.0); // 限制在0-10分之间
    }
    
    /**
     * 评估动量指标
     * @param indicators 技术指标数据
     * @return 评分 (0-10分)
     */
    private double evaluateMomentumIndicators(Map<String, Object> indicators) {
        double score = 5.0; // 基础分
        
        try {
            // 评估动量
            if (indicators.containsKey("近5日指标")) {
                List<Map<String, Object>> recentData = (List<Map<String, Object>>) indicators.get("近5日指标");
                if (!recentData.isEmpty()) {
                    Map<String, Object> latest = recentData.get(recentData.size() - 1);
                    
                    // RSI评分
                    Double rsi = getDoubleValue(latest.get("rsi"));
                    if (rsi != null) {
                        if (rsi > 30 && rsi < 70) {
                            score += 1.5; // RSI在合理区间
                        } else if (rsi < 30) {
                            score += 1.0; // 超卖
                        } else if (rsi > 70) {
                            score -= 1.0; // 超买
                        }
                    }
                    
                    // MACD评分
                    Double macd = getDoubleValue(latest.get("macd"));
                    Double macdSignal = getDoubleValue(latest.get("macd_signal"));
                    if (macd != null && macdSignal != null && macd > macdSignal) {
                        score += 1.5; // MACD金叉
                    } else if (macd != null && macdSignal != null && macd < macdSignal) {
                        score -= 1.5; // MACD死叉
                    }
                    
                    // KDJ评分
                    Double kdjK = getDoubleValue(latest.get("kdj_k"));
                    Double kdjD = getDoubleValue(latest.get("kdj_d"));
                    if (kdjK != null && kdjD != null) {
                        if (kdjK > 70 && kdjD > 70) {
                            score -= 1.0; // 超买
                        } else if (kdjK < 30 && kdjD < 30) {
                            score += 1.0; // 超卖
                        }
                        
                        // KDJ金叉死叉
                        if (recentData.size() >= 2) {
                            Map<String, Object> previous = recentData.get(recentData.size() - 2);
                            Double prevKdjK = getDoubleValue(previous.get("kdj_k"));
                            Double prevKdjD = getDoubleValue(previous.get("kdj_d"));
                            
                            if (prevKdjK != null && prevKdjD != null) {
                                if (kdjK > kdjD && prevKdjK <= prevKdjD) {
                                    score += 1.5; // KDJ金叉
                                } else if (kdjK < kdjD && prevKdjK >= prevKdjD) {
                                    score -= 1.5; // KDJ死叉
                                }
                            }
                        }
                    }
                    
                    // MFI评分（资金流量指标）
                    Double mfi = getDoubleValue(latest.get("mfi"));
                    if (mfi != null) {
                        if (mfi > 80) {
                            score -= 1.0; // 超买
                        } else if (mfi < 20) {
                            score += 1.0; // 超卖
                        }
                    }
                    
                    // CCI评分（顺势指标）
                    Double cci = getDoubleValue(latest.get("cci"));
                    if (cci != null) {
                        if (cci > 100) {
                            score += 1.5; // 强势上涨
                        } else if (cci < -100) {
                            score -= 1.5; // 强势下跌
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("评估动量指标时发生异常: {}", e.getMessage());
        }
        
        return Math.min(Math.max(score, 0.0), 10.0); // 限制在0-10分之间
    }
    
    /**
     * 评估成交量指标
     * @param indicators 技术指标数据
     * @return 评分 (0-10分)
     */
    private double evaluateVolumeIndicators(Map<String, Object> indicators) {
        double score = 5.0; // 基础分
        
        try {
            // 评估成交量
            if (indicators.containsKey("近5日指标")) {
                List<Map<String, Object>> recentData = (List<Map<String, Object>>) indicators.get("近5日指标");
                if (!recentData.isEmpty()) {
                    Map<String, Object> latest = recentData.get(recentData.size() - 1);
                    
                    // OBV评分（能量潮指标）
                    Double obv = getDoubleValue(latest.get("obv"));
                    if (obv != null && recentData.size() >= 2) {
                        Map<String, Object> previous = recentData.get(recentData.size() - 2);
                        Double prevObv = getDoubleValue(previous.get("obv"));
                        
                        if (prevObv != null) {
                            if (obv > prevObv) {
                                score += 1.5; // 资金流入
                            } else if (obv < prevObv) {
                                score -= 1.5; // 资金流出
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("评估成交量指标时发生异常: {}", e.getMessage());
        }
        
        return Math.min(Math.max(score, 0.0), 10.0); // 限制在0-10分之间
    }
    
    /**
     * 评估波动性指标
     * @param indicators 技术指标数据
     * @return 评分 (0-10分)
     */
    private double evaluateVolatilityIndicators(Map<String, Object> indicators) {
        double score = 5.0; // 基础分
        
        try {
            // 评估波动性
            if (indicators.containsKey("近5日指标")) {
                List<Map<String, Object>> recentData = (List<Map<String, Object>>) indicators.get("近5日指标");
                if (!recentData.isEmpty()) {
                    Map<String, Object> latest = recentData.get(recentData.size() - 1);
                    
                    // ATR评分（波动性）
                    Double atr = getDoubleValue(latest.get("atr"));
                    Double close = getDoubleValue(latest.get("close"));
                    if (atr != null && close != null) {
                        double atrRatio = atr / close;
                        if (atrRatio > 0.05) {
                            score -= 1.5; // 波动性较大
                        } else if (atrRatio < 0.02) {
                            score += 1.5; // 波动性较小
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("评估波动性指标时发生异常: {}", e.getMessage());
        }
        
        return Math.min(Math.max(score, 0.0), 10.0); // 限制在0-10分之间
    }
    
    /**
     * 评估趋势强度指标
     * @param indicators 技术指标数据
     * @return 评分 (0-10分)
     */
    private double evaluateStrengthIndicators(Map<String, Object> indicators) {
        double score = 5.0; // 基础分
        
        try {
            // 评估趋势强度
            if (indicators.containsKey("近5日指标")) {
                List<Map<String, Object>> recentData = (List<Map<String, Object>>) indicators.get("近5日指标");
                if (!recentData.isEmpty()) {
                    Map<String, Object> latest = recentData.get(recentData.size() - 1);
                    
                    // ADX评分（趋势强度）
                    Double adx = getDoubleValue(latest.get("adx"));
                    Double plusDi = getDoubleValue(latest.get("plus_di"));
                    Double minusDi = getDoubleValue(latest.get("minus_di"));
                    if (adx != null && plusDi != null && minusDi != null) {
                        if (adx > 25) { // 趋势较强
                            if (plusDi > minusDi) {
                                score += 2.0; // 上升趋势
                            } else {
                                score -= 2.0; // 下降趋势
                            }
                        } else if (adx > 20) { // 趋势中等
                            if (plusDi > minusDi) {
                                score += 1.0; // 上升趋势
                            } else {
                                score -= 1.0; // 下降趋势
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("评估趋势强度指标时发生异常: {}", e.getMessage());
        }
        
        return Math.min(Math.max(score, 0.0), 10.0); // 限制在0-10分之间
    }

    /**
     * 评估基本面
     * @param stockInfo 
     */
    private Map<String, Object> evaluateFundamentals(String stockCode, Map<String,Object> stockInfo) {
        try {
            Map<String, Object> financialAnalysisData = pythonScriptService.getFinancialAnalysisData(stockCode);
            
            double score = 5.0; // 基础分
            StringBuilder details = new StringBuilder("基本面评估: ");
            Map<String, Object> result = new HashMap<>();
            
            // 检查数据是否有效
            if (financialAnalysisData == null || financialAnalysisData.isEmpty()) {
                result.put("score", score);
                result.put("details", "基本面评估: 无财务数据");
                return result;
            }
            
            // 获取财务指标数据（按脚本真实输出做多键兜底）
            @SuppressWarnings("unchecked")
            Map<String, Object> financialData = (Map<String, Object>) getMapByAnyKey(
                financialAnalysisData,
                "financial_data",      // 英文键
                "财务数据",             // 中文常见
                "财务指标",             // 直接就是指标分组
                "data"                 // 其他可能包装
            );
            // 若直接就是"财务指标"分组，把其作为financialData传给后续逻辑
            if (financialData != null && !financialData.containsKey("财务指标") && financialAnalysisData.containsKey("财务指标")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> indicators = (Map<String, Object>) financialAnalysisData.get("财务指标");
                financialData = new java.util.HashMap<>();
                financialData.put("财务指标", indicators);
            }
            if (financialData == null || financialData.isEmpty()) {
                result.put("score", score);
                result.put("details", "基本面评估: 财务数据为空");
                return result;
            }
            
            // 1. 盈利能力评估 (权重30%)
            double profitabilityScore = evaluateProfitability(financialData);
            score += profitabilityScore * 0.3;
            details.append(String.format("盈利能力%.1f分 ", profitabilityScore));
            
            // 2. 成长性评估 (权重20%)
            double growthScore = evaluateGrowth(financialData);
            score += growthScore * 0.2;
            details.append(String.format("成长性%.1f分 ", growthScore));
            
            // 3. 财务健康度评估 (权重20%)
            double healthScore = evaluateFinancialHealth(financialData);
            score += healthScore * 0.2;
            details.append(String.format("财务健康%.1f分 ", healthScore));
            
            // 4. 现金流评估 (权重15%)
            double cashFlowScore = evaluateCashFlow(financialData);
            score += cashFlowScore * 0.15;
            details.append(String.format("现金流%.1f分 ", cashFlowScore));
            
            // 5. 估值水平评估 (权重15%)
            double valuationScore = evaluateValuation(financialData);
            score += valuationScore * 0.15;
            details.append(String.format("估值%.1f分 ", valuationScore));
            
            result.put("score", Math.min(score, 10.0));
            result.put("details", details.toString());
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
     * 评估盈利能力
     * @param financialData 财务数据
     * @return 评分 (0-10分)
     */
    private double evaluateProfitability(Map<String, Object> financialData) {
        double score = 5.0; // 基础分
        
        try {
            // 获取财务指标数据（多键兜底）
            @SuppressWarnings("unchecked")
            Map<String, Object> indicators = (Map<String, Object>) getMapByAnyKey(
                financialData,
                "财务指标",
                "indicators",
                "指标"
            );
            if (indicators == null || indicators.isEmpty()) {
                return score;
            }
            
            // 1. 毛利率评估
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> grossMarginList = (List<Map<String, Object>>) getByAnyKey(
                indicators,
                "毛利率(%)",
                "毛利率",
                "GrossMargin"
            );
            if (grossMarginList != null && !grossMarginList.isEmpty()) {
                // 获取最新一期数据
                Map<String, Object> latest = grossMarginList.get(0);
                Double grossMargin = parseDoubleValue(latest.get("数值"));
                if (grossMargin != null) {
                    if (grossMargin > 40) {
                        score += 1.5; // 毛利率很高
                    } else if (grossMargin > 30) {
                        score += 1.0; // 毛利率较高
                    } else if (grossMargin > 20) {
                        score += 0.5; // 毛利率一般
                    } else if (grossMargin < 10) {
                        score -= 1.0; // 毛利率很低
                    }
                }
            }
            
            // 2. 净利率评估
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> netMarginList = (List<Map<String, Object>>) getByAnyKey(
                indicators,
                "净利率(%)",
                "净利率",
                "NetMargin"
            );
            if (netMarginList != null && !netMarginList.isEmpty()) {
                // 获取最新一期数据
                Map<String, Object> latest = netMarginList.get(0);
                Double netMargin = parseDoubleValue(latest.get("数值"));
                if (netMargin != null) {
                    if (netMargin > 20) {
                        score += 1.5; // 净利率很高
                    } else if (netMargin > 10) {
                        score += 1.0; // 净利率较高
                    } else if (netMargin > 5) {
                        score += 0.5; // 净利率一般
                    } else if (netMargin < 2) {
                        score -= 1.0; // 净利率很低
                    }
                }
            }
            
            // 3. ROE评估
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> roeList = (List<Map<String, Object>>) getByAnyKey(
                indicators,
                "净资产收益率(加权)(%)",
                "ROE(%)",
                "净资产收益率(%)",
                "ROE"
            );
            if (roeList != null && !roeList.isEmpty()) {
                // 获取最新一期数据
                Map<String, Object> latest = roeList.get(0);
                Double roe = parseDoubleValue(latest.get("数值"));
                if (roe != null) {
                    if (roe > 20) {
                        score += 1.5; // ROE很高
                    } else if (roe > 15) {
                        score += 1.0; // ROE较高
                    } else if (roe > 10) {
                        score += 0.5; // ROE一般
                    } else if (roe < 5) {
                        score -= 1.0; // ROE很低
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("评估盈利能力时发生异常: {}", e.getMessage());
        }
        
        return Math.min(Math.max(score, 0.0), 10.0); // 限制在0-10分之间
    }
    
    /**
     * 评估成长性
     * @param financialData 财务数据
     * @return 评分 (0-10分)
     */
    private double evaluateGrowth(Map<String, Object> financialData) {
        double score = 5.0; // 基础分
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> indicators = (Map<String, Object>) getMapByAnyKey(
                financialData,
                "财务指标",
                "indicators",
                "指标"
            );
            if (indicators == null || indicators.isEmpty()) {
                return score;
            }
            
            // 1. 营业收入增长率评估
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> revenueList = (List<Map<String, Object>>) getFirstListByAnyKey(
                indicators,
                "营业收入",
                "Revenue"
            );
            if (revenueList != null && revenueList.size() >= 2) {
                // 获取最新两期数据
                Map<String, Object> latest = revenueList.get(0);
                Map<String, Object> previous = revenueList.get(1);
                
                Double latestRevenue = parseDoubleValue(latest.get("数值"));
                Double previousRevenue = parseDoubleValue(previous.get("数值"));
                
                if (latestRevenue != null && previousRevenue != null && previousRevenue > 0) {
                    double growthRate = (latestRevenue - previousRevenue) / previousRevenue * 100;
                    if (growthRate > 20) {
                        score += 1.5; // 增长很快
                    } else if (growthRate > 10) {
                        score += 1.0; // 增长较快
                    } else if (growthRate > 5) {
                        score += 0.5; // 增长一般
                    } else if (growthRate < 0) {
                        score -= 1.0; // 负增长
                    }
                }
            }
            
            // 2. 净利润增长率评估
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> profitList = (List<Map<String, Object>>) getFirstListByAnyKey(
                indicators,
                "净利润",
                "NetProfit"
            );
            if (profitList != null && profitList.size() >= 2) {
                // 获取最新两期数据
                Map<String, Object> latest = profitList.get(0);
                Map<String, Object> previous = profitList.get(1);
                
                Double latestProfit = parseDoubleValue(latest.get("数值"));
                Double previousProfit = parseDoubleValue(previous.get("数值"));
                
                if (latestProfit != null && previousProfit != null && previousProfit > 0) {
                    double growthRate = (latestProfit - previousProfit) / previousProfit * 100;
                    if (growthRate > 20) {
                        score += 1.5; // 增长很快
                    } else if (growthRate > 10) {
                        score += 1.0; // 增长较快
                    } else if (growthRate > 5) {
                        score += 0.5; // 增长一般
                    } else if (growthRate < 0) {
                        score -= 1.0; // 负增长
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("评估成长性时发生异常: {}", e.getMessage());
        }
        
        return Math.min(Math.max(score, 0.0), 10.0); // 限制在0-10分之间
    }
    
    /**
     * 评估财务健康度
     * @param financialData 财务数据
     * @return 评分 (0-10分)
     */
    private double evaluateFinancialHealth(Map<String, Object> financialData) {
        double score = 5.0; // 基础分
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> indicators = (Map<String, Object>) getMapByAnyKey(
                financialData,
                "财务指标",
                "indicators",
                "指标"
            );
            if (indicators == null || indicators.isEmpty()) {
                return score;
            }
            
            // 1. 资产负债率评估
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> debtRatioList = (List<Map<String, Object>>) getByAnyKey(
                indicators,
                "资产负债率(%)",
                "资产负债率",
                "DebtRatio"
            );
            if (debtRatioList != null && !debtRatioList.isEmpty()) {
                // 获取最新一期数据
                Map<String, Object> latest = debtRatioList.get(0);
                Double debtRatio = parseDoubleValue(latest.get("数值"));
                if (debtRatio != null) {
                    if (debtRatio < 30) {
                        score += 1.5; // 负债率很低
                    } else if (debtRatio < 50) {
                        score += 1.0; // 负债率较低
                    } else if (debtRatio < 70) {
                        score += 0.5; // 负债率一般
                    } else if (debtRatio > 80) {
                        score -= 1.5; // 负债率很高
                    }
                }
            }
            
            // 2. 流动比率评估
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> currentRatioList = (List<Map<String, Object>>) getByAnyKey(
                indicators,
                "流动比率",
                "CurrentRatio"
            );
            if (currentRatioList != null && !currentRatioList.isEmpty()) {
                // 获取最新一期数据
                Map<String, Object> latest = currentRatioList.get(0);
                Double currentRatio = parseDoubleValue(latest.get("数值"));
                if (currentRatio != null) {
                    if (currentRatio > 2.0) {
                        score += 1.0; // 流动比率很好
                    } else if (currentRatio > 1.5) {
                        score += 0.5; // 流动比率较好
                    } else if (currentRatio < 1.0) {
                        score -= 1.0; // 流动比率不足
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("评估财务健康度时发生异常: {}", e.getMessage());
        }
        
        return Math.min(Math.max(score, 0.0), 10.0); // 限制在0-10分之间
    }
    
    /**
     * 评估现金流
     * @param financialData 财务数据
     * @return 评分 (0-10分)
     */
    private double evaluateCashFlow(Map<String, Object> financialData) {
        double score = 5.0; // 基础分
        
        try {
            // 获取现金流量数据
            Map<String, Object> cashFlow = (Map<String, Object>) financialData.get("现金流量");
            if (cashFlow == null || cashFlow.isEmpty()) {
                return score;
            }
            
            // 获取财务指标数据
            Map<String, Object> indicators = (Map<String, Object>) financialData.get("财务指标");
            if (indicators == null || indicators.isEmpty()) {
                return score;
            }
            
            // 1. 经营现金流与净利润比率评估
            List<Map<String, Object>> operatingCashFlowList = (List<Map<String, Object>>) cashFlow.get("经营活动产生的现金流量净额");
            List<Map<String, Object>> netProfitList = (List<Map<String, Object>>) indicators.get("净利润");
            
            if (operatingCashFlowList != null && !operatingCashFlowList.isEmpty() &&
                netProfitList != null && !netProfitList.isEmpty()) {
                // 获取最新一期数据
                Map<String, Object> latestCash = operatingCashFlowList.get(0);
                Map<String, Object> latestProfit = netProfitList.get(0);
                
                Double operatingCashFlow = parseDoubleValue(latestCash.get("数值"));
                Double netProfit = parseDoubleValue(latestProfit.get("数值"));
                
                if (operatingCashFlow != null && netProfit != null && netProfit > 0) {
                    double ratio = operatingCashFlow / netProfit;
                    if (ratio > 1.0) {
                        score += 1.5; // 现金流很好
                    } else if (ratio > 0.8) {
                        score += 1.0; // 现金流较好
                    } else if (ratio > 0.5) {
                        score += 0.5; // 现金流一般
                    } else if (ratio < 0) {
                        score -= 1.5; // 现金流为负
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("评估现金流时发生异常: {}", e.getMessage());
        }
        
        return Math.min(Math.max(score, 0.0), 10.0); // 限制在0-10分之间
    }
    
    /**
     * 评估估值水平
     * @param financialData 财务数据
     * @return 评分 (0-10分)
     */
    private double evaluateValuation(Map<String, Object> financialData) {
        double score = 5.0; // 基础分
        
        try {
            // 获取估值指标数据
            Map<String, Object> valuation = (Map<String, Object>) financialData.get("估值指标");
            if (valuation == null || valuation.isEmpty()) {
                return score;
            }
            
            // 1. PE评估
            List<Map<String, Object>> peList = (List<Map<String, Object>>) valuation.get("PE(动态)");
            if (peList != null && !peList.isEmpty()) {
                // 获取最新一期数据
                Map<String, Object> latest = peList.get(0);
                Double pe = parseDoubleValue(latest.get("数值"));
                if (pe != null) {
                    if (pe < 15) {
                        score += 1.5; // PE很低
                    } else if (pe < 25) {
                        score += 1.0; // PE较低
                    } else if (pe < 35) {
                        score += 0.5; // PE一般
                    } else if (pe > 50) {
                        score -= 1.5; // PE很高
                    }
                }
            }
            
            // 2. PB评估
            List<Map<String, Object>> pbList = (List<Map<String, Object>>) valuation.get("PB(最新)");
            if (pbList != null && !pbList.isEmpty()) {
                // 获取最新一期数据
                Map<String, Object> latest = pbList.get(0);
                Double pb = parseDoubleValue(latest.get("数值"));
                if (pb != null) {
                    if (pb < 1.5) {
                        score += 1.5; // PB很低
                    } else if (pb < 2.5) {
                        score += 1.0; // PB较低
                    } else if (pb < 3.5) {
                        score += 0.5; // PB一般
                    } else if (pb > 5.0) {
                        score -= 1.5; // PB很高
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("评估估值水平时发生异常: {}", e.getMessage());
        }
        
        return Math.min(Math.max(score, 0.0), 10.0); // 限制在0-10分之间
    }
    
    /**
     * 安全地解析Double值
     */
    private Double parseDoubleValue(Object value) {
        if (value == null) {
            return null;
        }
        
        try {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            } else if (value instanceof String) {
                String strValue = (String) value;
                // 移除可能的百分号和其他非数字字符
                strValue = strValue.replaceAll("[%亿万元,]", "");
                return Double.parseDouble(strValue);
            }
        } catch (NumberFormatException e) {
            log.warn("解析数值时发生异常: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * 评估资金面
     */
    private Map<String, Object> evaluateMoneyFlow(String stockCode) {
        try {
            List<Map<String, Object>> moneyFlowData = pythonScriptService.getMoneyFlowData(stockCode);
            List<Map<String, Object>> marginTradingData = pythonScriptService.getMarginTradingData(stockCode);
            
            double score = 5.0; // 基础分
            StringBuilder details = new StringBuilder("资金面评估: ");
            Map<String, Object> result = new HashMap<>();
            
            // 1. 资金流向评估 (权重60%)
            double moneyFlowScore = evaluateMoneyFlowData(moneyFlowData);
            score += moneyFlowScore * 0.6;
            details.append(String.format("资金流向%.1f分 ", moneyFlowScore));
            
            // 2. 融资融券评估 (权重40%)
            double marginTradingScore = evaluateMarginTradingData(marginTradingData);
            score += marginTradingScore * 0.4;
            details.append(String.format("融资融券%.1f分 ", marginTradingScore));
            
            result.put("score", Math.min(score, 10.0));
            result.put("details", details.toString());
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
     * 评估资金流向数据
     * @param moneyFlowData 资金流向数据
     * @return 评分 (0-10分)
     */
    private double evaluateMoneyFlowData(List<Map<String, Object>> moneyFlowData) {
        double score = 5.0; // 基础分
        
        try {
            if (moneyFlowData == null || moneyFlowData.isEmpty()) {
                return score;
            }
            
            // 获取最新一期数据
            Map<String, Object> latest = moneyFlowData.get(0);
            
            // 从资金数据中提取今日、5日、10日数据
            Map<String, Object> fundData = (Map<String, Object>) latest.get("资金数据");
            if (fundData == null) {
                return score;
            }
            
            List<Map<String, Object>> todayData = (List<Map<String, Object>>) fundData.get("今日");
            List<Map<String, Object>> fiveDayData = (List<Map<String, Object>>) fundData.get("5日");
            List<Map<String, Object>> tenDayData = (List<Map<String, Object>>) fundData.get("10日");
            
            if (todayData == null || todayData.isEmpty()) {
                return score;
            }
            
            // 初始化主力和超大单数据
            Map<String, Object> mainDataToday = null;
            Map<String, Object> superLargeDataToday = null;
            Map<String, Object> mainDataFiveDay = null;
            Map<String, Object> superLargeDataFiveDay = null;
            Map<String, Object> mainDataTenDay = null;
            Map<String, Object> superLargeDataTenDay = null;
            
            // 遍历今日资金数据，找出主力和超大单
            for (Map<String, Object> item : todayData) {
                String fundType = (String) item.get("资金类型");
                if ("主力".equals(fundType)) {
                    mainDataToday = item;
                } else if ("超大单".equals(fundType)) {
                    superLargeDataToday = item;
                }
            }
            
            // 遍历5日资金数据，找出主力和超大单
            if (fiveDayData != null) {
                for (Map<String, Object> item : fiveDayData) {
                    String fundType = (String) item.get("资金类型");
                    if ("主力".equals(fundType)) {
                        mainDataFiveDay = item;
                    } else if ("超大单".equals(fundType)) {
                        superLargeDataFiveDay = item;
                    }
                }
            }
            
            // 遍历10日资金数据，找出主力和超大单
            if (tenDayData != null) {
                for (Map<String, Object> item : tenDayData) {
                    String fundType = (String) item.get("资金类型");
                    if ("主力".equals(fundType)) {
                        mainDataTenDay = item;
                    } else if ("超大单".equals(fundType)) {
                        superLargeDataTenDay = item;
                    }
                }
            }
            
            // 1. 主力资金趋势评估
            Double mainNetRatioToday = null;
            Double mainNetRatioFiveDay = null;
            Double mainNetRatioTenDay = null;
            
            if (mainDataToday != null) {
                mainNetRatioToday = getDoubleValue(mainDataToday.get("净占比（%）"));
            }
            if (mainDataFiveDay != null) {
                mainNetRatioFiveDay = getDoubleValue(mainDataFiveDay.get("净占比（%）"));
            }
            if (mainDataTenDay != null) {
                mainNetRatioTenDay = getDoubleValue(mainDataTenDay.get("净占比（%）"));
            }
            
            // 综合评估主力资金趋势
            if (mainNetRatioToday != null && mainNetRatioFiveDay != null && mainNetRatioTenDay != null) {
                // 计算趋势得分
                double trendScore = 0.0;
                
                // 今日趋势
                if (mainNetRatioToday > 5) {
                    trendScore += 1.0;
                } else if (mainNetRatioToday < -5) {
                    trendScore -= 1.0;
                }
                
                // 5日趋势
                if (mainNetRatioFiveDay > 3) {
                    trendScore += 0.7;
                } else if (mainNetRatioFiveDay < -3) {
                    trendScore -= 0.7;
                }
                
                // 10日趋势
                if (mainNetRatioTenDay > 2) {
                    trendScore += 0.5;
                } else if (mainNetRatioTenDay < -2) {
                    trendScore -= 0.5;
                }
                
                // 趋势一致性加分
                if (mainNetRatioToday > 0 && mainNetRatioFiveDay > 0 && mainNetRatioTenDay > 0) {
                    trendScore += 0.5; // 持续流入
                } else if (mainNetRatioToday < 0 && mainNetRatioFiveDay < 0 && mainNetRatioTenDay < 0) {
                    trendScore -= 0.5; // 持续流出
                }
                
                score += trendScore;
            } else if (mainNetRatioToday != null) {
                // 只有今日数据时，使用原有逻辑
                if (mainNetRatioToday > 10) { // 10%
                    score += 2.0; // 主力资金大幅流入
                } else if (mainNetRatioToday > 5) { // 5%
                    score += 1.5; // 主力资金明显流入
                } else if (mainNetRatioToday > 2) { // 2%
                    score += 1.0; // 主力资金小幅流入
                } else if (mainNetRatioToday < -10) { // -10%
                    score -= 2.0; // 主力资金大幅流出
                } else if (mainNetRatioToday < -5) { // -5%
                    score -= 1.5; // 主力资金明显流出
                } else if (mainNetRatioToday < -2) { // -2%
                    score -= 1.0; // 主力资金小幅流出
                }
            } else if (mainDataToday != null) { // 如果没有占比数据，则使用净额数据
                Double mainNetAmount = getDoubleValue(mainDataToday.get("净流入额（万元）"));
                if (mainNetAmount != null) {
                    if (mainNetAmount > 10000) { // 1亿元 (单位是万元)
                        score += 2.0; // 主力资金大幅流入
                    } else if (mainNetAmount > 5000) { // 5000万元
                        score += 1.5; // 主力资金明显流入
                    } else if (mainNetAmount > 1000) { // 1000万元
                        score += 1.0; // 主力资金小幅流入
                    } else if (mainNetAmount < -10000) { // -1亿元
                        score -= 2.0; // 主力资金大幅流出
                    } else if (mainNetAmount < -5000) { // -5000万元
                        score -= 1.5; // 主力资金明显流出
                    } else if (mainNetAmount < -1000) { // -1000万元
                        score -= 1.0; // 主力资金小幅流出
                    }
                }
            }
            
            // 2. 超大单资金趋势评估
            Double superLargeNetRatioToday = null;
            Double superLargeNetRatioFiveDay = null;
            Double superLargeNetRatioTenDay = null;
            
            if (superLargeDataToday != null) {
                superLargeNetRatioToday = getDoubleValue(superLargeDataToday.get("净占比（%）"));
            }
            if (superLargeDataFiveDay != null) {
                superLargeNetRatioFiveDay = getDoubleValue(superLargeDataFiveDay.get("净占比（%）"));
            }
            if (superLargeDataTenDay != null) {
                superLargeNetRatioTenDay = getDoubleValue(superLargeDataTenDay.get("净占比（%）"));
            }
            
            // 综合评估超大单资金趋势
            if (superLargeNetRatioToday != null && superLargeNetRatioFiveDay != null && superLargeNetRatioTenDay != null) {
                // 计算趋势得分
                double trendScore = 0.0;
                
                // 今日趋势
                if (superLargeNetRatioToday > 3) {
                    trendScore += 0.7;
                } else if (superLargeNetRatioToday < -3) {
                    trendScore -= 0.7;
                }
                
                // 5日趋势
                if (superLargeNetRatioFiveDay > 2) {
                    trendScore += 0.5;
                } else if (superLargeNetRatioFiveDay < -2) {
                    trendScore -= 0.5;
                }
                
                // 10日趋势
                if (superLargeNetRatioTenDay > 1) {
                    trendScore += 0.3;
                } else if (superLargeNetRatioTenDay < -1) {
                    trendScore -= 0.3;
                }
                
                // 趋势一致性加分
                if (superLargeNetRatioToday > 0 && superLargeNetRatioFiveDay > 0 && superLargeNetRatioTenDay > 0) {
                    trendScore += 0.3; // 持续流入
                } else if (superLargeNetRatioToday < 0 && superLargeNetRatioFiveDay < 0 && superLargeNetRatioTenDay < 0) {
                    trendScore -= 0.3; // 持续流出
                }
                
                score += trendScore;
            } else if (superLargeNetRatioToday != null) {
                // 只有今日数据时，使用原有逻辑
                if (superLargeNetRatioToday > 5) { // 5%
                    score += 1.0; // 超大单资金流入
                } else if (superLargeNetRatioToday < -5) { // -5%
                    score -= 1.0; // 超大单资金流出
                }
            } else if (superLargeDataToday != null) { // 如果没有占比数据，则使用净额数据
                Double superLargeNetAmount = getDoubleValue(superLargeDataToday.get("净流入额（万元）"));
                if (superLargeNetAmount != null) {
                    if (superLargeNetAmount > 5000) { // 5000万元
                        score += 1.0; // 超大单资金流入
                    } else if (superLargeNetAmount < -5000) { // -5000万元
                        score -= 1.0; // 超大单资金流出
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("评估资金流向数据时发生异常: {}", e.getMessage());
        }
        
        return Math.min(Math.max(score, 0.0), 10.0); // 限制在0-10分之间
    }
    
    /**
     * 评估融资融券数据
     * @param marginTradingData 融资融券数据
     * @return 评分 (0-10分)
     */
    private double evaluateMarginTradingData(List<Map<String, Object>> marginTradingData) {
        double score = 5.0; // 基础分
        
        try {
            if (marginTradingData == null || marginTradingData.isEmpty()) {
                return score;
            }
            
            // 获取最新一期数据
            Map<String, Object> latest = marginTradingData.get(0);
            
            // 1. 融资余额变化评估
            Double rzBalance = getDoubleValue(latest.get("融资余额（亿元）"));
            if (rzBalance != null && marginTradingData.size() >= 2) {
                // 获取前一期数据
                Map<String, Object> previous = marginTradingData.get(1);
                Double previousRzBalance = getDoubleValue(previous.get("融资余额（亿元）"));
                
                if (previousRzBalance != null && previousRzBalance > 0) {
                    double changeRate = (rzBalance - previousRzBalance) / previousRzBalance * 100;
                    if (changeRate > 10) { // 增长超过10%
                        score += 2.0; // 融资余额大幅增加
                    } else if (changeRate > 5) { // 增长超过5%
                        score += 1.5; // 融资余额明显增加
                    } else if (changeRate > 2) { // 增长超过2%
                        score += 1.0; // 融资余额小幅增加
                    } else if (changeRate < -10) { // 下降超过10%
                        score -= 2.0; // 融资余额大幅减少
                    } else if (changeRate < -5) { // 下降超过5%
                        score -= 1.5; // 融资余额明显减少
                    } else if (changeRate < -2) { // 下降超过2%
                        score -= 1.0; // 融资余额小幅减少
                    }
                }
            }
            
            // 2. 融券余额变化评估
            Double rqBalance = getDoubleValue(latest.get("融券余额（万元）"));
            if (rqBalance != null && marginTradingData.size() >= 2) {
                // 获取前一期数据
                Map<String, Object> previous = marginTradingData.get(1);
                Double previousRqBalance = getDoubleValue(previous.get("融券余额（万元）"));
                
                if (previousRqBalance != null && previousRqBalance > 0) {
                    double changeRate = (rqBalance - previousRqBalance) / previousRqBalance * 100;
                    if (changeRate > 10) { // 增长超过10%
                        score -= 1.0; // 融券余额大幅增加（看空）
                    } else if (changeRate < -10) { // 下降超过10%
                        score += 1.0; // 融券余额大幅减少（看多）
                    }
                }
            }
            
            // 3. 融资净买入额评估
            Double rzNetBuy = getDoubleValue(latest.get("融资净买入额（万元）"));
            if (rzNetBuy != null) {
                if (rzNetBuy > 5000) { // 5000万元
                    score += 1.5; // 融资大幅净买入
                } else if (rzNetBuy > 1000) { // 1000万元
                    score += 1.0; // 融资明显净买入
                } else if (rzNetBuy < -5000) { // -5000万元
                    score -= 1.5; // 融资大幅净偿还
                } else if (rzNetBuy < -1000) { // -1000万元
                    score -= 1.0; // 融资明显净偿还
                }
            }
            
        } catch (Exception e) {
            log.warn("评估融资融券数据时发生异常: {}", e.getMessage());
        }
        
        return Math.min(Math.max(score, 0.0), 10.0); // 限制在0-10分之间
    }

    /**
     * 计算综合评分
     */
    private double calculateTotalScore(double technicalScore, 
                                     double fundamentalScore, 
                                     double moneyFlowScore,
                                     double goodReasonScore) {
        // 加权平均：技术面30%，基本面20%，资金面25%，利好内容调整评分25%
        return technicalScore * 0.3 + fundamentalScore * 0.2 + moneyFlowScore * 0.25 + goodReasonScore * 0.25;
    }

    // ===== 辅助读取工具（多键兜底）=====
    private Object getByAnyKey(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String k : keys) {
            if (map.containsKey(k)) return map.get(k);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMapByAnyKey(Map<String, Object> map, String... keys) {
        Object v = getByAnyKey(map, keys);
        if (v instanceof Map) {
            return (Map<String, Object>) v;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getFirstListByAnyKey(Map<String, Object> map, String... keys) {
        Object v = getByAnyKey(map, keys);
        if (v instanceof List) {
            return (List<Map<String, Object>>) v;
        }
        return null;
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
