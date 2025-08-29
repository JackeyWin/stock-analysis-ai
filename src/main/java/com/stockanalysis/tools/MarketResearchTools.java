package com.stockanalysis.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.stockanalysis.config.TavilyApiKeyManager;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 行业趋势/政策 搜索工具
 *
 * 设计目标：
 * 1) 提供给大模型可调用的方法（@Tool），用于联网查询行业趋势与政策动向
 * 2) 优先使用 Tavily API（需要配置 API Key），未配置时返回明确提示
 * 3) 结果做轻量聚合，控制字数，附带来源链接，方便大模型引用
 *
 * Tavily API Key配置：
 * 在application.yml中配置tavily.api.key，该配置同样适用于StockPoolTools类
 */
@Slf4j
@Component
public class MarketResearchTools {

    private final HttpClient httpClient;
    private final String tavilyApiKey;
    private final TavilyApiKeyManager tavilyApiKeyManager;
    private final StockDataTool stockDataTool;

    // 默认构造函数，用于Spring Bean初始化
    public MarketResearchTools() {
        this.tavilyApiKey = "";
        this.tavilyApiKeyManager = null;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.stockDataTool = null;
    }

    // 带参数的构造函数，用于手动创建实例
    public MarketResearchTools(String tavilyApiKey) {
        this.tavilyApiKey = tavilyApiKey;
        this.tavilyApiKeyManager = null;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.stockDataTool = null;
    }

    // 带TavilyApiKeyManager参数的构造函数，用于Spring注入
    public MarketResearchTools(TavilyApiKeyManager tavilyApiKeyManager) {
        this.tavilyApiKey = "";
        this.tavilyApiKeyManager = tavilyApiKeyManager;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.stockDataTool = null;
    }

    // 带StockDataTool参数的构造函数，用于Spring注入
    public MarketResearchTools(String tavilyApiKey, StockDataTool stockDataTool) {
        this.tavilyApiKey = tavilyApiKey;
        this.tavilyApiKeyManager = null;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.stockDataTool = stockDataTool;
    }

    /**
     * 测试工具调用 - 用于验证工具是否被正确注册
     */
    @Tool("测试工具调用，返回当前时间戳")
    public String testToolCall() {
        log.info("🔍 AI调用测试工具: testToolCall - 开始执行");
        String result = "工具调用成功！当前时间: " + java.time.LocalDateTime.now();
        log.info("✅ 测试工具调用完成: {}", result);
        return result;
    }

    /**
     * 供大模型调用：搜索行业趋势/行情解读/竞品动态等
     *
     * @param query 关键词（建议包含行业/概念/公司名，例："储能 行业 趋势 2025"）
     * @param top   返回条数（1-10，默认5）
     * @return 简洁要点+来源链接
     */
    @Tool("搜索行业趋势，返回要点与链接。参数：query=关键词，top=返回数量")
    public String searchIndustryTrends(String query, Integer top) {
        log.info("🔍 AI调用工具: searchIndustryTrends - 开始执行");
        log.info("📝 搜索参数: query='{}', top={}", query, top);
        
        int limit = (top == null || top <= 0) ? 5 : Math.min(top, 10);
        String q = safe(query);
        log.info("🔧 处理后的参数: query='{}', limit={}", q, limit);

        if (!hasTavily()) {
            log.warn("⚠️ Tavily API未配置，返回fallback消息");
            return fallbackMessage("searchIndustryTrends", q);
        }

        log.info("🌐 开始调用Tavily API搜索行业趋势");
        String result = callTavilyApi(q, limit, "行业趋势", null);
                log.info("📊 行业趋势搜索结果解析完成，结果长度: {}", result.length());
                return result;
    }

