package com.stockanalysis.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 股票分析AI服务接口
 * 使用LangChain4j的AI Services功能
 */
public interface StockAnalysisAI {

    @SystemMessage("""
            你是一位专业的股票分析师，拥有丰富的技术分析经验。
            你需要基于提供的股票数据进行全面的技术分析，包括：
            1. 技术趋势分析
            2. 技术形态识别
            3. 移动平均线分析
            4. RSI指标分析
            5. 价格走势预测
            6. 交易建议
            
            请始终保持专业、客观的分析态度，基于数据给出合理的分析结论。
            """)
    @UserMessage("""
            请对股票 {{stockCode}} 进行全面的技术分析。
            
            ## 该股票的技术指标数据：
            {{technicalIndicatorsJson}}
            
            ## 该板块的技术指标数据：
            {{boardTechnicalIndicatorsJson}}
            
            ## 大盘的技术指标数据：
            {{marketTechnicalIndicatorsJson}}
            
            ## 最近股价数据：
            {{recentStockData}}
            
            ## 新闻数据：
            {{newsData}}
            
            ## 资金流向数据：
            {{moneyFlowData}}
            
            ## 融资融券数据：
            {{marginTradingData}}
            
            ## 今日分时数据分析：
            {{intradayAnalysis}}
            
            ## 当前时间：
            {{currentTime}}
            
            ## 分析要求：
            请基于以上数据进行专业的技术分析，并严格按照以下格式组织你的回答：
            
            1. 评估当前的技术趋势（上升趋势、下降趋势或盘整）
            2. 识别关键的技术形态（如头肩顶、双底、三角形等）
            3. 分析移动平均线的排列情况（金叉、死叉、多头排列、空头排列等）
            4. 评估RSI指标是否显示超买(>70)或超卖(<30)状态
            5. 基于以上分析，给出未来1-2周的价格走势预测
            6. 提供具体的交易建议（买入、卖出、持有）及理由
            
            请严格按照以下格式组织你的回答，每个部分必须独立且简洁：
            
            - 趋势分析: [简洁的趋势分析，不超过200字]
            
            - 技术形态: [简洁的形态分析，不超过200字]
            
            - 移动平均线: [简洁的均线分析，不超过200字]
            
            - RSI指标: [简洁的RSI分析，不超过200字]
            
            - 价格预测: [简洁的价格预测，不超过200字]
            
            - 交易建议: [简洁的交易建议，不超过200字]
            
            - 盘面分析: [根据当前时间和分时数据指标智能分析：开盘前(9:00前)结合技术指标和资金结构预测今日盘面走势和操作建议；盘中(9:00-15:00)根据实时价格位置、资金流向、成交量变化和资金攻击情况给出操作建议；盘后(15:00后)复盘总结今日走势特征并预测明日操作建议，不超过200字]
            
            重要要求：
            1. 每个部分必须以"- 标题:"开头
            2. 每个部分之间用空行分隔
            3. 每个部分内容要简洁明了，不要混合其他部分的内容
            4. 不要在一个部分中提及其他部分的标题
            5. 盘面分析部分要根据当前时间（开盘前/盘中/盘后）给出相应的分析内容：
               - 开盘前：重点关注技术指标趋势、资金结构特征，预测今日可能的走势
               - 盘中：重点关注价格位置（日内高低点位置）、资金流向强度、成交量变化、多空攻击情况
               - 盘后：总结今日走势特征，分析关键转折点，预测明日操作策略
            """)
        String analyzeStock(@V("stockCode") String stockCode,
                       @V("technicalIndicatorsJson") String technicalIndicatorsJson,
                        @V("boardTechnicalIndicatorsJson") String boardTechnicalIndicatorsJson,
                        @V("marketTechnicalIndicatorsJson") String marketTechnicalIndicatorsJson,
                        @V("recentStockData") String recentStockData,
                        @V("newsData") String newsData,
                        @V("moneyFlowData") String moneyFlowData,
                        @V("marginTradingData") String marginTradingData,
                        @V("intradayAnalysis") String intradayAnalysis,
                        @V("currentTime") String currentTime);

    @SystemMessage("""
            你是一位专业的股票分析师。请根据提供的技术指标数据进行快速分析。
            """)
    @UserMessage("""
            请对股票 {{stockCode}} 进行快速技术分析。
            
            技术指标数据：
            {{technicalIndicators}}
            
            请简要分析当前趋势和给出交易建议。
            """)
    String quickAnalyze(@V("stockCode") String stockCode,
                       @V("technicalIndicators") String technicalIndicators);

    @SystemMessage("""
            你是一位风险管理专家。请基于股票数据评估投资风险。
            """)
    @UserMessage("""
            请评估股票 {{stockCode}} 的投资风险。
            
            技术指标：{{technicalIndicators}}
            资金流向：{{moneyFlowData}}
            融资融券：{{marginTradingData}}
            
            请从以下角度分析风险：
            1. 技术风险
            2. 资金风险
            3. 市场风险
            4. 风险等级评估（低/中/高）
            """)
    String assessRisk(@V("stockCode") String stockCode,
                     @V("technicalIndicators") String technicalIndicators,
                     @V("moneyFlowData") String moneyFlowData,
                     @V("marginTradingData") String marginTradingData);
}
