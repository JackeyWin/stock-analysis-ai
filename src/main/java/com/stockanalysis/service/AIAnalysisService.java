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
                                       Map<String, Object> intradayAnalysis,
                                       Map<String, Object> peerComparison,
                                       Map<String, Object> financialAnalysis) {
        
        try {
            // 准备数据
            String technicalIndicatorsJson = formatDataAsJson(technicalIndicators);
            String recentStockDataJson = formatRecentStockData(stockData);
            String newsDataJson = formatDataAsJson(newsData);
            String moneyFlowDataJson = formatDataAsJson(moneyFlowData);
            String marginTradingDataJson = formatDataAsJson(marginTradingData);
            String marketTechnicalIndicatorsJson = formatDataAsJson(marketTechnicalIndicators);
            String boardTechnicalIndicatorsJson = formatDataAsJson(boardTechnicalIndicators);
            String intradayAnalysisJson = formatDataAsJson(intradayAnalysis);
            String peerComparisonJson = formatDataAsJson(peerComparison);
            String financialAnalysisJson = formatDataAsJson(financialAnalysis);

            log.debug("开始AI分析股票: {}", stockCode);
            
            // 获取当前时间
            String currentTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            // 从财务分析数据中提取概念和行业信息（如果有的话）
            String conceptsAndIndustries = extractConceptsAndIndustries(financialAnalysis);
            
            log.debug("提取到概念和行业信息");
            
            // 使用LangChain4j调用AI分析，让大模型决定是否调用工具
            String aiResponse = stockAnalysisAI.analyzeStock(
                    stockCode,
                    technicalIndicatorsJson,
                    boardTechnicalIndicatorsJson,
                    marketTechnicalIndicatorsJson,
                    recentStockDataJson,
                    newsDataJson,
                    moneyFlowDataJson,
                    marginTradingDataJson,
                    intradayAnalysisJson,
                    currentTime,
                    peerComparisonJson,
                    financialAnalysisJson,
                    conceptsAndIndustries
            );
//            String aiResponse = "";

            log.debug("AI分析完成，响应长度: {}", aiResponse != null ? aiResponse.length() : 0);
            
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
        // 降低解析日志噪音
        log.debug("开始解析AI响应");
        
        AIAnalysisResult result = new AIAnalysisResult();
        result.setFullAnalysis(aiResponse);
        
        // 提取"公司基本面分析"、"操作策略"、"盘面分析"和"行业趋势和政策导向"
        log.debug("开始提取各个分析部分");
        
        String companyFundamentalAnalysis = extractSection(aiResponse, "公司基本面分析");
        // 不打印内容
        
        String operationStrategy = extractSection(aiResponse, "操作策略");
        
        
        String intradayOperations = extractSection(aiResponse, "盘面分析");
        
        
        String industryPolicyOrientation = extractSection(aiResponse, "行业趋势和政策导向");
        
        
        result.setCompanyFundamentalAnalysis(companyFundamentalAnalysis);
        result.setIndustryPolicyOrientation(industryPolicyOrientation);
        result.setOperationStrategy(operationStrategy);
        result.setIntradayOperations(intradayOperations);
        
        log.debug("AI响应解析完成");
        return result;
    }

    /**
     * 提取特定部分的内容
     */
    private String extractSection(String text, String sectionName) {
        // 预处理文本，统一格式
        text = preprocessText(text);
        
        log.debug("🔍 extractSection 开始提取 '{}'", sectionName);
        log.debug("🔍 预处理后的文本长度: {}", text.length());
        
        // 首先尝试简单的方法：查找【sectionName】到下一个【】之间的内容
        String sectionStart = "【" + sectionName + "】";
        int startIndex = text.indexOf(sectionStart);
        if (startIndex != -1) {
            // 找到当前部分的开始位置
            int contentStart = startIndex + sectionStart.length();
            
            // 查找下一个【】标记的位置
            int nextSectionIndex = -1;
            for (int i = contentStart; i < text.length(); i++) {
                if (text.charAt(i) == '【') {
                    // 检查是否是完整的【】标记
                    int endBracketIndex = text.indexOf('】', i);
                    if (endBracketIndex != -1) {
                        nextSectionIndex = i;
                        break;
                    }
                }
            }
            
            // 提取内容
            int contentEnd = (nextSectionIndex != -1) ? nextSectionIndex : text.length();
            String result = text.substring(contentStart, contentEnd).trim();
            
            log.debug("✅ 使用手动解析成功匹配 '{}': 原始长度={}", sectionName, result.length());
            log.debug("🔍 手动解析匹配的原始内容:\n{}", result);
            
            result = cleanExtractedContent(result);
            if (!result.isEmpty() && result.length() > 10) {
                log.debug("✅ 成功提取部分 '{}': 最终长度={}", sectionName, result.length());
                return result;
            }
        }
        
        // 如果简单方法失败，尝试其他格式的正则表达式
        String[] patterns = {
            // 格式1: ### 【公司基本面分析】... 到下一个### 【...】或文本结束
            "### \\s*【" + sectionName + "】\\s*([\\s\\S]*?)(?=### \\s*【[^】]+】|$)",
            // 格式2: - 趋势分析: [内容] (标准格式)
            "- " + sectionName + ":\\s*([^\\n]*(?:\\n(?!- [\\u4e00-\\u9fa5]+:|#### \\d+\\.|\\*\\*[\\u4e00-\\u9fa5]+\\*\\*:)[^\\n]*)*)",
            // 格式3: #### 数字. 趋势分析: [内容]
            "#### \\d+\\. " + sectionName + ":\\s*([^\\n]*(?:\\n(?!#### \\d+\\.|\\n\\n)[^\\n]*)*)",
            // 格式4: **趋势分析**: [内容]
            "\\*\\*" + sectionName + "\\*\\*:\\s*([^\\n]*(?:\\n(?!\\*\\*[\\u4e00-\\u9fa5]+\\*\\*:|#### \\d+\\.|\\n\\n)[^\\n]*)*)",
            // 格式5: 简单的 趋势分析: [内容]
            "(?:^|\\n)" + sectionName + ":\\s*([^\\n]*(?:\\n(?![\\u4e00-\\u9fa5]+:|#### |\\*\\*|\\n)[^\\n]*)*)"
        };
        
        for (int i = 0; i < patterns.length; i++) {
            String patternStr = patterns[i];
            Pattern altPattern = Pattern.compile(patternStr, Pattern.DOTALL | Pattern.MULTILINE);
            Matcher altMatcher = altPattern.matcher(text);
            if (altMatcher.find()) {
                String result = altMatcher.group(1).trim();
                log.debug("✅ 使用模式{}成功匹配 '{}': 原始长度={}", i, sectionName, result.length());
                log.debug("🔍 模式{}匹配的原始内容: {}", i, result);
                
                result = cleanExtractedContent(result);
                if (!result.isEmpty() && result.length() > 10) { // 确保提取到有意义的内容
                    log.debug("✅ 成功提取部分 '{}': 最终长度={}", sectionName, result.length());
                    return result;
                } else {
                    log.warn("⚠️ 模式{}匹配成功但内容无效: 长度={}", i, result.length());
                }
            } else {
                log.debug("❌ 模式{}未匹配到 '{}'", i, sectionName);
            }
        }
        
        log.warn("⚠️ 未能通过正则表达式提取部分 '{}'，尝试关键词搜索", sectionName);
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
        
        log.debug("🔍 cleanExtractedContent 输入: {}", content);
        
        // 移除markdown格式
        content = content.replaceAll("\\*\\*", "");
        
        // 移除可能混入的其他部分标题（更精确的匹配）
        content = content.replaceAll("\\n\\n- [\\u4e00-\\u9fa5]+[：:].*", ""); // 只移除包含冒号的标题行
        content = content.replaceAll("\\n#### \\d+\\. [\\u4e00-\\u9fa5]+:.*", "");
        content = content.replaceAll("\\n【[^】]+】.*", ""); // 移除【】格式的其他标题
        
        // 清理多余的连续换行，但保留单个换行符
        content = content.replaceAll("\\n\\s*\\n\\s*\\n+", "\n\n");
        
        // 按行处理，但保留以-开头的内容行
        String[] lines = content.split("\n");
        StringBuilder result = new StringBuilder();
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            // 保留非空行，包括以-开头的内容行
            if (!trimmedLine.isEmpty()) {
                if (result.length() > 0) {
                    result.append("\n");
                }
                result.append(trimmedLine);
            }
        }
        
        content = result.toString();
        
        // 如果内容太长，可能包含了其他部分，尝试截断
        if (content.length() > 1000) {
            // 查找可能的分割点
            String[] splitPoints = {"。", "；", "!", "！"};
            for (String point : splitPoints) {
                int index = content.indexOf(point, 800);
                if (index > 0 && index < 1000) {
                    content = content.substring(0, index + 1);
                    break;
                }
            }
        }
        
        log.debug("🔍 cleanExtractedContent 输出: {}", content);
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
                    line.startsWith("**") ||
                    line.matches("【[^】]+】.*")) { // 检查【】格式的新部分
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
     * 从财务分析数据中提取概念和行业信息
     */
    private String extractConceptsAndIndustries(Map<String, Object> financialAnalysis) {
        if (financialAnalysis == null) {
            return "无概念和行业信息";
        }
        
        StringBuilder result = new StringBuilder();
        
        // 尝试提取概念信息
        if (financialAnalysis.containsKey("concepts")) {
            Object concepts = financialAnalysis.get("concepts");
            if (concepts instanceof List) {
                List<?> conceptList = (List<?>) concepts;
                if (!conceptList.isEmpty()) {
                    result.append("概念题材: ");
                    for (int i = 0; i < Math.min(conceptList.size(), 10); i++) {
                        if (i > 0) result.append("、");
                        result.append(conceptList.get(i));
                    }
                    result.append("\n");
                }
            }
        }
        
        // 尝试提取行业信息
        if (financialAnalysis.containsKey("industries")) {
            Object industries = financialAnalysis.get("industries");
            if (industries instanceof List) {
                List<?> industryList = (List<?>) industries;
                if (!industryList.isEmpty()) {
                    result.append("所属行业: ");
                    for (int i = 0; i < Math.min(industryList.size(), 5); i++) {
                        if (i > 0) result.append("、");
                        result.append(industryList.get(i));
                    }
                    result.append("\n");
                }
            }
        }
        
        return result.length() > 0 ? result.toString() : "无概念和行业信息";
    }

    /**
     * 创建错误结果
     */
    private AIAnalysisResult createErrorResult(String errorMessage) {
        AIAnalysisResult result = new AIAnalysisResult();
        result.setFullAnalysis("分析失败: " + errorMessage);
        result.setOperationStrategy("分析失败");
        result.setIntradayOperations("分析失败");
        return result;
    }
}