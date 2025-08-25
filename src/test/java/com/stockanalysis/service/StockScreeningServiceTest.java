package com.stockanalysis.service;

import com.stockanalysis.StockAnalysisApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = StockAnalysisApplication.class)
class StockScreeningServiceTest {

    @Autowired
    private StockScreeningService stockScreeningService;

    @Test
    void testEvaluateMoneyFlowData() throws Exception {
        // 使用反射访问私有方法
        Method method = StockScreeningService.class.getDeclaredMethod("evaluateMoneyFlowData", List.class);
        method.setAccessible(true);
        
        // 测试用例1: 资金持续流入的情况
        List<Map<String, Object>> moneyFlowData1 = createMoneyFlowData(
            5.0, 3.0, 1.0,  // 主力资金占比：今日、5日、10日
            3.0, 1.5, 0.5   // 超大单资金占比：今日、5日、10日
        );
        
        double score1 = (Double) method.invoke(stockScreeningService, moneyFlowData1);
        
        // 验证评分在合理范围内
        assertTrue(score1 >= 0 && score1 <= 10, "评分应该在0-10之间");
        
        // 验证评分是否符合预期趋势（应该是一个相对较高的分数，因为资金持续流入）
        assertTrue(score1 > 5, "由于资金持续流入，评分应该高于基础分5分");
        
        // 测试用例2: 资金持续流出的情况
        List<Map<String, Object>> moneyFlowData2 = createMoneyFlowData(
            -5.0, -3.0, -1.0,  // 主力资金占比：今日、5日、10日
            -3.0, -1.5, -0.5   // 超大单资金占比：今日、5日、10日
        );
        
        double score2 = (Double) method.invoke(stockScreeningService, moneyFlowData2);
        
        // 验证评分是否符合预期趋势（应该是一个相对较低的分数，因为资金持续流出）
        assertTrue(score2 < 5, "由于资金持续流出，评分应该低于基础分5分");
        
        // 测试用例3: 资金趋势不一致的情况
        List<Map<String, Object>> moneyFlowData3 = createMoneyFlowData(
            5.0, -3.0, 1.0,   // 主力资金占比：今日、5日、10日
            3.0, -1.5, 0.5    // 超大单资金占比：今日、5日、10日
        );
        
        double score3 = (Double) method.invoke(stockScreeningService, moneyFlowData3);
        
        // 验证评分在合理范围内
        assertTrue(score3 >= 0 && score3 <= 10, "评分应该在0-10之间");
        
        // 测试用例4: 只有今日数据的情况（验证向后兼容性）
        List<Map<String, Object>> moneyFlowData4 = createMoneyFlowDataWithOnlyToday(
            5.0, 3.0  // 主力和超大单今日资金占比
        );
        
        double score4 = (Double) method.invoke(stockScreeningService, moneyFlowData4);
        
        // 验证评分在合理范围内
        assertTrue(score4 >= 0 && score4 <= 10, "评分应该在0-10之间");
        
        // 测试空数据情况
        double emptyScore = (Double) method.invoke(stockScreeningService, new ArrayList<>());
        assertEquals(5.0, emptyScore, 0.01, "空数据应该返回基础分5分");
    }
    
    /**
     * 创建包含今日、5日、10日资金流向数据的测试数据
     */
    private List<Map<String, Object>> createMoneyFlowData(
            double mainToday, double mainFive, double mainTen,
            double superToday, double superFive, double superTen) {
        
        List<Map<String, Object>> moneyFlowData = new ArrayList<>();
        
        // 创建今日数据
        Map<String, Object> todayMainData = new HashMap<>();
        todayMainData.put("资金类型", "主力");
        todayMainData.put("净流入额（万元）", mainToday * 1000); // 转换为万元
        todayMainData.put("净占比（%）", mainToday);
        
        Map<String, Object> todaySuperLargeData = new HashMap<>();
        todaySuperLargeData.put("资金类型", "超大单");
        todaySuperLargeData.put("净流入额（万元）", superToday * 1000); // 转换为万元
        todaySuperLargeData.put("净占比（%）", superToday);
        
        List<Map<String, Object>> todayData = Arrays.asList(todayMainData, todaySuperLargeData);
        
        // 创建5日数据
        Map<String, Object> fiveDayMainData = new HashMap<>();
        fiveDayMainData.put("资金类型", "主力");
        fiveDayMainData.put("净流入额（万元）", mainFive * 1000); // 转换为万元
        fiveDayMainData.put("净占比（%）", mainFive);
        
        Map<String, Object> fiveDaySuperLargeData = new HashMap<>();
        fiveDaySuperLargeData.put("资金类型", "超大单");
        fiveDaySuperLargeData.put("净流入额（万元）", superFive * 1000); // 转换为万元
        fiveDaySuperLargeData.put("净占比（%）", superFive);
        
        List<Map<String, Object>> fiveDayData = Arrays.asList(fiveDayMainData, fiveDaySuperLargeData);
        
        // 创建10日数据
        Map<String, Object> tenDayMainData = new HashMap<>();
        tenDayMainData.put("资金类型", "主力");
        tenDayMainData.put("净流入额（万元）", mainTen * 1000); // 转换为万元
        tenDayMainData.put("净占比（%）", mainTen);
        
        Map<String, Object> tenDaySuperLargeData = new HashMap<>();
        tenDaySuperLargeData.put("资金类型", "超大单");
        tenDaySuperLargeData.put("净流入额（万元）", superTen * 1000); // 转换为万元
        tenDaySuperLargeData.put("净占比（%）", superTen);
        
        List<Map<String, Object>> tenDayData = Arrays.asList(tenDayMainData, tenDaySuperLargeData);
        
        // 创建资金数据
        Map<String, Object> fundData = new HashMap<>();
        fundData.put("今日", todayData);
        fundData.put("5日", fiveDayData);
        fundData.put("10日", tenDayData);
        
        // 创建最新数据
        Map<String, Object> latest = new HashMap<>();
        latest.put("资金数据", fundData);
        
        moneyFlowData.add(latest);
        
        return moneyFlowData;
    }
    
    /**
     * 创建只包含今日资金流向数据的测试数据（用于验证向后兼容性）
     */
    private List<Map<String, Object>> createMoneyFlowDataWithOnlyToday(double mainToday, double superToday) {
        List<Map<String, Object>> moneyFlowData = new ArrayList<>();
        
        // 创建今日数据
        Map<String, Object> todayMainData = new HashMap<>();
        todayMainData.put("资金类型", "主力");
        todayMainData.put("净流入额（万元）", mainToday * 1000); // 转换为万元
        todayMainData.put("净占比（%）", mainToday);
        
        Map<String, Object> todaySuperLargeData = new HashMap<>();
        todaySuperLargeData.put("资金类型", "超大单");
        todaySuperLargeData.put("净流入额（万元）", superToday * 1000); // 转换为万元
        todaySuperLargeData.put("净占比（%）", superToday);
        
        List<Map<String, Object>> todayData = Arrays.asList(todayMainData, todaySuperLargeData);
        
        // 创建资金数据（只包含今日数据）
        Map<String, Object> fundData = new HashMap<>();
        fundData.put("今日", todayData);
        // 注意：这里不设置5日和10日数据
        
        // 创建最新数据
        Map<String, Object> latest = new HashMap<>();
        latest.put("资金数据", fundData);
        
        moneyFlowData.add(latest);
        
        return moneyFlowData;
    }
}