package com.stockanalysis.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;

/**
 * 股票K线数据模型
 */
@Data
public class StockData {
    
    @JsonProperty("d")
    private String date;        // 日期
    
    @JsonProperty("o")
    private Double open;        // 开盘价
    
    @JsonProperty("c")
    private Double close;       // 收盘价
    
    @JsonProperty("h")
    private Double high;        // 最高价
    
    @JsonProperty("l")
    private Double low;         // 最低价
    
    @JsonProperty("v")
    private String volume;      // 成交量（万手）
    
    @JsonProperty("tu")
    private String turnover;    // 成交额（亿元）
}