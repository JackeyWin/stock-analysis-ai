package com.stockanalysis.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockanalysis.entity.DailyRecommendationEntity;
import com.stockanalysis.entity.StockRecommendationDetailEntity;
import com.stockanalysis.model.DailyRecommendation;
import com.stockanalysis.model.StockRecommendationDetail;
import com.stockanalysis.repository.DailyRecommendationRepository;
import com.stockanalysis.repository.StockRecommendationDetailRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import com.stockanalysis.entity.StockRecommendationDetailEntity;

/**
 * 每日推荐管理服务
 * 使用数据库存储推荐数据
 */
@Slf4j
@Service
public class DailyRecommendationService {

    private final AIStockPickerService aiStockPickerService;
    private final DailyRecommendationRepository dailyRecommendationRepository;
    private final StockRecommendationDetailRepository stockRecommendationDetailRepository;
    private final ObjectMapper objectMapper;

    public DailyRecommendationService(AIStockPickerService aiStockPickerService,
            DailyRecommendationRepository dailyRecommendationRepository,
            StockRecommendationDetailRepository stockRecommendationDetailRepository) {
        this.aiStockPickerService = aiStockPickerService;
        this.dailyRecommendationRepository = dailyRecommendationRepository;
        this.stockRecommendationDetailRepository = stockRecommendationDetailRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 生成每日推荐
     */
    public DailyRecommendation generateDailyRecommendation() {
        try {
            log.info("开始生成每日推荐");
            
            // 调用AI选股服务
            DailyRecommendation recommendation = aiStockPickerService.performAIStockPicking().get();
            
            // 保存推荐
            saveDailyRecommendation(recommendation);
            
            log.info("每日推荐生成完成，推荐{}只股票", 
                    recommendation.getRecommendedStocks() != null ? recommendation.getRecommendedStocks().size() : 0);
            
            return recommendation;
            
        } catch (Exception e) {
            log.error("生成每日推荐失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成每日推荐失败: " + e.getMessage());
        }
    }

    /**
     * 获取今日推荐
     */
    public DailyRecommendation getTodayRecommendation() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return dailyRecommendationRepository.findByRecommendationDate(today)
                .map(this::convertToModel)
                .orElse(null);
    }
    
    /**
     * 将实体类转换为模型类
     */
    private DailyRecommendation convertToModel(DailyRecommendationEntity entity) {
        DailyRecommendation model = new DailyRecommendation();
        model.setRecommendationId(entity.getRecommendationId());
        model.setRecommendationDate(entity.getRecommendationDate());
        model.setCreateTime(entity.getCreateTime());
        model.setSummary(entity.getSummary());
        model.setAnalystView(entity.getAnalystView());
        model.setRiskWarning(entity.getRiskWarning());
        model.setStatus(entity.getStatus());
        model.setVersion(entity.getVersion());
        
        // 转换政策热点和行业热点
        if (entity.getPolicyHotspotsAndIndustryHotspots() != null) {
            try {
                Map<String, String> hotspots = objectMapper.readValue(
                    entity.getPolicyHotspotsAndIndustryHotspots(), 
                    new TypeReference<Map<String, String>>() {});
                model.setPolicyHotspotsAndIndustryHotspots(hotspots);
            } catch (Exception e) {
                log.warn("转换政策热点和行业热点失败: {}", e.getMessage());
            }
        }
        
        // 转换推荐股票列表 - 安全处理懒加载
        try {
            if (entity.getRecommendedStocks() != null) {
                List<StockRecommendationDetail> details = entity.getRecommendedStocks().stream()
                        .map(this::convertDetailEntityToModel)
                        .collect(Collectors.toList());
                model.setRecommendedStocks(details);
            }
        } catch (Exception e) {
            log.warn("转换推荐股票列表时出现懒加载问题: {}", e.getMessage());
            model.setRecommendedStocks(new ArrayList<>());
        }
        
        return model;
    }
    
