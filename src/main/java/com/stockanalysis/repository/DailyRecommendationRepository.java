package com.stockanalysis.repository;

import com.stockanalysis.entity.DailyRecommendationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DailyRecommendationRepository extends JpaRepository<DailyRecommendationEntity, Long> {
    
    /**
     * 根据推荐ID查找每日推荐
     */
    Optional<DailyRecommendationEntity> findByRecommendationId(String recommendationId);
    
    /**
     * 根据推荐日期查找每日推荐
     */
    Optional<DailyRecommendationEntity> findByRecommendationDate(String recommendationDate);
    
    /**
     * 查找最新的每日推荐
     */
    Optional<DailyRecommendationEntity> findFirstByOrderByCreateTimeDesc();
}