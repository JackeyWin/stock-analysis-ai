package com.stockanalysis.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Tavily API Key管理器
 * 支持多个API key的自动切换和轮询
 */
@Slf4j
@Component
public class TavilyApiKeyManager {

    private final List<String> apiKeys;
    private final AtomicInteger currentKeyIndex = new AtomicInteger(0);

    public TavilyApiKeyManager(@Value("${tavily.api.keys:}") String keysConfig,
                              @Value("${tavily.api.key:}") String singleKey) {
        // 优先使用多key配置
        if (keysConfig != null && !keysConfig.trim().isEmpty()) {
            this.apiKeys = Arrays.stream(keysConfig.split(","))
                    .map(String::trim)
                    .filter(key -> !key.isEmpty())
                    .collect(Collectors.toList());
            log.info("🔑 配置了 {} 个Tavily API keys", this.apiKeys.size());
        } else if (singleKey != null && !singleKey.trim().isEmpty()) {
            // 兼容单个key配置
            this.apiKeys = List.of(singleKey.trim());
            log.info("🔑 使用单个Tavily API key配置");
        } else {
            this.apiKeys = List.of();
            log.warn("⚠️ 未配置Tavily API key");
        }
    }

    /**
     * 获取当前可用的API key
     */
    public String getCurrentApiKey() {
        if (apiKeys.isEmpty()) {
            return null;
        }
        return apiKeys.get(currentKeyIndex.get() % apiKeys.size());
    }

    /**
     * 切换到下一个API key
     */
    public void switchToNextKey() {
        if (apiKeys.size() > 1) {
            int currentIndex = currentKeyIndex.get();
            int nextIndex = (currentIndex + 1) % apiKeys.size();
            currentKeyIndex.set(nextIndex);
            log.info("🔄 切换到下一个Tavily API key: {} -> {}", 
                    getKeyDisplayName(apiKeys.get(currentIndex)), 
                    getKeyDisplayName(apiKeys.get(nextIndex)));
        }
    }

    /**
     * 检查是否有可用的API key
     */
    public boolean hasAvailableKeys() {
        return !apiKeys.isEmpty();
    }

    /**
     * 获取可用的key数量
     */
    public int getAvailableKeyCount() {
        return apiKeys.size();
    }

    /**
     * 获取当前key的索引
     */
    public int getCurrentKeyIndex() {
        return currentKeyIndex.get();
    }

    /**
     * 获取所有可用的key（用于调试，隐藏敏感信息）
     */
    public List<String> getAvailableKeys() {
        return apiKeys.stream()
                .map(this::getKeyDisplayName)
                .collect(Collectors.toList());
    }

    /**
     * 获取key的显示名称（隐藏敏感信息）
     */
    private String getKeyDisplayName(String key) {
        if (key == null || key.length() < 8) {
            return "***";
        }
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }

    /**
     * 重置key索引到第一个
     */
    public void resetToFirstKey() {
        currentKeyIndex.set(0);
        log.info("🔄 重置Tavily API key到第一个");
    }
}
