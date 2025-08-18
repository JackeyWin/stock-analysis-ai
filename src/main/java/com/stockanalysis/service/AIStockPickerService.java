package com.stockanalysis.service;

import com.stockanalysis.model.DailyRecommendation;
import com.stockanalysis.model.StockRecommendationDetail;
import com.stockanalysis.tools.StockDataTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AI选股核心服务
 */
@Slf4j
@Service
public class AIStockPickerService {

    private final PolicyHotspotService policyHotspotService;
    private final StockScreeningService stockScreeningService;
    private final StockAnalysisAI stockAnalysisAI;
    private final StockDataTool stockDataTool;

    public AIStockPickerService(PolicyHotspotService policyHotspotService,
                               StockScreeningService stockScreeningService,
                               StockAnalysisAI stockAnalysisAI,
                               StockDataTool stockDataTool) {
        this.policyHotspotService = policyHotspotService;
        this.stockScreeningService = stockScreeningService;
        this.stockAnalysisAI = stockAnalysisAI;
        this.stockDataTool = stockDataTool;
    }

    /**
     * 执行AI选股流程
     */
    public CompletableFuture<DailyRecommendation> performAIStockPicking() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("开始执行AI选股流程");
                
                // 1. 获取政策热点和行业趋势
                Map<String, String> hotspots = policyHotspotService.getPolicyAndIndustryHotspots().get();
                log.info("政策热点获取完成");
                
                // 2. 筛选优质股票池
                Map<String, List<Map<String, Object>>> qualityStocks = stockScreeningService.screenQualityStocks(hotspots).get();
                log.info("筛选到{}只优质股票", qualityStocks.size());
                
                // 3. AI分析和遴选
                Map<String, List<StockRecommendationDetail>> sectorRecommendations = performAIAnalysisAndSelection(qualityStocks, hotspots);
                int totalRecommendations = sectorRecommendations.values().stream().mapToInt(List::size).sum();
                log.info("AI分析完成，推荐{}只股票", totalRecommendations);
                
                // 5. 生成每日推荐
                DailyRecommendation dailyRecommendation = generateDailyRecommendation(hotspots, sectorRecommendations);
                
