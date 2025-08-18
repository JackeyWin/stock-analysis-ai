package com.stockanalysis.model;

import lombok.Data;

/**
 * AI分析结果
 */
@Data
public class AIAnalysisResult {
    private String stockCode;
    private String stockName;
    private String fullAnalysis; // 原始的完整AI分析文本

    // 新增：公司基本面分析
    private String companyFundamentalAnalysis;
    // 新的合并后的操作策略
    private String operationStrategy;
    // 盘面分析
    private String intradayOperations;
    // 新增：行业趋势和政策导向
    private String industryPolicyOrientation;

    // @Deprecated
    // private String summary; // AI分析摘要
    // @Deprecated
    // private String trendAnalysis; // 趋势分析
    // @Deprecated
    // private String technicalPattern; // 技术形态
    // @Deprecated
    // private String movingAverage; // 移动平均线
    // @Deprecated
    // private String rsiAnalysis; // RSI指标
    // @Deprecated
    // private String pricePredict; // 价格预测
    // @Deprecated
    // private String tradingAdvice; // 交易建议
    // @Deprecated
    // private String peerComparisonAnalysis; // 同行比较分析
    // @Deprecated
    // private String financialAnalysis; // 财务基本面分析
}