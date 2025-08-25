package com.stockanalysis.service;

import com.stockanalysis.model.DailyRecommendation;
import com.stockanalysis.model.StockRecommendationDetail;
import com.stockanalysis.service.DailyRecommendationStorageService;
import com.stockanalysis.tools.MarketResearchTools;
import com.stockanalysis.tools.StockPoolTools;
import com.stockanalysis.tools.StockDataTool;
import dev.langchain4j.service.AiServices;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * AI选股智能体服务
 * 实现用户需求的完整选股流程
 */
@Slf4j
@Service
public class AIStockAgentService {

    private final MarketResearchTools marketResearchTools;
    private final StockPoolTools stockPoolTools;
    private final StockDataTool stockDataTool;
    private final StockAnalysisAI stockAnalysisAI;
    private final DailyRecommendationStorageService dailyRecommendationStorageService;

    public AIStockAgentService(MarketResearchTools marketResearchTools,
                              StockPoolTools stockPoolTools,
                              StockDataTool stockDataTool,
                              StockAnalysisAI stockAnalysisAI,
                              DailyRecommendationStorageService dailyRecommendationStorageService) {
        this.marketResearchTools = marketResearchTools;
        this.stockPoolTools = stockPoolTools;
        this.stockDataTool = stockDataTool;
        this.stockAnalysisAI = stockAnalysisAI;
        this.dailyRecommendationStorageService = dailyRecommendationStorageService;
    }

    /**
     * 执行AI选股智能体流程
     * @return 每日推荐结果
     */
    // public CompletableFuture<DailyRecommendation> performAIStockAgentProcess() {
    //     return CompletableFuture.supplyAsync(() -> {
    //         try {
    //             log.info("开始执行AI选股智能体流程");
                
    //             // 1. 查询最新的政策方向与行业热点
    //             Map<String, Object> hotspots = getLatestHotspots();
    //             log.info("政策热点获取完成");
                
    //             // 2. 自行调用工具去查询政策或热点收益的股票池
    //             List<String> stockPool = getStockPoolFromHotspots(hotspots);
    //             log.info("筛选到{}只热点相关股票", stockPool.size());
                
    //             // 3. AI调用工具对相关的股票进行一一分析，遴选出优质的潜力股票
    //             List<StockRecommendationDetail> recommendations = analyzeStocks(stockPool, hotspots);
    //             log.info("AI分析完成，推荐{}只股票", recommendations.size());
                
    //             // 4. 分1-2个领域，每个领域优选出3-5家公司，写出推荐理由
    //             Map<String, List<StockRecommendationDetail>> sectorRecommendations = categorizeBySector(recommendations);
                
    //             // 5. 生成每日推荐
    //             DailyRecommendation dailyRecommendation = generateDailyRecommendation(hotspots, sectorRecommendations);
                
    //             // 6. 保存每日推荐到数据库
    //             dailyRecommendationStorageService.saveDailyRecommendation(dailyRecommendation);
    //             log.info("每日推荐已保存到数据库");
                
    //             log.info("AI选股智能体流程完成");
    //             return dailyRecommendation;
                
    //         } catch (Exception e) {
    //             log.error("AI选股智能体流程失败: {}", e.getMessage(), e);
    //             return createErrorRecommendation("AI选股智能体流程失败: " + e.getMessage());
    //         }
    //     });
    // }

    // /**
    //  * 查询最新的政策方向与行业热点
    //  * @return 政策热点信息
    //  */
    // private Map<String, Object> getLatestHotspots() {
    //     Map<String, Object> hotspots = new HashMap<>();
        
    //     try {
    //         // 查询最新的政策方向
    //         String policyHotspots = marketResearchTools.searchPolicyUpdates("", "中国", 5);
    //         hotspots.put("policyHotspots", policyHotspots);
            
    //         // 查询最新的行业热点
    //         String industryHotspots = marketResearchTools.searchIndustryTrends("A股市场", 5);
    //         hotspots.put("industryHotspots", industryHotspots);
            