    /**
     * 将股票推荐详情实体类转换为模型类
     */
    private StockRecommendationDetail convertToModel(StockRecommendationDetailEntity entity) {
        StockRecommendationDetail model = new StockRecommendationDetail();
        model.setStockCode(entity.getStockCode());
        model.setStockName(entity.getStockName());
        model.setSector(entity.getSector());
        model.setRecommendationReason(entity.getRecommendationReason());
        model.setRating(entity.getRating());
        model.setScore(entity.getScore());
        model.setTargetPrice(entity.getTargetPrice());
        model.setCurrentPrice(entity.getCurrentPrice());
        model.setExpectedReturn(entity.getExpectedReturn());
        model.setRiskLevel(entity.getRiskLevel());
        model.setInvestmentPeriod(entity.getInvestmentPeriod());
        model.setTechnicalAnalysis(entity.getTechnicalAnalysis());
        model.setFundamentalAnalysis(entity.getFundamentalAnalysis());
        model.setNewsAnalysis(entity.getNewsAnalysis());
        model.setKeyMetrics(entity.getKeyMetrics());
        model.setRecommendTime(entity.getRecommendTime());
        model.setSortOrder(entity.getSortOrder());
        model.setIsHot(entity.getIsHot());
        return model;
    }

    /**
     * 获取指定日期的推荐
     */
    public DailyRecommendation getRecommendationByDate(String date) {
        return dailyRecommendationRepository.findByRecommendationDate(date)
                .map(this::convertToModel)
                .orElse(null);
    }

    /**
     * 获取推荐历史
     */
    public List<DailyRecommendation> getRecommendationHistory(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);
        
        // 使用日期范围查询数据库
        // 注意：这里假设数据库中有足够多的历史数据
        // 实际实现可能需要根据具体需求调整
        List<DailyRecommendationEntity> entities = dailyRecommendationRepository
                .findAll()
                .stream()
                .filter(entity -> {
                    LocalDate entityDate = LocalDate.parse(entity.getRecommendationDate(), 
                            DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    return !entityDate.isBefore(startDate) && !entityDate.isAfter(endDate);
                })
                .sorted((a, b) -> b.getRecommendationDate().compareTo(a.getRecommendationDate()))
                .collect(Collectors.toList());
        
        return entities.stream()
                .map(this::convertToModel)
                .collect(Collectors.toList());
    }

    /**
     * 刷新推荐
     */
    public DailyRecommendation refreshRecommendation() {
        log.info("手动刷新每日推荐");
        return generateDailyRecommendation();
    }

    /**
     * 保存每日推荐
     */
    public void saveDailyRecommendation(DailyRecommendation recommendation) {
        if (recommendation == null) {
            return;
        }
        
        // 检查是否已存在相同日期的推荐
        String date = recommendation.getRecommendationDate();
        Optional<DailyRecommendationEntity> existing = dailyRecommendationRepository.findByRecommendationDate(date);
        
        DailyRecommendationEntity entity;
        if (existing.isPresent()) {
            // 更新现有记录
            entity = existing.get();
            // 删除现有的推荐股票详情
            if (entity.getRecommendedStocks() != null) {
                stockRecommendationDetailRepository.deleteAll(entity.getRecommendedStocks());
            }
        } else {
            // 创建新记录
            entity = new DailyRecommendationEntity();
            entity.setRecommendationId(recommendation.getRecommendationId());
            entity.setRecommendationDate(recommendation.getRecommendationDate());
        }
        
        // 更新实体字段
        entity.setCreateTime(recommendation.getCreateTime() != null ? 
            recommendation.getCreateTime() : LocalDateTime.now());
        entity.setSummary(recommendation.getSummary());
        entity.setAnalystView(recommendation.getAnalystView());
        entity.setRiskWarning(recommendation.getRiskWarning());
        entity.setStatus(recommendation.getStatus());
        entity.setVersion(recommendation.getVersion());
        
        // 转换政策热点和行业热点为JSON字符串
        if (recommendation.getPolicyHotspotsAndIndustryHotspots() != null) {
            try {
                String hotspotsJson = objectMapper.writeValueAsString(
                    recommendation.getPolicyHotspotsAndIndustryHotspots());
                entity.setPolicyHotspotsAndIndustryHotspots(hotspotsJson);
            } catch (Exception e) {
                log.warn("转换政策热点和行业热点为JSON失败: {}", e.getMessage());
            }
        }
        
        // 保存主实体
        DailyRecommendationEntity savedEntity = dailyRecommendationRepository.save(entity);
        
        // 保存推荐的股票详情
        if (recommendation.getRecommendedStocks() != null && !recommendation.getRecommendedStocks().isEmpty()) {
            List<StockRecommendationDetailEntity> detailEntities = recommendation.getRecommendedStocks()
                    .stream()
                    .map(this::convertToDetailEntity)
                    .collect(Collectors.toList());
            
            // 设置关联关系
            detailEntities.forEach(detail -> detail.setDailyRecommendation(savedEntity));
            
            // 批量保存
            stockRecommendationDetailRepository.saveAll(detailEntities);
            
            log.info("成功保存{}只推荐股票详情", detailEntities.size());
        }
        
        log.info("每日推荐已保存到数据库: {}", date);
    }



