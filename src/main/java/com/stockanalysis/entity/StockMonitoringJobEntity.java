package com.stockanalysis.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_monitoring_job")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockMonitoringJobEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "job_id", unique = true, nullable = false)
    private String jobId;
    
    @Column(name = "stock_code", nullable = false)
    private String stockCode;
    
    @Column(name = "interval_minutes", nullable = false)
    private Integer intervalMinutes;
    
    @Column(name = "analysis_id")
    private String analysisId;
    
    @Column(name = "machine_id")
    private String machineId;
    
    @Column(name = "status", nullable = false)
    private String status; // running, stopped
    
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;
    
    @Column(name = "last_run_time")
    private LocalDateTime lastRunTime;
    
    @Column(name = "last_message", columnDefinition = "text")
    private String lastMessage;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