    /**
     * 供大模型调用：搜索监管/政策/文件更新
     *
     * @param industry 行业关键词（例："半导体"）
     * @param region   区域（例："中国"、"上海"，可为空）
     * @param top      返回条数（1-10，默认5）
     * @return 简洁要点+来源链接
     */
    @Tool("搜索监管/政策更新，返回要点与链接。参数：industry=行业，region=区域，concepts=股票概念，top=返回数量")
    public String searchPolicyUpdates(String industry, String region, String concepts, Integer top) {
        log.info("🔍 AI调用工具: searchPolicyUpdates - 开始执行");
        log.info("📝 搜索参数: industry='{}', region='{}', top={}", industry, region, top);
        
        int limit = (top == null || top <= 0) ? 5 : Math.min(top, 10);
        String ind = safe(industry);
        String reg = safe(region);
        String con = safe(concepts);
        log.info("🔧 处理后的参数: industry='{}', region='{}', concepts='{}' limit={}", ind, reg, con, limit);

        if (!hasTavily()) {
            log.warn("⚠️ Tavily API未配置，返回fallback消息");
            return fallbackMessage("searchPolicyUpdates", ind + (reg.isEmpty() ? "" : (" " + reg)));
        }

        // 采用站点限定更聚焦政策类站点
        String query = (reg.isEmpty() ? "" : (reg + " ")) + ind + (con.isEmpty() ? "" : (" " + con)) + " 政策 文件 监管 site:gov.cn OR site:csrc.gov.cn OR site:pbc.gov.cn OR site:ndrc.gov.cn";

        log.info("🌐 构建政策搜索查询: '{}'", query);

        log.info("🌐 开始调用Tavily API搜索政策更新");
        String result = callTavilyApi(query, limit, "政策更新", "month");
                log.info("📊 政策更新搜索结果解析完成，结果长度: {}", result.length());
                return result;
    }

