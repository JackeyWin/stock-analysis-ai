package com.stockanalysis.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 股票推荐详情模型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockRecommendationDetail {
    
    /**
     * 股票代码
     */
    private String stockCode;
    
    /**
     * 股票名称
     */
    private String stockName;
    
    /**
     * 所属领域/行业
     */
    private String sector;
    
    /**
     * 推荐理由
     */
    private String recommendationReason;
    
    /**
     * 推荐评级 (强烈推荐, 推荐, 谨慎推荐)
     */
    private String rating;
    
    /**
     * 推荐评分 (1-10分)
     */
    private Double score;
    
    /**
     * 目标价格
     */
    private Double targetPrice;
    
    /**
     * 当前价格
     */
    private Double currentPrice;
    
    /**
     * 预期涨幅 (%)
     */
    private Double expectedReturn;
    
    /**
     * 风险等级 (低, 中, 高)
     */
    private String riskLevel;
    
    /**
     * 投资时间建议 (短期, 中期, 长期)
     */
    private String investmentPeriod;
    
    /**
     * 技术面分析
     */
    private String technicalAnalysis;
    
    /**
     * 基本面分析
     */
    private String fundamentalAnalysis;
    
    /**
     * 消息面分析
     */
    private String newsAnalysis;
    
    /**
     * 关键指标
     */
    private String keyMetrics;
    
    /**
     * 推荐时间
     */
    private LocalDateTime recommendTime;
    
    /**
     * 排序权重
     */
    private Integer sortOrder;
    
    /**
     * 是否为热门推荐
     */
    private Boolean isHot;
}
