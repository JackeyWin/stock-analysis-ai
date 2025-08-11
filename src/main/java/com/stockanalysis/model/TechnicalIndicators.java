package com.stockanalysis.model;

import lombok.Data;

import java.util.List;

/**
 * 技术指标数据模型
 */
@Data
public class TechnicalIndicators {
    
    // 日期映射格式 - 近5日指标数据
    private List<DailyIndicators> dailyIndicators;
    
    /**
     * 每日技术指标数据
     */
    @Data
    public static class DailyIndicators {
        private String date;            // 日期
        private Double close;           // 收盘价
        private Double volume;          // 成交量（万手）
        
        // 移动平均线
        private Double ma5;             // 5日移动平均线
        private Double ma10;            // 10日移动平均线
        private Double ma20;            // 20日移动平均线
        private Double ma60;            // 60日移动平均线
        
        // 技术指标
        private Double rsi;             // RSI指标
        private Double macd;            // MACD指标
        private Double macdSignal;      // MACD信号线
        private Double macdHist;        // MACD柱状图
        
        // 布林带
        private Double bollingerUpper;  // 布林带上轨
        private Double bollingerMiddle; // 布林带中轨
        private Double bollingerLower;  // 布林带下轨
        
        // KDJ指标
        private Double kdjK;            // KDJ指标K值
        private Double kdjD;            // KDJ指标D值
        private Double kdjJ;            // KDJ指标J值
    }
}