                log.info("AI选股流程完成");
                return dailyRecommendation;
                
            } catch (Exception e) {
                log.error("AI选股流程失败: {}", e.getMessage(), e);
                return createErrorRecommendation("AI选股流程失败: " + e.getMessage());
            }
        });
    }

    /**
     * 执行AI分析和遴选
     */
    private Map<String, List<StockRecommendationDetail>> performAIAnalysisAndSelection(
            Map<String, List<Map<String, Object>>> qualityStocks, 
            Map<String, String> hotspots) {
        
        log.info("开始AI分析和遴选，候选股票{}只", qualityStocks.size());
        
        Map<String, List<StockRecommendationDetail>> recommendationsBySector = new HashMap<>();
        
        // 初始化每个行业的推荐列表
        for (String sector : qualityStocks.keySet()) {
            recommendationsBySector.put(sector, new ArrayList<>());
        }
        
        // 并行分析股票（限制并发数避免过载）
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (Map.Entry<String, List<Map<String, Object>>> entry : qualityStocks.entrySet()) {
            String sector = entry.getKey();
            List<Map<String, Object>> sectorStocks = entry.getValue();
            
            // 为每个行业创建并行分析任务
            CompletableFuture<Void> sectorFuture = CompletableFuture.runAsync(() -> {
                List<StockRecommendationDetail> sectorRecommendations = new ArrayList<>();
                
                // 限制每个行业分析的股票数量
                List<Map<String, Object>> limitedStocks = sectorStocks.stream().limit(10).collect(Collectors.toList());
                
                List<CompletableFuture<StockRecommendationDetail>> stockFutures = limitedStocks.stream()
                        .map(stock -> CompletableFuture.supplyAsync(() -> 
                            analyzeStockWithAI((String) stock.get("stockCode"), hotspots.get(sector))))
                        .collect(Collectors.toList());
                
                // 等待该行业所有股票分析完成
                for (CompletableFuture<StockRecommendationDetail> future : stockFutures) {
                    try {
                        StockRecommendationDetail recommendation = future.get();
                        if (recommendation != null && recommendation.getScore() >= 7.5) {
                            sectorRecommendations.add(recommendation);
                        }
                    } catch (Exception e) {
                        log.warn("{}行业股票分析失败: {}", sector, e.getMessage());
                    }
                }
                
                // 按评分排序
                sectorRecommendations.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
                
                // 将该行业的推荐数据放入结果映射
                recommendationsBySector.put(sector, sectorRecommendations);
            });
            
            futures.add(sectorFuture);
        }
        
        // 等待所有行业分析完成
        for (CompletableFuture<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                log.warn("行业分析失败: {}", e.getMessage());
            }
        }
        
        return recommendationsBySector;
    }

    /**
     * 使用AI分析单只股票
     */
    private StockRecommendationDetail analyzeStockWithAI(String stockCode, String goodReasonString) {
        try {
            log.debug("AI分析股票: {}", stockCode);
            
            // 构建分析提示词
            String analysisPrompt = buildStockAnalysisPrompt(stockCode, goodReasonString);
            
            // 调用AI进行分析
            String aiAnalysis = stockAnalysisAI.analyzeGeneralMarket(analysisPrompt);
            
            // 解析AI分析结果
            return parseAIAnalysisResult(stockCode, aiAnalysis);
            
        } catch (Exception e) {
            log.warn("AI分析股票{}失败: {}", stockCode, e.getMessage());
            return null;
        }
    }

    /**
     * 构建股票分析提示词
     */
    private String buildStockAnalysisPrompt(String stockCode, String goodReasonString) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("请作为专业的股票分析师，基于以下信息对股票").append(stockCode).append("进行全面分析：\n\n");
        
        // 添加政策热点信息
        prompt.append("该股票最新政策利好因素\n");
        prompt.append(goodReasonString).append("\n\n");
        
        // 使用工具获取股票数据
        prompt.append("请使用可用的工具获取该股票的详细数据，包括：\n");
        prompt.append("1. K线数据和技术指标\n");
        prompt.append("2. 财务分析和基本面数据\n");
        prompt.append("3. 资金流向和融资融券数据\n");
        prompt.append("4. 相关新闻和市场情绪\n");
        prompt.append("5. 同业对比\n");
        
        prompt.append("基于以上信息，请从以下角度进行分析：\n");
        prompt.append("1. 投资价值评估（1-10分）\n");
        prompt.append("2. 推荐理由（结合政策、行业、技术、基本面）\n");
        prompt.append("3. 风险等级评估（低/中/高）\n");
        prompt.append("4. 投资时间建议（短期/中期/长期）\n");
        prompt.append("5. 目标价格预测\n");
        prompt.append("6. 所属领域/行业分类\n\n");
        
        prompt.append("请以JSON格式返回分析结果：\n");
        prompt.append("{\n");
        prompt.append("  \"score\": 评分,\n");
        prompt.append("  \"rating\": \"推荐等级\",\n");
        prompt.append("  \"sector\": \"所属领域\",\n");
        prompt.append("  \"recommendationReason\": \"推荐理由\",\n");
        prompt.append("  \"riskLevel\": \"风险等级\",\n");
        prompt.append("  \"investmentPeriod\": \"投资时间\",\n");
        prompt.append("  \"targetPrice\": 目标价格,\n");
        prompt.append("  \"technicalAnalysis\": \"技术面分析\",\n");
        prompt.append("  \"fundamentalAnalysis\": \"基本面分析\",\n");
        prompt.append("  \"newsAnalysis\": \"消息面分析\"\n");
        prompt.append("}");
        
        return prompt.toString();
    }

    /**
     * 解析AI分析结果
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
                    detail.setTargetPrice(null);
                }
            }
            
            // 获取股票名称
            String stockName = getStockName(stockCode);
            detail.setStockName(stockName);
            
            return detail;
            
        } catch (Exception e) {
            log.warn("解析AI分析结果失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从JSON字符串中提取值
     */
    private String extractJsonValue(String json, String key) {
        return extractJsonValue(json, key, null);
    }

    private String extractJsonValue(String json, String key, String defaultValue) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
            
            // 尝试数字格式
            pattern = "\"" + key + "\"\\s*:\\s*([0-9.]+)";
            p = java.util.regex.Pattern.compile(pattern);
            m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
            
        } catch (Exception e) {
            log.debug("提取JSON值失败: {}", e.getMessage());
        }
        return defaultValue;
    }

    /**
     * 获取股票名称
     */
    private String getStockName(String stockCode) {
        try {
            String intradayData = stockDataTool.getIntradayAnalysis(stockCode);
            if (intradayData.contains("股票名称:")) {
                String[] parts = intradayData.split("股票名称:");
                if (parts.length > 1) {
                    String namePart = parts[1].split("\n")[0].trim();
                    return namePart;
                }
            }
        } catch (Exception e) {
            log.debug("获取股票{}名称失败: {}", stockCode, e.getMessage());
        }
        return "股票" + stockCode;
    }



    /**
     * 生成每日推荐
     */
    private DailyRecommendation generateDailyRecommendation(Map<String, String> hotspots, 
                                                           Map<String, List<StockRecommendationDetail>> sectorRecommendations) {
        
        DailyRecommendation recommendation = new DailyRecommendation();
        
        // 基本信息
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        recommendation.setRecommendationId("AI_PICK_" + today.replace("-", ""));
        recommendation.setRecommendationDate(today);
        recommendation.setCreateTime(LocalDateTime.now());
        recommendation.setStatus("ACTIVE");
        recommendation.setVersion(1);
        
        // 市场概况和热点
        recommendation.setPolicyHotspots((String) hotspots.get("policyHotspots"));
        recommendation.setIndustryHotspots((String) hotspots.get("industryHotspots"));
        recommendation.setMarketOverview((String) hotspots.get("hotspotAnalysis"));
        
        // 推荐股票
        List<StockRecommendationDetail> allRecommendations = new ArrayList<>();
        int sortOrder = 1;
        
        for (Map.Entry<String, List<StockRecommendationDetail>> entry : sectorRecommendations.entrySet()) {
            for (StockRecommendationDetail stock : entry.getValue()) {
                stock.setSortOrder(sortOrder++);
                stock.setIsHot(stock.getScore() >= 8.5);
                allRecommendations.add(stock);
            }
        }
        
        recommendation.setRecommendedStocks(allRecommendations);
        
        // 生成总结和观点
        recommendation.setSummary(generateSummary(sectorRecommendations));
        recommendation.setAnalystView(generateAnalystView(allRecommendations));
        recommendation.setRiskWarning(generateRiskWarning());
        
        return recommendation;
    }

    /**
     * 生成推荐总结
     */
    private String generateSummary(Map<String, List<StockRecommendationDetail>> sectorRecommendations) {
        StringBuilder summary = new StringBuilder();
        summary.append("本日AI选股共推荐").append(sectorRecommendations.size()).append("个领域的优质股票：\n\n");
        
        for (Map.Entry<String, List<StockRecommendationDetail>> entry : sectorRecommendations.entrySet()) {
            summary.append("【").append(entry.getKey()).append("】领域推荐")
                   .append(entry.getValue().size()).append("只股票，");
            
            List<StockRecommendationDetail> topStocks = entry.getValue().stream()
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .limit(3)
                    .collect(Collectors.toList());
            
            summary.append("重点关注：");
            for (int i = 0; i < topStocks.size(); i++) {
                if (i > 0) summary.append("、");
                summary.append(topStocks.get(i).getStockName())
                       .append("(").append(topStocks.get(i).getStockCode()).append(")");
            }
            summary.append("\n");
        }
        
        return summary.toString();
    }

    /**
     * 生成分析师观点
     */
    private String generateAnalystView(List<StockRecommendationDetail> recommendations) {
        StringBuilder view = new StringBuilder();
        view.append("【AI分析师观点】\n\n");
        view.append("基于当前政策环境和市场热点，本次选股重点关注以下投资主线：\n\n");
        
        // 统计各领域推荐数量
        Map<String, Long> sectorCount = recommendations.stream()
                .collect(Collectors.groupingBy(StockRecommendationDetail::getSector, Collectors.counting()));
        
        sectorCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> {
                    view.append("• ").append(entry.getKey()).append("领域：")
                        .append("推荐").append(entry.getValue()).append("只股票，")
                        .append("符合当前政策导向和市场趋势\n");
                });
        
        view.append("\n投资建议：建议投资者根据自身风险偏好和资金情况，")
            .append("重点关注评分8分以上的优质标的，")
            .append("并注意分散投资，控制单一标的仓位。");
        
        return view.toString();
    }

    /**
     * 生成风险提示
     */
    private String generateRiskWarning() {
        return "【风险提示】\n" +
               "1. 本推荐基于AI分析生成，仅供参考，不构成投资建议\n" +
               "2. 股市有风险，投资需谨慎，请根据自身情况做出投资决策\n" +
               "3. 建议设置止损位，控制投资风险\n" +
               "4. 市场环境变化较快，请及时关注相关信息更新\n" +
               "5. 分散投资，避免集中持仓带来的风险";
    }

    /**
     * 创建错误推荐
     */
    private DailyRecommendation createErrorRecommendation(String errorMessage) {
        DailyRecommendation recommendation = new DailyRecommendation();
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        
        recommendation.setRecommendationId("ERROR_" + today.replace("-", ""));
        recommendation.setRecommendationDate(today);
        recommendation.setCreateTime(LocalDateTime.now());
        recommendation.setStatus("ERROR");
        recommendation.setVersion(1);
        recommendation.setSummary("AI选股服务暂时不可用");
        recommendation.setAnalystView(errorMessage);
        recommendation.setRecommendedStocks(new ArrayList<>());
        
        return recommendation;
    }
}
