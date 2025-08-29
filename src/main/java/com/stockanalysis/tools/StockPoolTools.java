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
 * 股票池搜索工具
 *
 * 设计目标：
 * 1) 提供给大模型可调用的方法（@Tool），用于联网查询热门股票池、行业龙头等信息
 * 2) 优先使用 Tavily API（需要配置 API Key），未配置时返回明确提示
 * 3) 结果做轻量聚合，控制字数，附带来源链接，方便大模型引用
 *
 * Tavily API Key配置：
 * 在application.yml中配置tavily.api.key，该配置同样适用于MarketResearchTools类
 */
@Slf4j
@Component
public class StockPoolTools {

    private final HttpClient httpClient;
    private final String tavilyApiKey;
    private final TavilyApiKeyManager tavilyApiKeyManager;

    // 默认构造函数，用于Spring Bean初始化
    public StockPoolTools() {
        this.tavilyApiKey = "";
        this.tavilyApiKeyManager = null;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // 带参数的构造函数，用于手动创建实例
    public StockPoolTools(String tavilyApiKey) {
        this.tavilyApiKey = tavilyApiKey;
        this.tavilyApiKeyManager = null;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // 带TavilyApiKeyManager参数的构造函数，用于Spring注入
    public StockPoolTools(TavilyApiKeyManager tavilyApiKeyManager) {
        this.tavilyApiKey = "";
        this.tavilyApiKeyManager = tavilyApiKeyManager;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 测试工具调用 - 用于验证工具是否被正确注册
     */
    @Tool("测试股票池工具调用，返回当前时间戳")
    public String testStockPoolToolCall() {
        log.info("🔍 AI调用测试工具: testStockPoolToolCall - 开始执行");
        String result = "股票池工具调用成功！当前时间: " + java.time.LocalDateTime.now();
        log.info("✅ 测试工具调用完成: {}", result);
        return result;
    }

    /**
     * 供大模型调用：搜索热门股票池
     *
     * @param query 关键词（建议包含行业/概念，例："新能源 股票池"）
     * @param top   返回条数（1-10，默认5）
     * @return 简洁要点+来源链接
     */
    @Tool("搜索热门股票池，返回要点与链接。参数：query=关键词，top=返回数量")
    public String searchHotStockPools(String query, Integer top) {
        return searchHotStockPoolsInternal(query, top);
    }

    private String searchHotStockPoolsInternal(String query, Integer top) {
        log.info("🔍 AI调用工具: searchHotStockPools - 开始执行");
        log.info("📝 搜索参数: query='{}', top={}", query, top);
        
        int limit = (top == null || top <= 0) ? 5 : Math.min(top, 10);
        String q = safe(query);
        log.info("🔧 处理后的参数: query='{}', limit={}", q, limit);

        if (!hasTavily()) {
            log.warn("⚠️ Tavily API未配置，返回fallback消息");
            return fallbackMessage("searchHotStockPools", q);
        }

        log.info("🌐 开始调用Tavily API搜索热门股票池");
        String result = callTavilyApi(q + " 股票池 推荐", limit, "热门股票池");
        log.info("📊 热门股票池搜索结果解析完成，结果长度: {}", result.length());
        return result;
    }

    /**
     * 供大模型调用：搜索行业龙头股票
     *
     * @param industry 行业关键词（例："半导体"）
     * @param top      返回条数（1-10，默认5）
     * @return 简洁要点+来源链接
     */
    @Tool("搜索行业龙头股票，返回要点与链接。参数：industry=行业，top=返回数量")
    public String searchIndustryLeaders(String industry, Integer top) {
        return searchIndustryLeadersInternal(industry, top);
    }

    @Tool("根据AI分析内容搜索相关股票池，返回要点与链接。参数：analysisContent=分析内容，hotspots=热点内容，top=返回数量")
    public String searchRelatedStockPools(String analysisContent, Map<String, Object> hotspots, Integer top) {
        return searchRelatedStockPoolsInternal(analysisContent, hotspots, top);
    }

    private String searchRelatedStockPoolsInternal(String analysisContent, Map<String, Object> hotspots, Integer top) {
        log.info("🔍 AI调用工具: searchRelatedStockPools - 开始执行");
        log.info("📝 搜索参数: analysisContent='{}', top={}", analysisContent, top);
        
        int limit = (top == null || top <= 0) ? 5 : Math.min(top, 10);
        String content = safe(analysisContent);
        log.info("🔧 处理后的参数: analysisContent='{}', limit={}", content, limit);

        if (!hasTavily()) {
            log.warn("⚠️ Tavily API未配置，返回fallback消息");
            return fallbackMessage("searchRelatedStockPools", content);
        }

        // 根据分析内容和热点构建搜索查询
        String query = buildSearchQueryFromAnalysis(content, hotspots);
        log.info("🌐 构建相关股票池搜索查询: '{}'", query);

        log.info("🌐 开始调用Tavily API搜索相关股票池");
        String result = callTavilyApi(query, limit, "相关股票池");
        log.info("📊 相关股票池搜索结果解析完成，结果长度: {}", result.length());
        return result;
    }

    /**
     * 根据AI分析内容构建搜索查询
     * @param analysisContent AI分析内容
     * @param hotspots 热点内容
     * @return 搜索查询
     */
    private String buildSearchQueryFromAnalysis(String analysisContent, Map<String, Object> hotspots) {
        // 改进实现：提取更多关键词
        StringBuilder query = new StringBuilder("热门股票");
        
        if (analysisContent != null && !analysisContent.isEmpty()) {
            // 提取行业关键词
            if (analysisContent.contains("科技")) {
                query.append(" 科技股");
            }
            if (analysisContent.contains("医药")) {
                query.append(" 医药股");
            }
            if (analysisContent.contains("消费")) {
                query.append(" 消费股");
            }
            if (analysisContent.contains("金融")) {
                query.append(" 金融股");
            }
            
            // 提取政策热点关键词
            if (analysisContent.contains("人工智能")) {
                query.append(" 人工智能");
            }
            if (analysisContent.contains("新能源")) {
                query.append(" 新能源");
            }
            if (analysisContent.contains("芯片")) {
                query.append(" 芯片");
            }
            if (analysisContent.contains("半导体")) {
                query.append(" 半导体");
            }
            if (analysisContent.contains("5G")) {
                query.append(" 5G");
            }
            
            // 提取市场热点关键词
            if (analysisContent.contains("牛市")) {
                query.append(" 牛市");
            }
            if (analysisContent.contains("龙头")) {
                query.append(" 龙头");
            }
        }
        
        // 根据热点内容添加关键词
        if (hotspots != null) {
            // 添加政策热点关键词
            String policyHotspots = hotspots.getOrDefault("policyHotspots", "").toString();
            if (!policyHotspots.isEmpty()) {
                query.append(" ").append(policyHotspots);
            }
            
            // 添加行业热点关键词
            String industryHotspots = hotspots.getOrDefault("industryHotspots", "").toString();
            if (!industryHotspots.isEmpty()) {
                query.append(" ").append(industryHotspots);
            }
            
            // 添加市场热点关键词
            String marketHotspots = hotspots.getOrDefault("marketHotspots", "").toString();
            if (!marketHotspots.isEmpty()) {
                query.append(" ").append(marketHotspots);
            }
        }
        
        return query.toString();
    }

    private String searchIndustryLeadersInternal(String industry, Integer top) {
        log.info("🔍 AI调用工具: searchIndustryLeaders - 开始执行");
        log.info("📝 搜索参数: industry='{}', top={}", industry, top);
        
        int limit = (top == null || top <= 0) ? 5 : Math.min(top, 10);
        String ind = safe(industry);
        log.info("🔧 处理后的参数: industry='{}', limit={}", ind, limit);

        if (!hasTavily()) {
            log.warn("⚠️ Tavily API未配置，返回fallback消息");
            return fallbackMessage("searchIndustryLeaders", ind);
        }

        // 构建搜索查询
        String query = ind + " 龙头股 上市公司";
        log.info("🌐 构建行业龙头搜索查询: '{}'", query);

        log.info("🌐 开始调用Tavily API搜索行业龙头股票");
        String result = callTavilyApi(query, limit, "行业龙头股");
        log.info("📊 行业龙头股搜索结果解析完成，结果长度: {}", result.length());
        return result;
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
    private String callTavilyApi(String query, int limit, String searchType) {
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
                        jsonPair("max_results", String.valueOf(limit)) +
                        "}";

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
        return extractTavilyResultsInternal(json, limit, title);
    }

    private String extractTavilyResultsInternal(String json, int limit, String title) {
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

    private String fallbackMessage(String method, String query) {
        log.warn("⚠️ 返回fallback消息: method='{}', query='{}'", method, query);
        return "【提示】未配置 TAVILY_API_KEY，已禁用联网搜索。\n" +
                "方法：" + method + "\n" +
                (query == null || query.isBlank() ? "" : ("关键词：" + query + "\n")) +
                "请在应用配置中设置 tavily.api.key 后重试。";
    }
}