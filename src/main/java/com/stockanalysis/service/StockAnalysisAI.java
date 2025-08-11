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
            {{technicalIndicators}}
            
            ## 该板块前5日指标数据：
            {{boardTechnicalIndicatorsJson}}
            
            ## 大盘前5日指标数据：
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
            
            - 盘中操作: [基于今日分时数据分析，给出具体的盘中操作建议，不超过200字]
            
            重要要求：
            1. 每个部分必须以"- 标题:"开头
            2. 每个部分之间用空行分隔
            3. 每个部分内容要简洁明了，不要混合其他部分的内容
            4. 不要在一个部分中提及其他部分的标题
            5. 盘中操作部分要重点结合今日分时数据的关键转折点、资金攻击情况和操作建议
            """)
        String analyzeStock(@V("stockCode") String stockCode,
                       @V("technicalIndicators") String technicalIndicators,
                        @V("boardTechnicalIndicatorsJson") String boardTechnicalIndicatorsJson,
                        @V("marketTechnicalIndicatorsJson") String marketTechnicalIndicatorsJson,
                        @V("recentStockData") String recentStockData,
                        @V("newsData") String newsData,
                        @V("moneyFlowData") String moneyFlowData,
                        @V("marginTradingData") String marginTradingData,
                        @V("intradayAnalysis") String intradayAnalysis);

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
