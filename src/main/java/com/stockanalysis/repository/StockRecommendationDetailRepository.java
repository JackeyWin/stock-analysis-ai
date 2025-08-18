package com.stockanalysis.repository;

import com.stockanalysis.entity.StockRecommendationDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockRecommendationDetailRepository extends JpaRepository<StockRecommendationDetailEntity, Long> {
    
    /**
     * 根据股票代码查找推荐详情
     */
    List<StockRecommendationDetailEntity> findByStockCode(String stockCode);
    
    /**
     * 根据行业查找推荐详情
     */
    List<StockRecommendationDetailEntity> findBySector(String sector);
    
    /**
     * 根据推荐日期查找推荐详情
     */
    List<StockRecommendationDetailEntity> findByDailyRecommendationRecommendationDate(String recommendationDate);
    
    /**
     * 查找热门推荐
     */
    List<StockRecommendationDetailEntity> findByIsHotTrue();
}