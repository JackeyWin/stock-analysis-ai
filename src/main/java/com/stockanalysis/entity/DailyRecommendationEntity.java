package com.stockanalysis.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 每日推荐数据实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "daily_recommendation")
public class DailyRecommendationEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 推荐ID
     */
    @Column(name = "recommendation_id", unique = true, nullable = false)
    private String recommendationId;
    
    /**
     * 推荐日期
     */
    @Column(name = "recommendation_date", nullable = false)
    private String recommendationDate;
    
    /**
     * 创建时间
     */
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;
    
    /**
     * 市场概况
     */
    @Column(name = "market_overview", length = 5000)
    private String marketOverview;
    
    /**
     * 政策热点
     */
    @Column(name = "policy_hotspots", length = 2000)
    private String policyHotspots;
    
    /**
     * 行业热点
     */
    @Column(name = "industry_hotspots", length = 2000)
    private String industryHotspots;
    
    /**
     * 推荐总结
     */
    @Column(name = "summary", length = 5000)
    private String summary;
    
    /**
     * AI分析师观点
     */
    @Column(name = "analyst_view", length = 3000)
    private String analystView;
    
    /**
     * 风险提示
     */
    @Column(name = "risk_warning", length = 2000)
    private String riskWarning;
    
    /**
     * 推荐状态 (ACTIVE, EXPIRED, DRAFT)
     */
    @Column(name = "status")
    private String status;
    
    /**
     * 版本号
     */
    @Column(name = "version")
    private Integer version;
    
    /**
     * 推荐股票列表
     */
    @OneToMany(mappedBy = "dailyRecommendation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<StockRecommendationDetailEntity> recommendedStocks;
}