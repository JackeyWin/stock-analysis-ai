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

    public PolicyHotspotService(StockAnalysisAI stockAnalysisAI) {
        this.stockAnalysisAI = stockAnalysisAI;
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
                // 去掉头尾的双引号（如果存在）
                if (policyHotspotsJson != null && policyHotspotsJson.length() >= 2 && 
                    policyHotspotsJson.startsWith("\"") && policyHotspotsJson.endsWith("\"")) {
                    policyHotspotsJson = policyHotspotsJson.substring(1, policyHotspotsJson.length() - 1);
                }
                ObjectMapper objectMapper = new ObjectMapper();
                try {
                    result = objectMapper.readValue(policyHotspotsJson, new TypeReference<Map<String, String>>(){});     
                } catch (Exception e) {
                    log.error("解析政策热点JSON失败: {}", e.getMessage(), e);
                    
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
            
            String prompt = String.format(
                "请分析当前（%s）中国股市相关的政策热点和方向，包括：\n" +
                "1. 最新的国家政策导向\n" +
                "2. 监管政策变化\n" +
                "3. 产业政策支持方向\n" +
                "4. 财政货币政策影响\n" +
                "在分析过程中，请务必使用searchPolicyUpdates工具获取最新的政策信息，区域为\"中国\"，仅返回最近一周的结果，如果结果太多只选取前10条。" +
                "查询关键词：国家发改委 项目批复清单、财政部 贴息目录、工信部 创新目录、国家数据局 授权运营名单、部门联合声明、政府采购等等" + 
                "请务必使用getAllSectors工具获取所有的行业名称，然后根据最新政策从这些行业中筛选出政策利好的行业，多概念叠加为最佳。" + 
                "输出json格式的数据，key:行业名称（这里的行业名称一定要用工具获取的列表中的行业名称，一个字都不要改）, value:利好因素(尽量详细，不超过200字）（标注政策发布时间）" +
                "一定要输出标准格式的json数据，不要出现任何其他的注释或者符号，只输出{key:value}格式的数据，一定不能出现```json```符号，也不能出现``````符号" + 
                "输出的json字符串要以{开头，以}结尾，前后不能有双引号，也不能有任何其他符号" + 
                "一定要输出能被解析的标准json，不能包含特殊字符" + 
                "如果新闻中有出现企业名称，一定要在利好因素中标注企业名称和企业代码，企业名称和企业代码要对应起来" + 
                "一定只输出我要求的json数据，其他的分析内容不需要输出，可以放在各利好行业的利好因素里面，否则会出现解析错误",
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
}
