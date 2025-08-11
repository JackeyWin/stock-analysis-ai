package com.stockanalysis.config;

import com.stockanalysis.service.StockAnalysisAI;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j配置类
 */
@Configuration
public class LangChain4jConfig {

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${deepseek.api.model:deepseek-reasoner}")
    private String model;

    /**
     * 配置DeepSeek聊天模型
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl + "/v1")
                .modelName(model)
                .temperature(0.7)
//                .maxTokens(2000)
                .timeout(Duration.ofMinutes(5))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * 配置股票分析AI服务
     */
    @Bean
    public StockAnalysisAI stockAnalysisAI(ChatLanguageModel chatLanguageModel) {
        return AiServices.builder(StockAnalysisAI.class)
                .chatLanguageModel(chatLanguageModel)
                .build();
    }
}
