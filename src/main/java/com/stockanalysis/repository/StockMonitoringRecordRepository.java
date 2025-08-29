package com.stockanalysis.repository;

import com.stockanalysis.entity.StockMonitoringRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockMonitoringRecordRepository extends JpaRepository<StockMonitoringRecordEntity, Long> {

    @Query("SELECT r FROM StockMonitoringRecordEntity r WHERE r.stockCode = :stockCode AND r.createdAt BETWEEN :start AND :end ORDER BY r.createdAt DESC")
    List<StockMonitoringRecordEntity> findTodayByStockCode(@Param("stockCode") String stockCode,
                                                           @Param("start") LocalDateTime start,
                                                           @Param("end") LocalDateTime end);
}


