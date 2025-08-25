package com.stockanalysis.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 股票分析结果实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "stock_analysis_result")
public class StockAnalysisResultEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 机器标识
     */
    @Column(name = "machine_id", nullable = false)
    private String machineId;
    
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
     * 分析时间
     */
    @Column(name = "analysis_time", nullable = false)
    private LocalDateTime analysisTime;
    
    /**
     * 完整的AI分析文本
     */
    @Column(name = "full_analysis", columnDefinition = "TEXT")
    private String fullAnalysis;
    
    /**
     * 公司基本面分析
     */
    @Column(name = "company_fundamental_analysis", columnDefinition = "TEXT")
    private String companyFundamentalAnalysis;
    
    /**
     * 操作策略
     */
    @Column(name = "operation_strategy", columnDefinition = "TEXT")
    private String operationStrategy;
    
    /**
     * 盘面分析
     */
    @Column(name = "intraday_operations", columnDefinition = "TEXT")
    private String intradayOperations;
    
    /**
     * 行业趋势和政策导向
     */
    @Column(name = "industry_policy_orientation", columnDefinition = "TEXT")
    private String industryPolicyOrientation;
    
    /**
     * 创建时间
     */
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * 分析状态
     */
    @Column(name = "status")
    private String status;
    
    /**
     * 分析耗时（毫秒）
     */
    @Column(name = "analysis_duration")
    private Long analysisDuration;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (analysisTime == null) {
            analysisTime = LocalDateTime.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}