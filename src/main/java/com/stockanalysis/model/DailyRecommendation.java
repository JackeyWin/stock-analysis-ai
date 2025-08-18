package com.stockanalysis.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 每日推荐数据模型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyRecommendation {
    
    /**
     * 推荐ID
     */
    private String recommendationId;
    
    /**
     * 推荐日期
     */
    private String recommendationDate;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 市场概况
     */
    private String marketOverview;
    
    /**
     * 政策热点
     */
    private String policyHotspots;
    
    /**
     * 行业热点
     */
    private String industryHotspots;
    
    /**
     * 推荐股票列表
     */
    private List<StockRecommendationDetail> recommendedStocks;
    
    /**
     * 推荐总结
     */
    private String summary;
    
    /**
     * AI分析师观点
     */
    private String analystView;
    
    /**
     * 风险提示
     */
    private String riskWarning;
    
    /**
     * 推荐状态 (ACTIVE, EXPIRED, DRAFT)
     */
    private String status;
    
    /**
     * 版本号
     */
    private Integer version;
}
