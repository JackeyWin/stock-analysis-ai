package com.stockanalysis.service;

import com.stockanalysis.entity.DailyRecommendationEntity;
import com.stockanalysis.entity.StockRecommendationDetailEntity;
import com.stockanalysis.model.DailyRecommendation;
import com.stockanalysis.model.StockRecommendationDetail;
import com.stockanalysis.repository.DailyRecommendationRepository;
import com.stockanalysis.repository.StockRecommendationDetailRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 每日推荐数据存储服务
 */
@Slf4j
@Service
public class DailyRecommendationStorageService {
    
    @Autowired
    private DailyRecommendationRepository dailyRecommendationRepository;
    
    @Autowired
    private StockRecommendationDetailRepository stockRecommendationDetailRepository;
    
    /**
     * 保存每日推荐数据到数据库
     */
    public DailyRecommendationEntity saveDailyRecommendation(DailyRecommendation dailyRecommendation) {
        try {
            // 转换并保存每日推荐数据
            DailyRecommendationEntity entity = convertToEntity(dailyRecommendation);
            DailyRecommendationEntity savedEntity = dailyRecommendationRepository.save(entity);
            
            // 保存推荐的股票详情
            if (dailyRecommendation.getRecommendedStocks() != null) {
                List<StockRecommendationDetailEntity> detailEntities = dailyRecommendation.getRecommendedStocks()
                        .stream()
                        .map(this::convertToEntity)
                        .collect(Collectors.toList());
                
                // 设置关联关系
                detailEntities.forEach(detail -> detail.setDailyRecommendation(savedEntity));
                
                // 批量保存
                stockRecommendationDetailRepository.saveAll(detailEntities);
            }
            
            log.info("成功保存每日推荐数据，ID: {}", savedEntity.getId());
            return savedEntity;
        } catch (Exception e) {
            log.error("保存每日推荐数据失败: {}", e.getMessage(), e);
            throw new RuntimeException("保存每日推荐数据失败", e);
        }
    }
    
    /**
     * 根据推荐日期获取每日推荐数据
     */
    public Optional<DailyRecommendationEntity> getDailyRecommendationByDate(String recommendationDate) {
        return dailyRecommendationRepository.findByRecommendationDate(recommendationDate);
    }
    
    /**
     * 获取最新的每日推荐数据
     */
    public Optional<DailyRecommendationEntity> getLatestDailyRecommendation() {
        return dailyRecommendationRepository.findFirstByOrderByCreateTimeDesc();
    }
    
    /**
     * 根据股票代码获取推荐详情
     */
    public List<StockRecommendationDetailEntity> getRecommendationDetailsByStockCode(String stockCode) {
        return stockRecommendationDetailRepository.findByStockCode(stockCode);
    }
    
    /**
     * 根据行业获取推荐详情
     */
    public List<StockRecommendationDetailEntity> getRecommendationDetailsBySector(String sector) {
        return stockRecommendationDetailRepository.findBySector(sector);
    }
    
    /**
     * 获取热门推荐
     */
    public List<StockRecommendationDetailEntity> getHotRecommendations() {
        return stockRecommendationDetailRepository.findByIsHotTrue();
    }
    
    /**
     * 转换DailyRecommendation模型为DailyRecommendationEntity实体
     */
    private DailyRecommendationEntity convertToEntity(DailyRecommendation dailyRecommendation) {
        DailyRecommendationEntity entity = new DailyRecommendationEntity();
        entity.setRecommendationId(dailyRecommendation.getRecommendationId());
        entity.setRecommendationDate(dailyRecommendation.getRecommendationDate());
        entity.setCreateTime(dailyRecommendation.getCreateTime() != null ? 
                dailyRecommendation.getCreateTime() : LocalDateTime.now());
        // 使用新的policyHotspotsAndIndustryHotspots字段
        if (dailyRecommendation.getPolicyHotspotsAndIndustryHotspots() != null) {
            // 将Map转换为JSON字符串存储
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                entity.setPolicyHotspotsAndIndustryHotspots(
                    objectMapper.writeValueAsString(dailyRecommendation.getPolicyHotspotsAndIndustryHotspots()));
            } catch (Exception e) {
                log.warn("序列化policyHotspotsAndIndustryHotspots失败: {}", e.getMessage());
                // 如果序列化失败，使用简化处理
                entity.setPolicyHotspotsAndIndustryHotspots(
                    dailyRecommendation.getPolicyHotspotsAndIndustryHotspots().toString());
            }
        }
        entity.setSummary(dailyRecommendation.getSummary());
        entity.setAnalystView(dailyRecommendation.getAnalystView());
        entity.setRiskWarning(dailyRecommendation.getRiskWarning());
        entity.setStatus(dailyRecommendation.getStatus());
        entity.setVersion(dailyRecommendation.getVersion());
        return entity;
    }
    
    /**
     * 转换StockRecommendationDetail模型为StockRecommendationDetailEntity实体
     */
    private StockRecommendationDetailEntity convertToEntity(StockRecommendationDetail detail) {
        StockRecommendationDetailEntity entity = new StockRecommendationDetailEntity();
        entity.setStockCode(detail.getStockCode());
        entity.setStockName(detail.getStockName());
        entity.setSector(detail.getSector());
        entity.setRecommendationReason(detail.getRecommendationReason());
        entity.setRating(detail.getRating());
        entity.setScore(detail.getScore());
        entity.setTargetPrice(detail.getTargetPrice());
        entity.setCurrentPrice(detail.getCurrentPrice());
        entity.setExpectedReturn(detail.getExpectedReturn());
        entity.setRiskLevel(detail.getRiskLevel());
        entity.setInvestmentPeriod(detail.getInvestmentPeriod());
        entity.setTechnicalAnalysis(detail.getTechnicalAnalysis());
        entity.setFundamentalAnalysis(detail.getFundamentalAnalysis());
        entity.setNewsAnalysis(detail.getNewsAnalysis());
        entity.setKeyMetrics(detail.getKeyMetrics());
        entity.setRecommendTime(detail.getRecommendTime());
        entity.setSortOrder(detail.getSortOrder());
        entity.setIsHot(detail.getIsHot() != null ? detail.getIsHot() : false);
        return entity;
    }
}