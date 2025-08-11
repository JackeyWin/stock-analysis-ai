package com.stockanalysis.model;

import lombok.Data;

import java.util.List;

/**
 * 融资融券数据模型
 */
@Data
public class MarginTradingData {
    
    private List<DailyMarginData> dailyData;  // 每日融资融券数据
    
    @Data
    public static class DailyMarginData {
        private String date;                    // 日期
        private Double marginBalance;           // 融资余额
        private Double shortBalance;            // 融券余额
        private Double marginBuyAmount;         // 融资买入额
        private Double shortSellAmount;         // 融券卖出量
        private Double marginRepayAmount;       // 融资偿还额
        private Double shortRepayAmount;        // 融券偿还量
        private Double netMarginAmount;         // 融资净买入额
        private Double netShortAmount;          // 融券净卖出额
    }
}