    //         log.info("获取到政策热点和行业热点");
    //     } catch (Exception e) {
    //         log.error("获取政策热点失败: {}", e.getMessage(), e);
    //         hotspots.put("policyHotspots", "获取政策热点失败");
    //         hotspots.put("industryHotspots", "获取行业热点失败");
    //     }
        
    //     return hotspots;
    // }


    /**
     * AI调用工具对相关的股票进行一一分析，遴选出优质的潜力股票
     * @param stockPool 股票池
     * @param hotspots 政策热点信息
     * @return 推荐股票列表
     */
    private List<StockRecommendationDetail> analyzeStocks(List<String> stockPool, Map<String, Object> hotspots) {
        List<StockRecommendationDetail> recommendations = new ArrayList<>();
        
        // 限制分析数量，避免过载
        int maxAnalysisCount = Math.min(stockPool.size(), 20);
        
        // 并行分析股票
        List<CompletableFuture<StockRecommendationDetail>> futures = stockPool.stream()
                .limit(maxAnalysisCount)
                .map(stockCode -> CompletableFuture.supplyAsync(() -> analyzeStockWithAI(stockCode, hotspots)))
                .collect(Collectors.toList());
        
        // 等待所有分析完成
        for (CompletableFuture<StockRecommendationDetail> future : futures) {
            try {
                StockRecommendationDetail recommendation = future.get();
                if (recommendation != null && recommendation.getScore() >= 7.5) {
                    recommendations.add(recommendation);
                }
            } catch (Exception e) {
                log.warn("股票分析失败: {}", e.getMessage());
            }
        }
        
        // 按评分排序
        recommendations.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        
        return recommendations;
    }

    /**
     * 使用AI分析单只股票
     * @param stockCode 股票代码
     * @param hotspots 政策热点信息
     * @return 股票推荐详情
     */
    private StockRecommendationDetail analyzeStockWithAI(String stockCode, Map<String, Object> hotspots) {
        try {
            log.debug("AI分析股票: {}", stockCode);
            
            // 获取股票分析所需的各种数据
            String technicalIndicatorsJson = stockDataTool.calculateTechnicalIndicators(stockCode);
            String recentStockData = stockDataTool.getStockKlineData(stockCode);
            String newsData = stockDataTool.getStockNews(stockCode);
            String moneyFlowData = stockDataTool.getMoneyFlowData(stockCode);
            String marginTradingData = stockDataTool.getMarginTradingData(stockCode);
            String intradayAnalysis = stockDataTool.getIntradayAnalysis(stockCode);
            String peerComparison = stockDataTool.getPeerComparison(stockCode);
            String financialAnalysis = stockDataTool.getFinancialAnalysis(stockCode);
            String conceptsAndIndustries = stockDataTool.getCoreTagsData(stockCode);
            
            // 简化的板块和大盘技术指标（实际应用中应获取真实的板块和大盘数据）
            String boardTechnicalIndicatorsJson = "暂无板块数据";
            String marketTechnicalIndicatorsJson = "暂无大盘数据";
            
            // 获取当前时间
            String currentTime = java.time.LocalDateTime.now().toString();
            
            // 调用AI进行分析
            String aiAnalysis = stockAnalysisAI.analyzeStock(
                stockCode,
                technicalIndicatorsJson,
                boardTechnicalIndicatorsJson,
                marketTechnicalIndicatorsJson,
                recentStockData,
                newsData,
                moneyFlowData,
                marginTradingData,
                intradayAnalysis,
                currentTime,
                peerComparison,
                financialAnalysis,
                conceptsAndIndustries
            );
            
            // 解析AI分析结果
            return parseAIAnalysisResult(stockCode, aiAnalysis);
            
        } catch (Exception e) {
            log.warn("AI分析股票{}失败: {}", stockCode, e.getMessage());
            return null;
        }
    }

