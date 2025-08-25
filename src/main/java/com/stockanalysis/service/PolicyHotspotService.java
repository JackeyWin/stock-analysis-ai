package com.stockanalysis.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * 政策热点查询服务
 */
@Slf4j
@Service
public class PolicyHotspotService {

    private final WebClient webClient;
    private final StockAnalysisAI stockAnalysisAI;
    private final PythonScriptService pythonScriptService;

    public PolicyHotspotService(StockAnalysisAI stockAnalysisAI, PythonScriptService pythonScriptService) {
        this.stockAnalysisAI = stockAnalysisAI;
        this.pythonScriptService = pythonScriptService;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * 获取最新政策方向与行业热点
     */
    public CompletableFuture<Map<String, String>> getPolicyAndIndustryHotspots() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("开始获取政策热点");
                
                Map<String, String> result = new HashMap<>();
                
                // 1. 获取政策热点
                String policyHotspotsJson = getPolicyHotspots();
                log.debug("获取到的原始政策热点数据: {}", policyHotspotsJson);
                
                // 清理和预处理JSON字符串
                String cleanedJson = cleanJsonString(policyHotspotsJson);
                log.debug("清理后的JSON数据: {}", cleanedJson);
                
                ObjectMapper objectMapper = new ObjectMapper();
                try {
                    result = objectMapper.readValue(cleanedJson, new TypeReference<Map<String, String>>(){});     
                    log.info("成功解析政策热点JSON，共{}个行业", result.size());
                } catch (Exception e) {
                    log.error("解析政策热点JSON失败: {}", e.getMessage());
                    log.error("原始数据: {}", policyHotspotsJson);
                    log.error("清理后数据: {}", cleanedJson);
                    // 返回空结果而不是抛出异常
                    result = new HashMap<>();
                }   
                log.info("政策热点获取完成");
                return result;
                
            } catch (Exception e) {
                log.error("获取政策热点失败: {}", e.getMessage(), e);
                return createErrorResult("获取政策热点失败: " + e.getMessage());
            }
        });
    }

    /**
     * 获取政策热点
     */
    private String getPolicyHotspots() {
        try {
            log.debug("获取政策热点信息");
            
            // 使用AI分析当前政策环境和热点
            String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            // 获取行业列表（通过脚本，不再让AI调用工具）
            String sectorList;
            try {
                var sectors = pythonScriptService.getSectorList();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < sectors.size(); i++) {
                    var s = sectors.get(i);
                    String code = s.getOrDefault("code", "");
                    String name = s.getOrDefault("name", "");
                    if (name == null || name.isEmpty()) continue;
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(name);
                }
                sectorList = sb.toString();
            } catch (Exception ex) {
                sectorList = ""; // 出错时传空，AI需自行约束输出
            }
            
            String prompt = String.format(
                "你是一个专业的政策分析助手。请严格按照以下要求执行：\n\n" +
                "1. 使用searchPolicies工具获取最新政策信息，查询政策或行业信息只能用这个工具：\n" +
                "   - 区域：China\n" +
                "   - 政策领域：economic policy OR industrial policy\n" +
                "   - 最大返回结果数：10\n" +
                "   - 时间范围：day\n\n" +
                "2. 如果上述查询结果不足，依次添加以下关键词：\n" +
                "   - NDRC (List of Approved Projects)\n" +
                "   - MOF (Interest Subsidy Catalog)\n" +
                "   - MIIT (Catalog of Recommended Innovative Technologies)\n" +
                "   - NDA (List of Authorized Data Operators)\n" +
                "   - Joint Statement by Government Departments\n" +
                "   - Public Tendering\n\n" +
                "3. 已提供全量行业名称列表（必须严格使用以下列表中的行业名称，不得自创/改名）：\n" +
                "   %s\n\n" +
                "4. 根据政策信息筛选出政策利好的行业（仅使用上述行业名称）。\n\n" +
                "5. 输出格式要求：\n" +
                "   - 必须输出纯JSON格式，不能包含任何其他文字\n" +
                "   - 不能使用```json```或``````标记\n" +
                "   - 不能包含注释、说明或其他非JSON内容\n" +
                "   - JSON结构：{\"行业名称\":\"利好因素描述\"}\n" +
                "   - 利好因素要求：200字左右，包含政策发布时间、相关产业名称、热点概念名称\n" +
                "   - 如果涉及企业，一定要标注企业名称，一定要标注股票代码\n\n" +
                "6. 示例输出格式：\n" +
                "{\"半导体\":\"2025年8月22日，工信部发布半导体产业支持政策，重点支持国产替代...\",\"新能源\":\"2025年8月22日，发改委发布新能源补贴政策...\"}\n\n" +
                "7. 重要提醒：\n" +
                "   - 只输出JSON数据，不要任何其他内容\n" +
                "   - 确保JSON格式完全正确，可以被标准JSON解析器解析\n" +
                "   - 如果无法获取有效数据，返回空JSON对象：{}\n\n" +
                "当前日期：%s",
                sectorList,
                currentDate
            );
            
            return stockAnalysisAI.analyzeGeneralMarket(prompt);
            
        } catch (Exception e) {
            log.error("获取政策热点失败: {}", e.getMessage(), e);
            return "政策热点获取失败：" + e.getMessage();
        }
    }

    /**
     * 获取行业热点
     */
    private String getIndustryHotspots() {
        try {
            log.debug("获取行业热点信息");
            
            String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            String prompt = String.format(
                "请分析当前（%s）A股市场的行业热点和投资机会，包括：\n" +
                "1. 当前最热门的行业板块\n" +
                "2. 新兴产业发展趋势\n" +
                "3. 传统行业转型升级机会\n" +
                "4. 技术创新驱动的行业\n" +
                "5. 政策支持的重点行业\n" +
                "6. 各行业的投资价值分析\n" +
                "请重点分析具有投资潜力的行业和相关投资逻辑。\n\n" +
                "在分析过程中，请务必使用searchIndustryTrends工具获取最新的行业趋势信息，查询关键词为\"A股市场 行业热点\"，返回前5条结果。",
                currentDate
            );
            
            return stockAnalysisAI.analyzeGeneralMarket(prompt);
            
        } catch (Exception e) {
            log.error("获取行业热点失败: {}", e.getMessage(), e);
            return "行业热点获取失败：" + e.getMessage();
        }
    }

    /**
     * 获取市场热点
     */
    private String getMarketHotspots() {
        try {
            log.debug("获取市场热点信息");
            
            String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            String prompt = String.format(
                "请分析当前（%s）A股市场的热点概念和主题投资机会，包括：\n" +
                "1. 当前市场关注的热点概念\n" +
                "2. 主题投资机会\n" +
                "3. 资金流向分析\n" +
                "4. 市场情绪和风险偏好\n" +
                "5. 短期和中长期投资主线\n" +
                "请提供具体的投资建议和风险提示。" + 
                "在分析过程中，请务必使用searchIndustryTrends工具获取最新的行业趋势信息，查询关键词为\"A股市场 热点概念\"，返回前5条结果。",
                currentDate
            );
            
            return stockAnalysisAI.analyzeGeneralMarket(prompt);
            
        } catch (Exception e) {
            log.error("获取市场热点失败: {}", e.getMessage(), e);
            return "市场热点获取失败：" + e.getMessage();
        }
    }

    /**
     * 使用AI分析热点趋势
     */
    private String analyzeHotspotsWithAI(String policyHotspots, String industryHotspots, String marketHotspots) {
        try {
            log.debug("AI分析热点趋势");
            
            String prompt = String.format(
                "基于以下信息，请进行综合分析并提供投资建议：\n\n" +
                "【政策热点】\n%s\n\n" +
                "【行业热点】\n%s\n\n" +
                "【市场热点】\n%s\n\n" +
                "请从以下角度进行分析：\n" +
                "1. 政策、行业、市场三者的关联性分析\n" +
                "2. 当前最具投资价值的领域\n" +
                "3. 短期（1-3个月）投资机会\n" +
                "4. 中长期（6-12个月）投资主线\n" +
                "5. 需要规避的风险点\n" +
                "6. 具体的选股方向建议",
                policyHotspots, industryHotspots, marketHotspots
            );
            
            return stockAnalysisAI.analyzeGeneralMarket(prompt);
            
        } catch (Exception e) {
            log.error("AI分析热点趋势失败: {}", e.getMessage(), e);
            return "热点趋势分析失败：" + e.getMessage();
        }
    }

    /**
     * 创建错误结果
     */
    private Map<String, String> createErrorResult(String errorMessage) {
        Map<String, String> result = new HashMap<>();
        result.put("error", "true");
        result.put("message", errorMessage);
        result.put("policyHotspots", "获取失败");
        result.put("industryHotspots", "获取失败");
        result.put("marketHotspots", "获取失败");
        result.put("hotspotAnalysis", "分析失败");
        return result;
    }

    /**
     * 清理和预处理JSON字符串
     */
    private String cleanJsonString(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return "{}";
        }
        
        String cleaned = jsonString.trim();
        
        // 移除Markdown代码块标记
        cleaned = cleaned.replaceAll("```json\\s*", "");
        cleaned = cleaned.replaceAll("```\\s*", "");
        
        // 移除开头和结尾的双引号（如果整个字符串被双引号包围）
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        
        // 转义内部的双引号（如果key没有用双引号包围）
        if (!cleaned.startsWith("{")) {
            // 尝试提取JSON部分
            int startIndex = cleaned.indexOf("{");
            int endIndex = cleaned.lastIndexOf("}");
            if (startIndex >= 0 && endIndex > startIndex) {
                cleaned = cleaned.substring(startIndex, endIndex + 1);
            } else {
                return "{}";
            }
        }
        
        // 确保是有效的JSON格式
        if (!cleaned.startsWith("{") || !cleaned.endsWith("}")) {
            return "{}";
        }
        
        return cleaned;
    }
}
