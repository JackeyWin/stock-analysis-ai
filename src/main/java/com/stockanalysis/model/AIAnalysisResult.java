package com.stockanalysis.model;

import lombok.Data;

/**
 * AI分析结果模型
 */
@Data
public class AIAnalysisResult {
    
    private String trendAnalysis;       // 趋势分析
    private String technicalPattern;    // 技术形态
    private String movingAverage;       // 移动平均线分析
    private String rsiAnalysis;         // RSI指标分析
    private String pricePredict;        // 价格预测
    private String tradingAdvice;       // 交易建议
    private String intradayOperations;  // 盘中操作
    
    private String fullAnalysis;        // 完整分析报告
}