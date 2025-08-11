package com.stockanalysis.model;

import lombok.Data;

import java.util.List;

/**
 * 股票分析响应模型
 */
@Data
public class StockAnalysisResponse {
    
    private String stockCode;
    private String stockName;
    
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
    
    // AI分析结果
    private AIAnalysisResult aiAnalysisResult;
    
    // 响应状态
    private boolean success = true;
    private String message;
}