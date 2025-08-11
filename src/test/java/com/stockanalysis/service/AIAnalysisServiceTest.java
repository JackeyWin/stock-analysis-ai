package com.stockanalysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockanalysis.model.AIAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI分析服务测试类
 */
class AIAnalysisServiceTest {

    @Mock
    private StockAnalysisAI stockAnalysisAI;

    private AIAnalysisService aiAnalysisService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ObjectMapper objectMapper = new ObjectMapper();
        aiAnalysisService = new AIAnalysisService(stockAnalysisAI, objectMapper);
    }

    @Test
    void testParseAIResponseWithNumberedFormat() {
        // 测试实际的AI响应格式
        String aiResponse = """
                ### 股票 601127（赛力斯）技术分析报告

                基于提供的技术指标数据、最近股价数据、新闻数据、资金流向数据和融资融券数据，我对赛力斯（601127）进行了全面技术分析。分析周期为2024-07-26至2025-08-06，重点包括趋势、形态、均线、RSI、价格预测和交易建议。所有分析基于客观数据，避免主观臆测。

                #### 1. 趋势分析: 
                当前技术趋势为**盘整趋势**。价格在126.26（20日及60日支撑位）至130附近区间波动，无明显单边方向。

                #### 2. 技术形态: 
                识别为**震荡整理形态**，无明显经典形态（如头肩顶或双底）。

                #### 3. 移动平均线: 
                移动平均线呈现**空头排列迹象**，但未完全确认。

                #### 4. RSI指标: 
                RSI指标显示**超卖状态缓解**，但仍需警惕。

                #### 5. 价格预测: 
                未来1-2周价格走势预计为**区间震荡，偏向小幅反弹**。

                #### 6. 交易建议: 
                建议**谨慎持有或轻仓买入**。
                """;

        // 使用反射调用私有方法进行测试
        try {
            java.lang.reflect.Method method = AIAnalysisService.class.getDeclaredMethod("parseAIResponse", String.class);
            method.setAccessible(true);
            AIAnalysisResult result = (AIAnalysisResult) method.invoke(aiAnalysisService, aiResponse);

            // 验证解析结果
            assertNotNull(result);
            assertNotNull(result.getTrendAnalysis());
            assertNotNull(result.getTechnicalPattern());
            assertNotNull(result.getMovingAverage());
            assertNotNull(result.getRsiAnalysis());
            assertNotNull(result.getPricePredict());
            assertNotNull(result.getTradingAdvice());

            // 验证具体内容
            assertTrue(result.getTrendAnalysis().contains("盘整趋势"));
            assertTrue(result.getTechnicalPattern().contains("震荡整理形态"));
            assertTrue(result.getMovingAverage().contains("空头排列迹象"));
            assertTrue(result.getRsiAnalysis().contains("超卖状态缓解"));
            assertTrue(result.getPricePredict().contains("区间震荡"));
            assertTrue(result.getTradingAdvice().contains("谨慎持有"));

            // 打印结果用于调试
            System.out.println("趋势分析: " + result.getTrendAnalysis());
            System.out.println("技术形态: " + result.getTechnicalPattern());
            System.out.println("移动平均线: " + result.getMovingAverage());
            System.out.println("RSI指标: " + result.getRsiAnalysis());
            System.out.println("价格预测: " + result.getPricePredict());
            System.out.println("交易建议: " + result.getTradingAdvice());

        } catch (Exception e) {
            fail("测试失败: " + e.getMessage());
        }
    }

    @Test
    void testParseAIResponseWithStandardFormat() {
        // 测试标准格式
        String aiResponse = """
                - 趋势分析: 当前处于上升趋势，价格突破关键阻力位
                - 技术形态: 形成双底形态，显示反转信号
                - 移动平均线: MA5上穿MA10，呈现金叉信号
                - RSI指标: RSI为65，接近超买区域但仍在合理范围
                - 价格预测: 未来1-2周预计上涨5-10%
                - 交易建议: 建议买入，止损位设在支撑位下方
                """;

        try {
            java.lang.reflect.Method method = AIAnalysisService.class.getDeclaredMethod("parseAIResponse", String.class);
            method.setAccessible(true);
            AIAnalysisResult result = (AIAnalysisResult) method.invoke(aiAnalysisService, aiResponse);

            // 验证解析结果
            assertNotNull(result);
            assertTrue(result.getTrendAnalysis().contains("上升趋势"));
            assertTrue(result.getTechnicalPattern().contains("双底形态"));
            assertTrue(result.getMovingAverage().contains("金叉信号"));
            assertTrue(result.getRsiAnalysis().contains("RSI为65"));
            assertTrue(result.getPricePredict().contains("上涨5-10%"));
            assertTrue(result.getTradingAdvice().contains("建议买入"));

        } catch (Exception e) {
            fail("测试失败: " + e.getMessage());
        }
    }
}
