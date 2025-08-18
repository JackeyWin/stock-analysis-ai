package com.stockanalysis.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 股票推荐详情实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "stock_recommendation_detail")
public class StockRecommendationDetailEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 股票代码
     */
    @Column(name = "stock_code", nullable = false)
    private String stockCode;
    
    /**
     * 股票名称
     */
    @Column(name = "stock_name")
    private String stockName;
    
    /**
     * 所属领域/行业
     */
    @Column(name = "sector")
    private String sector;
    
    /**
     * 推荐理由
     */
    @Column(name = "recommendation_reason", length = 1000)
    private String recommendationReason;
    
    /**
     * 推荐评级 (强烈推荐, 推荐, 谨慎推荐)
     */
    @Column(name = "rating")
    private String rating;
    
    /**
     * 推荐评分 (1-10分)
     */
    @Column(name = "score")
    private Double score;
    
    /**
     * 目标价格
     */
    @Column(name = "target_price")
    private Double targetPrice;
    
    /**
     * 当前价格
     */
    @Column(name = "current_price")
    private Double currentPrice;
    
    /**
     * 预期涨幅 (%)
     */
    @Column(name = "expected_return")
    private Double expectedReturn;
    
    /**
     * 风险等级 (低, 中, 高)
     */
    @Column(name = "risk_level")
    private String riskLevel;
    
    /**
     * 投资时间建议 (短期, 中期, 长期)
     */
    @Column(name = "investment_period")
    private String investmentPeriod;
    
    /**
     * 技术面分析
     */
    @Column(name = "technical_analysis", length = 2000)
    private String technicalAnalysis;
    
    /**
     * 基本面分析
     */
    @Column(name = "fundamental_analysis", length = 2000)
    private String fundamentalAnalysis;
    
    /**
     * 消息面分析
     */
    @Column(name = "news_analysis", length = 2000)
    private String newsAnalysis;
    
    /**
     * 关键指标
     */
    @Column(name = "key_metrics", length = 1000)
    private String keyMetrics;
    
    /**
     * 推荐时间
     */
    @Column(name = "recommend_time")
    private LocalDateTime recommendTime;
    
    /**
     * 排序权重
     */
    @Column(name = "sort_order")
    private Integer sortOrder;
    
    /**
     * 是否为热门推荐
     */
    @Column(name = "is_hot")
    private Boolean isHot;
    
    /**
     * 关联的每日推荐
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_recommendation_id")
    private DailyRecommendationEntity dailyRecommendation;
}