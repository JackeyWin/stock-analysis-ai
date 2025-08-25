package com.stockanalysis.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Supplier;

public class RetryTemplate {
    private static final Logger logger = LoggerFactory.getLogger(RetryTemplate.class);

    public static <T> T executeWithRetry(Supplier<T> supplier, int maxAttempts, long delayMs) {
        Exception lastException = null;
        for (int i = 1; i <= maxAttempts; i++) {
            try {
                return supplier.get();
            } catch (Exception e) {
                lastException = e;
                logger.warn("第{}次重试失败（股票数据获取）: {}", i, e.getMessage());
                if (i < maxAttempts) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        throw new RetryException("操作重试" + maxAttempts + "次后失败", lastException);
    }

    public static class RetryException extends RuntimeException {
        public RetryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}