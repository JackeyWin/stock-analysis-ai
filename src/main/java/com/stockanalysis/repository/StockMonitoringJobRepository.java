package com.stockanalysis.repository;

import com.stockanalysis.entity.StockMonitoringJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockMonitoringJobRepository extends JpaRepository<StockMonitoringJobEntity, Long> {
    
    Optional<StockMonitoringJobEntity> findByJobId(String jobId);
    
    List<StockMonitoringJobEntity> findByStockCodeAndStatus(String stockCode, String status);
    
    @Query("SELECT j FROM StockMonitoringJobEntity j WHERE j.status = 'running'")
    List<StockMonitoringJobEntity> findAllRunningJobs();
    
    @Query("SELECT j FROM StockMonitoringJobEntity j WHERE j.status = 'paused'")
    List<StockMonitoringJobEntity> findAllPausedJobs();
    
    @Query("SELECT j FROM StockMonitoringJobEntity j WHERE j.stockCode = :stockCode AND j.status = 'running'")
    Optional<StockMonitoringJobEntity> findRunningJobByStockCode(@Param("stockCode") String stockCode);
    
    boolean existsByStockCodeAndStatus(String stockCode, String status);
}
