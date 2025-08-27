package com.stockanalysis.config;

import com.stockanalysis.service.StockAnalysisAI;
import com.stockanalysis.tools.MarketResearchTools;
import com.stockanalysis.tools.StockPoolTools;
import com.stockanalysis.tools.StockDataTool;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LangChain4j配置类
 */
@Configuration
public class LangChain4jConfig {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jConfig.class);

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${deepseek.api.model:deepseek-reasoner}")
    private String model;

    @Value("${tavily.api.key:}")
    private String tavilyApiKey;

    /**
     * 配置DeepSeek聊天模型
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl + "/v1")
                .modelName(model)
                .temperature(1.0)
                .frequencyPenalty(0.2)
                .maxTokens(10000)
                .timeout(Duration.ofMinutes(10))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * 配置股票分析AI服务
     */
    @Bean
    public StockAnalysisAI stockAnalysisAI(ChatLanguageModel chatLanguageModel, StockDataTool stockDataTool) {
        log.info("🔧 开始配置StockAnalysisAI服务...");
        log.info("🔧 Tavily API Key配置状态: {}", tavilyApiKey != null && !tavilyApiKey.isEmpty() ? "已配置" : "未配置");
        
        MarketResearchTools marketResearchTools = new MarketResearchTools(tavilyApiKey);
        log.info("🔧 创建MarketResearchTools实例: {}", marketResearchTools.getClass().getSimpleName());
        
        StockPoolTools stockPoolTools = new StockPoolTools(tavilyApiKey);
        log.info("🔧 创建StockPoolTools实例: {}", stockPoolTools.getClass().getSimpleName());
        
        StockAnalysisAI aiService = AiServices.builder(StockAnalysisAI.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(marketResearchTools, stockPoolTools, stockDataTool)
                .build();
        
        log.info("✅ StockAnalysisAI服务配置完成");
        return aiService;
    }
}
