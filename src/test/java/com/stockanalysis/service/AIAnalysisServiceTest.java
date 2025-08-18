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
    void testParseAIResponseWithNewFormat() {
        // 测试新的AI响应格式
        String aiResponse = """
                【公司基本面分析】
                - 财务表现：连续亏损，2025Q1归属净利润-5425万（同比-55.14%），毛利率骤降至2.81%（2025Q1），经营现金流为负（-0.1224元/股），营收增长疲软（5.09% YoY）。(财务数据)
                - 经营现状：高负债压力（资产负债率48.65%），存货周转率低（0.24次），盈利能力恶化（ROE -2.47%），现金流紧张制约运营。(财务数据)
                - 估值分析：PE(TTM)-12839，PB 2261，严重脱离行业均值，反映基本面与股价背离，存在巨大估值泡沫。(财务数据)

                【行业趋势和政策导向】
                - 行业趋势：军工行业景气度受地缘局势催化，军民融合+央改加速推进，但专用设备领域产能过剩风险升温。(概念行业)
                - 政策影响：国防预算增速维持高位，国资委推动军工央企整合，短期刺激订单预期，但长期依赖改革落地实效。(政策面)
                - 对股价影响：政策利好支撑短期炒作，中期需验证订单放量，高估值需基本面修复配合。

                【操作策略】
                - 短期(1-2周)：[卖出：67.82-70元 | 止损：64.22元 | 仓位：0%] 理由：RSI 97.63超买+量能萎缩+龙虎榜利空，技术回调压力极大。(技术面+消息面)
                - 中期(1-3月)：[观察区间：50-60元 | 目标位：75元 | 止损位：45元 | 仓位：≤5%] 理由：等待行业政策兑现与季报改善信号，破位需止损。(政策面+基本面)
                - 长期(3月-1年)：[减持区：60元以上 | 目标：重组落地 | 仓位：0%] 理由：PB 2261不可持续，基本面修复前宜回避。(估值+财务面)

                【盘面分析】
                - 预判关键点位：支撑64.22元（布林上轨），阻力70元心理关口。若高开无量冲高，果断止盈；低开破64.22元则触发止损。
                - 突发事件应对：关注军工板块联动（板块RSI 87.17超买），若板块回调超2%将加剧个股抛压；主力净流入48735万元（资金面）或延缓跌速，但需防获利盘兑现。
                - 风险预警：两融余额下降（5.497亿→5.592亿）+融券净卖出4400股，反映杠杆资金撤退，加剧波动。(资金面)
                """;

        // 使用反射调用私有方法进行测试
        try {
            java.lang.reflect.Method method = AIAnalysisService.class.getDeclaredMethod("parseAIResponse", String.class);
            method.setAccessible(true);
            AIAnalysisResult result = (AIAnalysisResult) method.invoke(aiAnalysisService, aiResponse);

            // 验证解析结果
            assertNotNull(result);
            assertNotNull(result.getCompanyFundamentalAnalysis());
            assertNotNull(result.getOperationStrategy());
            assertNotNull(result.getIntradayOperations());
            assertNotNull(result.getIndustryPolicyOrientation());

            // 验证具体内容
            assertTrue(result.getCompanyFundamentalAnalysis().contains("财务表现"));
            assertTrue(result.getOperationStrategy().contains("短期"));
            assertTrue(result.getIntradayOperations().contains("预判关键点位"));
            assertTrue(result.getIndustryPolicyOrientation().contains("行业趋势"));

            // 打印结果用于调试
            System.out.println("公司基本面分析: " + result.getCompanyFundamentalAnalysis());
            System.out.println("操作策略: " + result.getOperationStrategy());
            System.out.println("盘面分析: " + result.getIntradayOperations());
            System.out.println("行业趋势和政策导向: " + result.getIndustryPolicyOrientation());

        } catch (Exception e) {
            fail("测试失败: " + e.getMessage());
        }
    }

    @Test
    void testParseAIResponseWithAlternativeFormat() {
        // 测试替代格式
        String aiResponse = """
                【公司基本面分析】
                - 财务表现：营收增长稳定，净利润持续改善
                - 经营现状：资产负债率合理，现金流充裕
                - 估值分析：PE、PB指标处于合理区间

                【行业趋势和政策导向】
                - 行业趋势：行业景气度上升，政策支持力度加大
                - 政策影响：相关政策利好行业发展
                - 对股价影响：政策面和技术面共振，股价有望上涨

                【操作策略】
                - 短期(1-2周)：[买入：50-55元 | 止损：48元 | 仓位：30%] 理由：技术面突破，基本面改善
                - 中期(1-3月)：[目标位：60元 | 止损位：45元 | 仓位：50%] 理由：行业趋势向好
                - 长期(3月-1年)：[建仓区：45-55元 | 目标：70元 | 仓位：70%] 理由：长期价值投资

                【盘面分析】
                - 开盘前：关注重要经济数据发布，预判市场情绪
                - 盘中：关注成交量变化，把握买卖时机
                - 盘后：总结当日表现，制定次日策略
                """;

        try {
            java.lang.reflect.Method method = AIAnalysisService.class.getDeclaredMethod("parseAIResponse", String.class);
            method.setAccessible(true);
            AIAnalysisResult result = (AIAnalysisResult) method.invoke(aiAnalysisService, aiResponse);

            // 验证解析结果
            assertNotNull(result);
            assertTrue(result.getCompanyFundamentalAnalysis().contains("财务表现"));
            assertTrue(result.getOperationStrategy().contains("买入"));
            assertTrue(result.getIntradayOperations().contains("开盘前"));
            assertTrue(result.getIndustryPolicyOrientation().contains("行业趋势"));

        } catch (Exception e) {
            fail("测试失败: " + e.getMessage());
        }
    }
}
