package com.stockanalysis.service;

import com.stockanalysis.entity.DailyRecommendationEntity;
import com.stockanalysis.entity.StockRecommendationDetailEntity;
import com.stockanalysis.model.DailyRecommendation;
import com.stockanalysis.model.StockRecommendationDetail;
import com.stockanalysis.repository.DailyRecommendationRepository;
import com.stockanalysis.repository.StockRecommendationDetailRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class DailyRecommendationServiceTest {

    @Autowired
    private DailyRecommendationService dailyRecommendationService;

    @Autowired
    private DailyRecommendationRepository dailyRecommendationRepository;

    @Autowired
    private StockRecommendationDetailRepository stockRecommendationDetailRepository;

    @Test
    void testSaveAndRetrieveDailyRecommendation() {
        // 创建测试数据
        DailyRecommendation recommendation = createTestRecommendation();

        // 保存推荐
        dailyRecommendationService.saveDailyRecommendation(recommendation);

        // 验证数据已保存到数据库
        DailyRecommendationEntity savedEntity = dailyRecommendationRepository
                .findByRecommendationDate(recommendation.getRecommendationDate())
                .orElse(null);
        assertNotNull(savedEntity);
        assertEquals(recommendation.getRecommendationId(), savedEntity.getRecommendationId());
        assertEquals(recommendation.getRecommendationDate(), savedEntity.getRecommendationDate());
        assertEquals(recommendation.getSummary(), savedEntity.getSummary());

        // 验证能够从服务中获取推荐
        DailyRecommendation retrieved = dailyRecommendationService
                .getRecommendationByDate(recommendation.getRecommendationDate());
        assertNotNull(retrieved);
        assertEquals(recommendation.getRecommendationId(), retrieved.getRecommendationId());
        assertEquals(recommendation.getRecommendationDate(), retrieved.getRecommendationDate());
        assertEquals(recommendation.getSummary(), retrieved.getSummary());
    }

    @Test
    void testGetTodayRecommendation() {
        // 创建今天的推荐
        DailyRecommendation todayRecommendation = createTestRecommendation();
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        todayRecommendation.setRecommendationDate(today);
        dailyRecommendationService.saveDailyRecommendation(todayRecommendation);

        // 获取今天的推荐
        DailyRecommendation retrieved = dailyRecommendationService.getTodayRecommendation();
        assertNotNull(retrieved);
        assertEquals(today, retrieved.getRecommendationDate());
    }

    @Test
    void testGetRecommendationStatus() {
        // 创建今天的测试数据
        DailyRecommendation recommendation = createTestRecommendation();
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        recommendation.setRecommendationDate(today);
        dailyRecommendationService.saveDailyRecommendation(recommendation);

        // 获取推荐状态
        Map<String, Object> status = dailyRecommendationService.getRecommendationStatus();
        assertTrue((Boolean) status.get("hasToday"));
        assertEquals(today, status.get("todayDate"));
        assertTrue((Long) status.get("recordCount") > 0);
    }

    @Test
    void testNeedsUpdate() {
        // 清空数据库中的数据
        dailyRecommendationRepository.deleteAll();

        // 检查是否需要更新（应该需要，因为没有今天的推荐）
        assertTrue(dailyRecommendationService.needsUpdate());

        // 创建今天的推荐
        DailyRecommendation todayRecommendation = createTestRecommendation();
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        todayRecommendation.setRecommendationDate(today);
        dailyRecommendationService.saveDailyRecommendation(todayRecommendation);

        // 检查是否需要更新（应该不需要，因为已经有今天的推荐）
        assertFalse(dailyRecommendationService.needsUpdate());
    }

    private DailyRecommendation createTestRecommendation() {
        DailyRecommendation recommendation = new DailyRecommendation();
        recommendation.setRecommendationId(UUID.randomUUID().toString());
        recommendation.setRecommendationDate("2023-01-01");
        recommendation.setCreateTime(LocalDateTime.now());
        recommendation.setSummary("Test summary");
        recommendation.setAnalystView("Test analyst view");
        recommendation.setRiskWarning("Test risk warning");
        recommendation.setStatus("ACTIVE");
        recommendation.setVersion(1);

        // 添加政策热点和行业热点
        Map<String, String> hotspots = new HashMap<>();
        hotspots.put("Tech", "政策支持科技创新");
        hotspots.put("Green Energy", "碳中和政策利好");
        recommendation.setPolicyHotspotsAndIndustryHotspots(hotspots);

        // 添加推荐股票
        List<StockRecommendationDetail> stocks = new ArrayList<>();
        StockRecommendationDetail stock1 = new StockRecommendationDetail();
        stock1.setStockCode("000001");
        stock1.setStockName("平安银行");
        stock1.setSector("Banking");
        stock1.setRecommendationReason("业绩稳定增长");
        stock1.setRating("推荐");
        stock1.setScore(8.5);
        stock1.setTargetPrice(15.0);
        stock1.setCurrentPrice(12.0);
        stock1.setExpectedReturn(25.0);
        stock1.setRiskLevel("中");
        stock1.setInvestmentPeriod("中期");
        stocks.add(stock1);

        recommendation.setRecommendedStocks(stocks);

        return recommendation;
    }
}