    /**
     * 解析AI分析结果
     * @param stockCode 股票代码
     * @param aiAnalysis AI分析结果
     * @return 股票推荐详情
     */
    private StockRecommendationDetail parseAIAnalysisResult(String stockCode, String aiAnalysis) {
        try {
            // 简化解析，实际应该使用JSON解析
            StockRecommendationDetail detail = new StockRecommendationDetail();
            detail.setStockCode(stockCode);
            detail.setRecommendTime(LocalDateTime.now());
            
            // 从AI分析中提取信息（简化处理）
            if (aiAnalysis.contains("\"score\"")) {
                // 尝试提取评分
                String scoreStr = extractJsonValue(aiAnalysis, "score");
                if (scoreStr != null) {
                    try {
                        detail.setScore(Double.parseDouble(scoreStr));
                    } catch (NumberFormatException e) {
                        detail.setScore(7.0); // 默认评分
                    }
                }
            } else {
                detail.setScore(7.0); // 默认评分
            }
            
            detail.setRating(extractJsonValue(aiAnalysis, "rating", "推荐"));
            detail.setSector(extractJsonValue(aiAnalysis, "sector", "综合"));
            detail.setRecommendationReason(extractJsonValue(aiAnalysis, "recommendationReason", "AI分析推荐"));
            detail.setRiskLevel(extractJsonValue(aiAnalysis, "riskLevel", "中"));
            detail.setInvestmentPeriod(extractJsonValue(aiAnalysis, "investmentPeriod", "中期"));
            detail.setTechnicalAnalysis(extractJsonValue(aiAnalysis, "technicalAnalysis", "技术面分析"));
            detail.setFundamentalAnalysis(extractJsonValue(aiAnalysis, "fundamentalAnalysis", "基本面分析"));
            detail.setNewsAnalysis(extractJsonValue(aiAnalysis, "newsAnalysis", "消息面分析"));
            
            // 设置目标价格
            String targetPriceStr = extractJsonValue(aiAnalysis, "targetPrice");
            if (targetPriceStr != null) {
                try {
                    detail.setTargetPrice(Double.parseDouble(targetPriceStr));
                } catch (NumberFormatException e) {
                    detail.setTargetPrice(0.0);
                }
            }
            
            return detail;
            
        } catch (Exception e) {
            log.warn("解析AI分析结果失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 提取JSON值
     * @param json JSON字符串
     * @param key 键
     * @return 值
     */
    private String extractJsonValue(String json, String key) {
        return extractJsonValue(json, key, null);
    }

    /**
     * 提取JSON值
     * @param json JSON字符串
     * @param key 键
     * @param defaultValue 默认值
     * @return 值
     */
    private String extractJsonValue(String json, String key, String defaultValue) {
        try {
            String keyPattern = "\"" + key + "\"\\s*:\\s*";
            int keyIndex = json.indexOf(keyPattern);
            if (keyIndex < 0) {
                return defaultValue;
            }
            
            int valueStart = keyIndex + keyPattern.length();
            char firstChar = json.charAt(valueStart);
            
            if (firstChar == '"') {
                // 字符串值
                int valueEnd = json.indexOf('"', valueStart + 1);
                if (valueEnd > valueStart) {
                    return json.substring(valueStart + 1, valueEnd);
                }
            } else if (Character.isDigit(firstChar) || firstChar == '-') {
                // 数字值
                int valueEnd = valueStart;
                while (valueEnd < json.length() && 
                       (Character.isDigit(json.charAt(valueEnd)) || json.charAt(valueEnd) == '.' || json.charAt(valueEnd) == '-')) {
                    valueEnd++;
                }
                return json.substring(valueStart, valueEnd);
            }
            
            return defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 按领域分类推荐
     * @param recommendations 推荐股票列表
     * @return 按领域分类的推荐
     */
    private Map<String, List<StockRecommendationDetail>> categorizeBySector(List<StockRecommendationDetail> recommendations) {
        return recommendations.stream()
                .collect(Collectors.groupingBy(
                    detail -> detail.getSector() != null ? detail.getSector() : "未分类",
                    Collectors.collectingAndThen(
                        Collectors.toList(),
                        list -> list.stream()
                                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                                .limit(5)
                                .collect(Collectors.toList())
                    )
                ));
    }
}