    /**
     * 获取推荐状态
     */
    public Map<String, Object> getRecommendationStatus() {
        Map<String, Object> status = new HashMap<>();
        
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Optional<DailyRecommendationEntity> todayRecommendation = dailyRecommendationRepository.findByRecommendationDate(today);
        
        status.put("hasToday", todayRecommendation.isPresent());
        status.put("todayDate", today);
        status.put("recordCount", dailyRecommendationRepository.count());
        status.put("lastUpdate", todayRecommendation.isPresent() ? todayRecommendation.get().getCreateTime() : null);
        status.put("status", todayRecommendation.isPresent() ? todayRecommendation.get().getStatus() : "NONE");
        
        return status;
    }

    /**
     * 检查是否需要更新推荐
     */
    public boolean needsUpdate() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Optional<DailyRecommendationEntity> todayRecommendation = dailyRecommendationRepository.findByRecommendationDate(today);
        
        if (!todayRecommendation.isPresent()) {
            return true;
        }
        
        // 检查是否是今天生成的
        LocalDateTime createTime = todayRecommendation.get().getCreateTime();
        if (createTime == null) {
            return true;
        }
        
        LocalDate createDate = createTime.toLocalDate();
        return !createDate.equals(LocalDate.now());
    }

    /**
     * 获取推荐摘要（用于首页展示）
     */
    public Map<String, Object> getRecommendationSummary() {
        DailyRecommendation todayRecommendation = getTodayRecommendation();
        Map<String, Object> summary = new HashMap<>();
        
        if (todayRecommendation == null) {
            summary.put("available", false);
            summary.put("message", "今日推荐暂未生成");
            return summary;
        }
        
        summary.put("available", true);
        summary.put("date", todayRecommendation.getRecommendationDate());
        summary.put("summary", todayRecommendation.getSummary());
        
        // 添加分析师观点
        summary.put("analystView", todayRecommendation.getAnalystView());
        
        // 处理新的policyHotspotsAndIndustryHotspots字段类型 (Map<String, String>)
        Map<String, String> policyHotspotsAndIndustryHotspots = todayRecommendation.getPolicyHotspotsAndIndustryHotspots();
        if (policyHotspotsAndIndustryHotspots != null) {
            // 将Map<String, String>转换为字符串摘要
            StringBuilder hotspotsSummary = new StringBuilder();
            for (Map.Entry<String, String> entry : policyHotspotsAndIndustryHotspots.entrySet()) {
                hotspotsSummary.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            summary.put("hotspotsSummary", hotspotsSummary.toString());
        }
        
        // 获取前3只热门推荐
        List<StockRecommendationDetail> topStocks = new ArrayList<>();
        List<StockRecommendationDetail> recommendedStocks = todayRecommendation.getRecommendedStocks();
        if (recommendedStocks != null) {
            
            topStocks = recommendedStocks.stream()
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .limit(3)
                    .collect(Collectors.toList());
            summary.put("totalCount", recommendedStocks.size());
        } else {
            summary.put("totalCount", 0);
        }
        
        summary.put("topStocks", topStocks);
        
        return summary;
    }

    public StockRecommendationDetail getRecommendationDetail(String stockCode) {
        List<StockRecommendationDetailEntity> entities = stockRecommendationDetailRepository.findByStockCode(stockCode);
        if (entities != null && !entities.isEmpty()) {
            // 返回最新的推荐详情
            StockRecommendationDetailEntity entity = entities.stream()
                .max((a, b) -> a.getRecommendTime().compareTo(b.getRecommendTime()))
                .orElse(entities.get(0));
            return convertDetailEntityToModel(entity);
        }
        return null;
    }

    /**
     * 将StockRecommendationDetailEntity转换为StockRecommendationDetail模型
     */
    private StockRecommendationDetail convertDetailEntityToModel(StockRecommendationDetailEntity entity) {
        if (entity == null) {
            return null;
        }
        
        StockRecommendationDetail model = new StockRecommendationDetail();
        model.setStockCode(entity.getStockCode());
        model.setStockName(entity.getStockName());
        model.setSector(entity.getSector());
        model.setRecommendationReason(entity.getRecommendationReason());
        model.setRating(entity.getRating());
        model.setScore(entity.getScore());
        model.setTargetPrice(entity.getTargetPrice());
        model.setCurrentPrice(entity.getCurrentPrice());
        model.setExpectedReturn(entity.getExpectedReturn());
        model.setRiskLevel(entity.getRiskLevel());
        model.setInvestmentPeriod(entity.getInvestmentPeriod());
        model.setTechnicalAnalysis(entity.getTechnicalAnalysis());
        model.setFundamentalAnalysis(entity.getFundamentalAnalysis());
        model.setNewsAnalysis(entity.getNewsAnalysis());
        model.setKeyMetrics(entity.getKeyMetrics());
        model.setRecommendTime(entity.getRecommendTime());
        model.setSortOrder(entity.getSortOrder());
        model.setIsHot(entity.getIsHot());
        return model;
    }

    /**
     * 将StockRecommendationDetail模型转换为StockRecommendationDetailEntity实体
     */
    private StockRecommendationDetailEntity convertToDetailEntity(StockRecommendationDetail detail) {
        if (detail == null) {
            return null;
        }
        
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

    /**
     * 获取可用的推荐日期列表
     */
    public List<String> getAvailableDates() {
        try {
            log.info("获取可用的推荐日期列表");
            
            List<DailyRecommendationEntity> entities = dailyRecommendationRepository.findAllByOrderByRecommendationDateDesc();
            
            return entities.stream()
                    .map(DailyRecommendationEntity::getRecommendationDate)
                    .distinct()
                    .sorted((a, b) -> b.compareTo(a)) // 按日期降序排列
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("获取可用日期列表失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 根据日期获取推荐摘要
     */
    public Map<String, Object> getRecommendationSummaryByDate(String date) {
        try {
            log.info("获取{}的推荐摘要", date);
            
            DailyRecommendationEntity entity = dailyRecommendationRepository.findByRecommendationDate(date)
                    .orElse(null);
            
            if (entity == null) {
                return null;
            }
            
            DailyRecommendation recommendation = convertToModel(entity);
            return buildRecommendationSummary(recommendation);
            
        } catch (Exception e) {
            log.error("获取{}的推荐摘要失败: {}", date, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建推荐摘要（通用方法，可用于任何推荐数据）
     */
    private Map<String, Object> buildRecommendationSummary(DailyRecommendation recommendation) {
        Map<String, Object> summary = new HashMap<>();
        
        if (recommendation == null) {
            summary.put("available", false);
            summary.put("message", "推荐数据不存在");
            return summary;
        }
        
        summary.put("available", true);
        summary.put("date", recommendation.getRecommendationDate());
        summary.put("summary", recommendation.getSummary());
        
        // 添加分析师观点
        summary.put("analystView", recommendation.getAnalystView());
        
        // 处理政策热点和行业热点
        Map<String, String> policyHotspotsAndIndustryHotspots = recommendation.getPolicyHotspotsAndIndustryHotspots();
        if (policyHotspotsAndIndustryHotspots != null) {
            // 将Map<String, String>转换为字符串摘要
            StringBuilder hotspotsSummary = new StringBuilder();
            for (Map.Entry<String, String> entry : policyHotspotsAndIndustryHotspots.entrySet()) {
                hotspotsSummary.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            summary.put("hotspotsSummary", hotspotsSummary.toString());
        }
        
        // 获取前3只热门推荐
        List<StockRecommendationDetail> topStocks = new ArrayList<>();
        List<StockRecommendationDetail> recommendedStocks = recommendation.getRecommendedStocks();
        if (recommendedStocks != null) {
            topStocks = recommendedStocks.stream()
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .limit(3)
                    .collect(Collectors.toList());
            summary.put("totalCount", recommendedStocks.size());
        } else {
            summary.put("totalCount", 0);
        }
        
        summary.put("topStocks", topStocks);
        
        return summary;
    }
}
