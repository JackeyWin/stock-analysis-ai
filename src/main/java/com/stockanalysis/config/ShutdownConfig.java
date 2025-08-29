package com.stockanalysis.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 应用关闭配置
 * 确保应用关闭时能够优雅地处理所有资源
 */
@Slf4j
@Configuration
@EnableAsync
public class ShutdownConfig {

    /**
     * 配置异步任务执行器
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("StockAnalysis-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 监听应用关闭事件
     */
    @EventListener(ContextClosedEvent.class)
    public void handleContextClosedEvent(ContextClosedEvent event) {
        log.info("应用上下文正在关闭，开始清理资源...");
        
        try {
            // 等待一段时间确保所有任务完成
            Thread.sleep(2000);
            log.info("应用关闭事件处理完成");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("应用关闭事件处理被中断");
        }
    }
}
