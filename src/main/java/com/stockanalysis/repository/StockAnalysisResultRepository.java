package com.stockanalysis.repository;

import com.stockanalysis.entity.StockAnalysisResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockAnalysisResultRepository extends JpaRepository<StockAnalysisResultEntity, Long> {
    
    /**
     * 根据机器标识查找分析结果
     */
    List<StockAnalysisResultEntity> findByMachineId(String machineId);
    
    /**
     * 根据股票代码查找分析结果
     */
    List<StockAnalysisResultEntity> findByStockCode(String stockCode);
    
    /**
     * 根据机器标识和股票代码查找分析结果
     */
    List<StockAnalysisResultEntity> findByMachineIdAndStockCode(String machineId, String stockCode);
    
    /**
     * 根据机器标识和股票代码查找最新的分析结果
     */
    @Query("SELECT s FROM StockAnalysisResultEntity s WHERE s.machineId = :machineId AND s.stockCode = :stockCode ORDER BY s.analysisTime DESC")
    List<StockAnalysisResultEntity> findLatestByMachineIdAndStockCode(@Param("machineId") String machineId, @Param("stockCode") String stockCode);
    
    /**
     * 根据机器标识查找最近的分析结果
     */
    @Query("SELECT s FROM StockAnalysisResultEntity s WHERE s.machineId = :machineId ORDER BY s.analysisTime DESC")
    List<StockAnalysisResultEntity> findRecentByMachineId(@Param("machineId") String machineId);
    
    /**
     * 根据时间范围查找分析结果
     */
    @Query("SELECT s FROM StockAnalysisResultEntity s WHERE s.analysisTime BETWEEN :startTime AND :endTime")
    List<StockAnalysisResultEntity> findByAnalysisTimeBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
    
    /**
     * 根据机器标识和时间范围查找分析结果
     */
    @Query("SELECT s FROM StockAnalysisResultEntity s WHERE s.machineId = :machineId AND s.analysisTime BETWEEN :startTime AND :endTime")
    List<StockAnalysisResultEntity> findByMachineIdAndAnalysisTimeBetween(
            @Param("machineId") String machineId, 
            @Param("startTime") LocalDateTime startTime, 
            @Param("endTime") LocalDateTime endTime);
    
    /**
     * 统计机器标识的分析次数
     */
    @Query("SELECT COUNT(s) FROM StockAnalysisResultEntity s WHERE s.machineId = :machineId")
    Long countByMachineId(@Param("machineId") String machineId);
    
    /**
     * 统计股票代码的分析次数
     */
    @Query("SELECT COUNT(s) FROM StockAnalysisResultEntity s WHERE s.stockCode = :stockCode")
    Long countByStockCode(@Param("stockCode") String stockCode);
    
    /**
     * 根据机器标识和股票代码删除分析结果
     */
    @Query("DELETE FROM StockAnalysisResultEntity s WHERE s.machineId = :machineId AND s.stockCode = :stockCode AND s.id != :excludeId")
    void deleteByMachineIdAndStockCodeExcludeId(@Param("machineId") String machineId, @Param("stockCode") String stockCode, @Param("excludeId") Long excludeId);
    
    /**
     * 根据机器标识和股票代码删除分析结果
     */
    @Query("DELETE FROM StockAnalysisResultEntity s WHERE s.machineId = :machineId AND s.stockCode = :stockCode")
    void deleteByMachineIdAndStockCode(@Param("machineId") String machineId, @Param("stockCode") String stockCode);
}