    /**
     * 供大模型调用：搜索市场情绪和投资者行为
     *
     * @param topic 话题关键词（例："股市情绪"、"投资者行为"）
     * @param top   返回条数（1-10，默认5）
     * @return 简洁要点+来源链接
     */
    @Tool("搜索市场情绪和投资者行为，返回要点与链接。参数：topic=话题关键词，top=返回数量")
    public String searchMarketSentiment(String topic, Integer top) {
        log.info("🔍 AI调用工具: searchMarketSentiment - 开始执行");
        log.info("📝 搜索参数: topic='{}', top={}", topic, top);
        
        int limit = (top == null || top <= 0) ? 5 : Math.min(top, 10);
        String q = safe(topic);
        log.info("🔧 处理后的参数: topic='{}', limit={}", q, limit);

        if (!hasTavily()) {
            log.warn("⚠️ Tavily API未配置，返回fallback消息");
            return fallbackMessage("searchMarketSentiment", q);
        }

        log.info("🌐 开始调用Tavily API搜索市场情绪");
        String api = "https://api.tavily.com/search";
        String body = "{" +
                jsonPair("api_key", tavilyApiKey) + "," +
                jsonPair("query", q) + "," +
                jsonPair("search_depth", "basic") + "," +
                jsonPair("max_results", String.valueOf(limit)) +
                "}";
        log.debug("📡 API请求体: {}", body);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(api))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            log.info("🚀 发送HTTP请求到Tavily API");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("📥 收到Tavily响应: 状态码={}, 响应长度={}", response.statusCode(), response.body().length());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("✅ Tavily API调用成功，开始解析结果");
                String result = extractTavilyResults(response.body(), limit, "市场情绪");
                log.info("📊 市场情绪搜索结果解析完成，结果长度: {}", result.length());
                return result;
            }
            log.warn("❌ Tavily 返回非 2xx：{} - {}", response.statusCode(), response.body());
            return "【联网搜索失败】Tavily响应异常，稍后重试。";
        } catch (IOException | InterruptedException e) {
            log.error("💥 Tavily 搜索失败: {}", e.getMessage(), e);
            return "【联网搜索失败】" + e.getMessage();
        } finally {
            log.info("🏁 AI调用工具: searchMarketSentiment - 执行完成");
        }
    }

    /**
     * 供大模型调用：搜索宏观经济指标对股市的影响
     *
     * @param indicator 宏观经济指标（例："GDP"、"通胀率"、"货币政策"）
     * @param top       返回条数（1-10，默认5）
     * @return 简洁要点+来源链接
     */
    @Tool("搜索宏观经济指标对股市的影响，返回要点与链接。参数：indicator=宏观经济指标，top=返回数量")
    public String searchMacroEconomyImpact(String indicator, Integer top) {
        log.info("🔍 AI调用工具: searchMacroEconomyImpact - 开始执行");
        log.info("📝 搜索参数: indicator='{}', top={}", indicator, top);
        
        int limit = (top == null || top <= 0) ? 5 : Math.min(top, 10);
        String q = safe(indicator) + " 宏观经济 股市 影响";
        log.info("🔧 处理后的参数: indicator='{}', limit={}", q, limit);

        if (!hasTavily()) {
            log.warn("⚠️ Tavily API未配置，返回fallback消息");
            return fallbackMessage("searchMacroEconomyImpact", q);
        }

        log.info("🌐 开始调用Tavily API搜索宏观经济影响");
        String result = callTavilyApi(q, limit, "宏观经济影响", null);
                log.info("📊 宏观经济影响搜索结果解析完成，结果长度: {}", result.length());
                return result;
    }

    /**
     * 供大模型调用：搜索特定行业的投资机会和风险
     *
     * @param industry 行业名称（例："人工智能"、"新能源汽车"）
     * @param top      返回条数（1-10，默认5）
     * @return 简洁要点+来源链接
     */
    @Tool("搜索特定行业的投资机会和风险，返回要点与链接。参数：industry=行业名称，top=返回数量")
    public String searchIndustryOpportunitiesAndRisks(String industry, Integer top) {
        log.info("🔍 AI调用工具: searchIndustryOpportunitiesAndRisks - 开始执行");
        log.info("📝 搜索参数: industry='{}', top={}", industry, top);
        
        int limit = (top == null || top <= 0) ? 5 : Math.min(top, 10);
        String q = safe(industry) + " 行业 投资机会 风险 分析";
        log.info("🔧 处理后的参数: industry='{}', limit={}", q, limit);

        if (!hasTavily()) {
            log.warn("⚠️ Tavily API未配置，返回fallback消息");
            return fallbackMessage("searchIndustryOpportunitiesAndRisks", q);
        }

        log.info("🌐 开始调用Tavily API搜索行业投资机会和风险");
        String result = callTavilyApi(q, limit, "行业投资机会和风险", null);
                log.info("📊 行业投资机会和风险搜索结果解析完成，结果长度: {}", result.length());
                return result;
    }

    /**
     * 供大模型调用：获取股票的技术分析和资金流向数据
     *
     * @param stockCode 股票代码（例："000001"）
     * @param top       返回条数（1-10，默认5）
     * @return 技术分析和资金流向数据
     */
    @Tool("获取股票的技术分析和资金流向数据。参数：stockCode=股票代码，top=返回数量")
    public String searchTechnicalAndFundamentalAnalysis(String stockCode, Integer top) {
        log.info("🔍 AI调用工具: searchTechnicalAndFundamentalAnalysis - 开始执行");
        log.info("📝 搜索参数: stockCode='{}', top={}", stockCode, top);
        
        // 直接使用StockDataTool中的方法获取技术分析和资金流向数据
        try {
            log.info("📊 开始获取股票{}的技术分析和资金流向数据", stockCode);
            
            // 检查StockDataTool是否已注入
            if (stockDataTool == null) {
                log.warn("⚠️ StockDataTool未注入，无法获取技术分析和资金流向数据");
                return "【数据获取失败】StockDataTool未注入";
            }
            
            // 获取技术分析数据
            String technicalData = stockDataTool.calculateTechnicalIndicators(stockCode);
            
            // 获取资金流向数据
            String moneyFlowData = stockDataTool.getMoneyFlowData(stockCode);
            
            log.info("✅ 技术分析和资金流向数据获取完成");
            return "【技术分析和资金流向】\n" + technicalData + "\n" + moneyFlowData;
        } catch (Exception e) {
            log.error("💥 获取技术分析和资金流向数据失败: {}", e.getMessage(), e);
            return "【数据获取失败】" + e.getMessage();
        } finally {
            log.info("🏁 AI调用工具: searchTechnicalAndFundamentalAnalysis - 执行完成");
        }
    }

    private boolean hasTavily() {
        if (tavilyApiKeyManager != null && tavilyApiKeyManager.hasAvailableKeys()) {
            return true;
        }
        return tavilyApiKey != null && !tavilyApiKey.isBlank();
    }

    /**
     * 获取当前可用的API key
     */
    private String getCurrentApiKey() {
        if (tavilyApiKeyManager != null && tavilyApiKeyManager.hasAvailableKeys()) {
            return tavilyApiKeyManager.getCurrentApiKey();
        }
        return tavilyApiKey;
    }

    /**
     * 调用Tavily API，支持自动重试和key切换
     */
    private String callTavilyApi(String query, int limit, String searchType, String timeRange) {
        int maxRetries = tavilyApiKeyManager != null ? tavilyApiKeyManager.getAvailableKeyCount() : 1;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                String apiKey = getCurrentApiKey();
                if (apiKey == null || apiKey.isEmpty()) {
                    log.error("❌ 没有可用的Tavily API key");
                    return "【联网搜索失败】未配置Tavily API key";
                }

                String api = "https://api.tavily.com/search";
                String body = "{" +
                        jsonPair("api_key", apiKey) + "," +
                        jsonPair("query", query) + "," +
                        jsonPair("search_depth", "basic") + "," +
                        jsonPair("max_results", String.valueOf(limit));
                
                if (timeRange != null && !timeRange.isEmpty()) {
                    body += "," + jsonPair("time_range", timeRange);
                }
                body += "}";

                log.debug("📡 API请求体: {}", body);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(api))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(20))
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

                log.info("🚀 发送HTTP请求到Tavily API (尝试 {}/{})", retryCount + 1, maxRetries);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                log.info("📥 收到Tavily响应: 状态码={}, 响应长度={}", response.statusCode(), response.body().length());
                
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    log.info("✅ Tavily API调用成功，开始解析结果");
                    return extractTavilyResults(response.body(), limit, searchType);
                }
                
                // 处理432错误（API key限制），自动切换key
                if (response.statusCode() == 432) {
                    log.warn("⚠️ Tavily API返回432错误（API key限制），尝试切换key");
                    if (tavilyApiKeyManager != null) {
                        tavilyApiKeyManager.switchToNextKey();
                        retryCount++;
                        continue;
                    }
                }
                
                log.warn("❌ Tavily 返回非 2xx：{} - {}", response.statusCode(), response.body());
                return "【联网搜索失败】Tavily响应异常，稍后重试。";
                
            } catch (IOException | InterruptedException e) {
                log.error("💥 Tavily 搜索失败: {}", e.getMessage(), e);
                if (retryCount < maxRetries - 1) {
                    log.info("🔄 尝试重试 ({}/{})", retryCount + 1, maxRetries);
                    retryCount++;
                    continue;
                }
                return "【联网搜索失败】" + e.getMessage();
            }
        }
        
        return "【联网搜索失败】所有API key都已尝试，请稍后重试";
    }

    private String safe(String v) {
        return v == null ? "" : v.trim();
    }

    /**
     * 解析 Tavily JSON 响应，输出：
     * - 要点若干行（截断）
     * - 来源链接列表
     */
    private String extractTavilyResults(String json, int limit, String title) {
        log.info("🔍 开始解析Tavily搜索结果: title='{}', limit={}, JSON长度={}", title, limit, json.length());
        
        // 轻量 JSON 解析：不引入第三方依赖，做简单字段提取
        // Tavily 格式参考：https://docs.tavily.com/
        List<Map<String, String>> items = new ArrayList<>();

        // 粗略切割 "results": [ { ... }, { ... } ]
        int lb = json.indexOf("\"results\"");
        if (lb < 0) {
            log.warn("⚠️ 未找到'results'字段，JSON格式可能不正确");
            return "【" + title + "】未获得结果";
        }
        lb = json.indexOf('[', lb);
        int rb = json.indexOf(']', lb);
        if (lb < 0 || rb < 0 || rb <= lb) {
            log.warn("⚠️ 未找到results数组，JSON格式可能不正确");
            return "【" + title + "】未获得结果";
        }
        
        String arr = json.substring(lb + 1, rb);
        log.debug("📋 提取的results数组内容长度: {}", arr.length());

        String[] blocks = arr.split("\\},\\s*\\{");
        int count = Math.min(blocks.length, limit);
        log.info("📊 解析到{}个结果块，将返回前{}个", blocks.length, count);
        
        StringBuilder out = new StringBuilder();
        out.append("【").append(title).append("·联网检索】\n");

        for (int i = 0; i < count; i++) {
            String b = blocks[i];
            String url = pickJsonString(b, "url");
            String t = pickJsonString(b, "title");
            String snippet = pickJsonString(b, "content");
            if (snippet.isBlank()) snippet = pickJsonString(b, "snippet");

            if (t.isBlank() && !url.isBlank()) t = url;

            log.debug("📝 结果{}: title='{}', url='{}', snippet长度={}", i+1, t, url, snippet.length());

            out.append("- ").append(take(t, 60)).append("\n");
            if (!snippet.isBlank()) {
                out.append("  摘要：").append(take(clean(snippet), 120)).append("\n");
            }
            if (!url.isBlank()) {
                out.append("  来源：").append(url).append("\n");
            }
        }

        String result = out.toString().trim();
        log.info("✅ Tavily结果解析完成: title='{}', 最终结果长度={}", title, result.length());
        return result;
    }

    private String take(String s, int n) {
        if (s == null) return "";
        String v = s.trim();
        return v.length() <= n ? v : v.substring(0, n) + "…";
    }

    private String clean(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    private String pickJsonString(String block, String key) {
        // 简单提取："key":"value"（非严格JSON解析，仅用于提要）
        try {
            String keyToken = "\"" + key + "\"";
            int keyPos = block.indexOf(keyToken);
            if (keyPos < 0) return "";
            int colon = block.indexOf(':', keyPos + keyToken.length());
            if (colon < 0) return "";
            int firstQuote = block.indexOf('"', colon + 1);
            if (firstQuote < 0) return "";
            int endQuote = findClosingQuote(block, firstQuote + 1);
            if (endQuote < 0) return "";
            String raw = block.substring(firstQuote + 1, endQuote);
            return raw.replace("\\\"", "\"");
        } catch (Exception e) {
            return "";
        }
    }

    private int findClosingQuote(String s, int start) {
        boolean escaped = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && !escaped) {
                return i;
            }
            if (c == '\\' && !escaped) {
                escaped = true;
            } else {
                escaped = false;
            }
        }
        return -1;
    }

    private String jsonPair(String key, String value) {
        if (value == null) {
            return "\"" + key + "\": null";
        }
        // 简单字符串或数字自动识别
        boolean isNumber = value.matches("^-?\\d+(?:\\.\\d+)?$");
        if (isNumber) {
            return "\"" + key + "\": " + value;
        }
        String safe = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + key + "\":\"" + safe + "\"";
    }

    /**
     * 使用Tavily AI Search搜索政策信息
     *
     * @param apiKey Tavily API密钥
     * @param country 国家名称（如"China"）
     * @param policyArea 政策领域（如"economic policy", "industrial policy"）
     * @param industry 特定行业（可选，如"artificial intelligence"）
     * @param maxResults 最大返回结果数
     * @param timeRange 时间范围（"day", "week", "month", "year"）
     * @return 政策搜索结果列表
     */
    @Tool("获取国家最近政策更新。参数：country 国家名称（如\"China\"）, policyArea 政策领域（如\"economic policy\", \"industrial policy\"）, industry 特定行业（可选，如\"artificial intelligence\"）, maxResults 最大返回结果数（默认10）, timeRange 时间范围（\"day\", \"week\", \"month\", \"year\"）（默认\"day\"）")
    public String searchPolicies(String country, String policyArea, String industry, int maxResults, String timeRange) {
        try {
            // 构建查询字符串
            String query = buildQueryString(country, policyArea, industry);
            
            // 使用通用API调用方法，支持自动重试和key切换
            String result = callTavilyApi(query, maxResults, "政策搜索", "day");
            
            // 如果返回的是错误信息，尝试解析为JSON格式
            if (result.startsWith("【联网搜索失败】")) {
                return "{\"error\": \"" + result + "\"}";
            }
            
            return result;
        } catch (Exception e) {
            return "{\"error\": \"搜索政策信息时发生错误: " + e.getMessage() + "\"}";
        }
    }
    
    /**
     * 构建搜索查询字符串
     */
    private String buildQueryString(String country, String policyArea, String industry) {
        StringBuilder query = new StringBuilder();
        
        // 添加国家/地区
        if (country != null && !country.trim().isEmpty()) {
            query.append(country).append(" ");
        }
        
        // 添加"最新"关键词
        query.append("latest ");
        
        // 添加行业（如果提供）
        if (industry != null && !industry.trim().isEmpty()) {
            query.append(industry).append(" ");
        }
        
        // 添加政策领域
        query.append(policyArea);
        
        // 添加年份（确保获取最新信息）
        query.append(" 2025");
        
        // 添加政府网站偏好
        query.append(" site:.gov.cn OR site:.gov OR site:xinhuanet.com OR site:china-daily.com.cn OR site:miit.gov.cn");

        
        return query.toString();
    }

    private String fallbackMessage(String method, String query) {
        log.warn("⚠️ 返回fallback消息: method='{}', query='{}'", method, query);
        return "【提示】未配置 TAVILY_API_KEY，已禁用联网搜索。\n" +
                "方法：" + method + "\n" +
                (query == null || query.isBlank() ? "" : ("关键词：" + query + "\n")) +
                "请在应用配置中设置 tavily.api.key 后重试。";
    }
}


