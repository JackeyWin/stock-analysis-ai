package com.stockanalysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockanalysis.model.AIAnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI分析服务 - 使用LangChain4j框架
 */
@Slf4j
@Service
public class AIAnalysisService {

    private final StockAnalysisAI stockAnalysisAI;
    private final ObjectMapper objectMapper;

    public AIAnalysisService(StockAnalysisAI stockAnalysisAI, ObjectMapper objectMapper) {
        this.stockAnalysisAI = stockAnalysisAI;
        this.objectMapper = objectMapper;
    }

    /**
     * 进行股票AI分析
     */
    public AIAnalysisResult analyzeStock(String stockCode, 
                                       List<Map<String, Object>> stockData,
                                       Map<String, Object> marketTechnicalIndicators,
                                       Map<String, Object> boardTechnicalIndicators,
                                       Map<String, Object> technicalIndicators,
                                       List<Map<String, Object>> newsData,
                                       List<Map<String, Object>> moneyFlowData,
                                       List<Map<String, Object>> marginTradingData,
                                       Map<String, Object> intradayAnalysis) {
        
        try {
            // 准备数据
            String technicalIndicatorsJson = formatDataAsJson(technicalIndicators);
            String recentStockDataJson = formatRecentStockData(stockData);
            String newsDataJson = formatDataAsJson(newsData);
            String moneyFlowDataJson = formatDataAsJson(moneyFlowData);
            String marginTradingDataJson = formatDataAsJson(marginTradingData);
            String marketTechnicalIndicatorsJson = formatDataAsJson(marketTechnicalIndicators.get("近5日指标"));
            String boardTechnicalIndicatorsJson = formatDataAsJson(marketTechnicalIndicators.get("近5日指标"));
            String intradayAnalysisJson = formatDataAsJson(intradayAnalysis);

            log.info("开始AI分析股票: {}", stockCode);
            
            // 使用LangChain4j调用AI分析
            String aiResponse = stockAnalysisAI.analyzeStock(
                    stockCode,
                    technicalIndicatorsJson,
                    boardTechnicalIndicatorsJson,
                    marketTechnicalIndicatorsJson,
                    recentStockDataJson,
                    newsDataJson,
                    moneyFlowDataJson,
                    marginTradingDataJson,
                    intradayAnalysisJson
            );
            
            log.info("AI分析完成，响应长度: {}", aiResponse.length());
            log.info("分析结果: {}", aiResponse);
            
            // 解析AI响应
            return parseAIResponse(aiResponse);
            
        } catch (Exception e) {
            log.error("AI分析失败: {}", e.getMessage(), e);
            return createErrorResult("AI分析失败: " + e.getMessage());
        }
    }

    /**
     * 快速分析
     */
    public AIAnalysisResult quickAnalyze(String stockCode, Map<String, Object> technicalIndicators) {
        try {
            String technicalIndicatorsJson = formatDataAsJson(technicalIndicators);
            
            log.info("开始快速AI分析股票: {}", stockCode);
            
            String aiResponse = stockAnalysisAI.quickAnalyze(stockCode, technicalIndicatorsJson);
            
            log.info("快速AI分析完成");
            
            return parseAIResponse(aiResponse);
            
        } catch (Exception e) {
            log.error("快速AI分析失败: {}", e.getMessage(), e);
            return createErrorResult("快速AI分析失败: " + e.getMessage());
        }
    }

    /**
     * 风险评估
     */
    public String assessRisk(String stockCode, 
                           Map<String, Object> technicalIndicators,
                             List<Map<String, Object>> moneyFlowData,
                             List<Map<String, Object>> marginTradingData) {
        try {
            String technicalIndicatorsJson = formatDataAsJson(technicalIndicators);
            String moneyFlowDataJson = formatDataAsJson(moneyFlowData);
            String marginTradingDataJson = formatDataAsJson(marginTradingData);
            
            log.info("开始风险评估: {}", stockCode);
            
            return stockAnalysisAI.assessRisk(
                    stockCode,
                    technicalIndicatorsJson,
                    moneyFlowDataJson,
                    marginTradingDataJson
            );
            
        } catch (Exception e) {
            log.error("风险评估失败: {}", e.getMessage(), e);
            return "风险评估失败: " + e.getMessage();
        }
    }

