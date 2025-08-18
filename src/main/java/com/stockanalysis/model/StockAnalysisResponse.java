package com.stockanalysis.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 股票分析响应模型
 */
@Data
public class StockAnalysisResponse {
    
    private String stockCode;
    private String stockName;
    // 基础信息（来自东方财富：代码、名称、价格、成交量等）
    private Map<String, Object> stockBasic;
    
    // 基础数据
    private List<StockData> stockData;
    private List<StockData> marketData;
    private List<StockData> boardData;
    
    // 技术指标
    private TechnicalIndicators technicalIndicators;
    
    // 新闻数据
    private List<NewsData> newsData;
    
    // 资金流向数据
    private MoneyFlowData moneyFlowData;
    
    // 融资融券数据
    private MarginTradingData marginTradingData;
    
    // 同行比较数据
    private Map<String, Object> peerComparison;
    
    // 财务分析数据
    private Map<String, Object> financialAnalysis;
    
    // AI分析结果
    private AIAnalysisResult aiAnalysisResult;
    
    // 响应状态
    private boolean success = true;
    private String message;
}