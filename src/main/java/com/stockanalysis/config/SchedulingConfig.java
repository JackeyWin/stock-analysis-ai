package com.stockanalysis.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 定时任务配置
 */
@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        // 配置定时任务线程池
        taskRegistrar.setScheduler(taskExecutor());
    }

    /**
     * 定时任务线程池
     */
    public Executor taskExecutor() {
        return Executors.newScheduledThreadPool(3);
    }
}