    /**
     * 格式化数据为JSON字符串
     */
    private String formatDataAsJson(Object data) {
        if (data == null) {
            return "无数据";
        }
        
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.warn("数据格式化为JSON失败: {}", e.getMessage());
            return data.toString();
        }
    }

    /**
     * 格式化最近股价数据
     */
    private String formatRecentStockData(List<Map<String, Object>> stockData) {
        if (stockData == null || stockData.isEmpty()) {
            return "无股价数据";
        }
        
        StringBuilder result = new StringBuilder();
        int startIdx = Math.max(0, stockData.size() - 10);
        
        for (int i = startIdx; i < stockData.size(); i++) {
            Map<String, Object> data = stockData.get(i);
            result.append(String.format("日期: %s, 开盘: %s, 收盘: %s, 最高: %s, 最低: %s, 成交量: %s\n",
                    data.get("d"), data.get("o"), data.get("c"), data.get("h"), data.get("l"), data.get("v")));
        }
        
        return result.toString();
    }

    /**
     * 解析AI响应
     */
    private AIAnalysisResult parseAIResponse(String aiResponse) {
        AIAnalysisResult result = new AIAnalysisResult();
        result.setFullAnalysis(aiResponse);
        
        // 使用正则表达式提取各个部分
        result.setTrendAnalysis(extractSection(aiResponse, "趋势分析"));
        result.setTechnicalPattern(extractSection(aiResponse, "技术形态"));
        result.setMovingAverage(extractSection(aiResponse, "移动平均线"));
        result.setRsiAnalysis(extractSection(aiResponse, "RSI指标"));
        result.setPricePredict(extractSection(aiResponse, "价格预测"));
        result.setTradingAdvice(extractSection(aiResponse, "交易建议"));
        result.setIntradayOperations(extractSection(aiResponse, "盘中操作"));
        
        return result;
    }

    /**
     * 提取特定部分的内容
     */
    private String extractSection(String text, String sectionName) {
        // 预处理文本，统一格式
        text = preprocessText(text);
        
        // 尝试多种格式的正则表达式
        String[] patterns = {
            // 格式1: - 趋势分析: [内容] (标准格式)
            "- " + sectionName + ":\\s*([^\\n]*(?:\\n(?!- [\\u4e00-\\u9fa5]+:|#### \\d+\\.|\\*\\*[\\u4e00-\\u9fa5]+\\*\\*:)[^\\n]*)*)",
            // 格式2: #### 数字. 趋势分析: [内容]
            "#### \\d+\\. " + sectionName + ":\\s*([^\\n]*(?:\\n(?!#### \\d+\\.|\\n\\n)[^\\n]*)*)",
            // 格式3: **趋势分析**: [内容]
            "\\*\\*" + sectionName + "\\*\\*:\\s*([^\\n]*(?:\\n(?!\\*\\*[\\u4e00-\\u9fa5]+\\*\\*:|#### \\d+\\.|\\n\\n)[^\\n]*)*)",
            // 格式4: 简单的 趋势分析: [内容]
            "(?:^|\\n)" + sectionName + ":\\s*([^\\n]*(?:\\n(?![\\u4e00-\\u9fa5]+:|#### |\\*\\*|\\n)[^\\n]*)*)"
        };
        
        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr, Pattern.DOTALL | Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String result = matcher.group(1).trim();
                result = cleanExtractedContent(result);
                if (!result.isEmpty() && result.length() > 10) { // 确保提取到有意义的内容
                    return result;
                }
            }
        }
        
        // 如果都没匹配到，尝试简单的关键词搜索
        return extractByKeywordSearch(text, sectionName);
    }

    /**
     * 预处理文本
     */
    private String preprocessText(String text) {
        if (text == null) return "";
        
        // 移除多余的空行
        text = text.replaceAll("\\n\\s*\\n", "\n\n");
        
        // 确保每个部分都在新行开始
        text = text.replaceAll("([^\\n])\\n\\n- ", "$1\n\n- ");
        
        return text;
    }

    /**
     * 清理提取的内容
     */
    private String cleanExtractedContent(String content) {
        if (content == null) return "";
        
        // 移除markdown格式
        content = content.replaceAll("\\*\\*", "");
        
        // 移除可能混入的其他部分标题
        content = content.replaceAll("\\n\\n- [\\u4e00-\\u9fa5]+:.*", "");
        content = content.replaceAll("\\n#### \\d+\\. [\\u4e00-\\u9fa5]+:.*", "");
        
        // 移除多余的换行和空格
        content = content.replaceAll("\\n+", " ").trim();
        
        // 如果内容太长，可能包含了其他部分，尝试截断
        if (content.length() > 500) {
            // 查找可能的分割点
            String[] splitPoints = {"。", "；", "!", "！"};
            for (String point : splitPoints) {
                int index = content.indexOf(point, 300);
                if (index > 0 && index < 500) {
                    content = content.substring(0, index + 1);
                    break;
                }
            }
        }
        
        return content.trim();
    }

    /**
     * 通过关键词搜索提取内容
     */
    private String extractByKeywordSearch(String text, String sectionName) {
        String[] lines = text.split("\n");
        StringBuilder result = new StringBuilder();
        boolean found = false;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            // 检查是否包含目标关键词
            if (line.contains(sectionName) && line.contains(":")) {
                found = true;
                // 提取冒号后的内容
                int colonIndex = line.indexOf(":");
                if (colonIndex >= 0 && colonIndex < line.length() - 1) {
                    String content = line.substring(colonIndex + 1).trim();
                    if (!content.isEmpty()) {
                        result.append(content);
                    }
                }
                continue;
            }
            
            // 如果已经找到目标部分，继续收集内容直到下一个部分
            if (found) {
                // 检查是否到了下一个部分
                if (line.matches(".*[\\u4e00-\\u9fa5]+.*:.*") || 
                    line.startsWith("####") || 
                    line.startsWith("- ") ||
                    line.startsWith("**")) {
                    break;
                }
                
                if (!line.isEmpty()) {
                    if (result.length() > 0) {
                        result.append(" ");
                    }
                    result.append(line);
                }
            }
        }
        
        String finalResult = result.toString().trim();
        return finalResult.isEmpty() ? "未找到" + sectionName + "分析" : finalResult;
    }

    /**
     * 创建错误结果
     */
    private AIAnalysisResult createErrorResult(String errorMessage) {
        AIAnalysisResult result = new AIAnalysisResult();
        result.setFullAnalysis("分析失败: " + errorMessage);
        result.setTrendAnalysis("分析失败");
        result.setTechnicalPattern("分析失败");
        result.setMovingAverage("分析失败");
        result.setRsiAnalysis("分析失败");
        result.setPricePredict("分析失败");
        result.setTradingAdvice("分析失败");
        result.setIntradayOperations("分析失败");
        return result;
    }
}