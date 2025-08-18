package com.stockanalysis.tools;

import com.stockanalysis.service.PythonScriptService;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 股票数据工具 - 封装Python脚本为AI可调用的工具
 */
@Slf4j
@Component
public class StockDataTool {

    private final PythonScriptService pythonScriptService;

    public StockDataTool(PythonScriptService pythonScriptService) {
        this.pythonScriptService = pythonScriptService;
    }

    /**
     * 获取股票K线数据
     */
    @Tool("获取指定股票的K线数据，包括开盘价、收盘价、最高价、最低价、成交量等")
    public String getStockKlineData(String stockCode) {
        try {
            log.info("AI工具调用: 获取股票{}的K线数据", stockCode);
            List<Map<String, Object>> data = pythonScriptService.getStockKlineData(stockCode);
            
            if (data == null || data.isEmpty()) {
                return "未获取到股票" + stockCode + "的K线数据";
            }
            
            // 返回最近10天的数据摘要
            StringBuilder result = new StringBuilder();
            result.append("股票").append(stockCode).append("最近K线数据:\n");
            
            int startIndex = Math.max(0, data.size() - 10);
            for (int i = startIndex; i < data.size(); i++) {
                Map<String, Object> dayData = data.get(i);
                result.append(String.format("日期:%s, 开盘:%.2f, 收盘:%.2f, 最高:%.2f, 最低:%.2f, 成交量:%s\n",
                    dayData.get("d"), dayData.get("o"), dayData.get("c"), 
                    dayData.get("h"), dayData.get("l"), dayData.get("v")));
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("获取股票{}K线数据失败: {}", stockCode, e.getMessage());
            return "获取股票" + stockCode + "K线数据失败: " + e.getMessage();
        }
    }

    /**
     * 计算技术指标
     */
    @Tool("计算指定股票的技术指标，包括MA、RSI、MACD、KDJ、布林带等")
    public String calculateTechnicalIndicators(String stockCode) {
        try {
            log.info("AI工具调用: 计算股票{}的技术指标", stockCode);
            List<Map<String, Object>> stockData = pythonScriptService.getStockKlineData(stockCode);
            Map<String, Object> indicators = pythonScriptService.calculateTechnicalIndicators(stockData);
            
            if (indicators == null || indicators.isEmpty()) {
                return "未能计算股票" + stockCode + "的技术指标";
            }
            
            StringBuilder result = new StringBuilder();
            result.append("股票").append(stockCode).append("技术指标:\n");
            
            // 提取核心指标
            if (indicators.containsKey("核心指标")) {
                Map<String, Object> coreIndicators = (Map<String, Object>) indicators.get("核心指标");
                result.append("核心指标: ").append(coreIndicators.toString()).append("\n");
            }
            
            // 提取最近指标
            if (indicators.containsKey("近5日指标")) {
                List<Map<String, Object>> recentIndicators = (List<Map<String, Object>>) indicators.get("近5日指标");
                if (!recentIndicators.isEmpty()) {
                    Map<String, Object> latest = recentIndicators.get(recentIndicators.size() - 1);
                    result.append("最新指标: MA5=").append(latest.get("ma5"))
                          .append(", MA20=").append(latest.get("ma20"))
                          .append(", RSI=").append(latest.get("rsi"))
                          .append(", MACD=").append(latest.get("macd")).append("\n");
                }
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("计算股票{}技术指标失败: {}", stockCode, e.getMessage());
            return "计算股票" + stockCode + "技术指标失败: " + e.getMessage();
        }
    }

    /**
     * 获取股票新闻数据
     */
    @Tool("获取指定股票的相关新闻、公告和研报信息，包括情感分析")
    public String getStockNews(String stockCode) {
        try {
            log.info("AI工具调用: 获取股票{}的新闻数据", stockCode);
            List<Map<String, Object>> newsData = pythonScriptService.getNewsData(stockCode);
            
            if (newsData == null || newsData.isEmpty()) {
                return "未获取到股票" + stockCode + "的新闻数据";
            }
            
            StringBuilder result = new StringBuilder();
            result.append("股票").append(stockCode).append("相关新闻:\n");
            
            // 只显示前5条新闻
            for (int i = 0; i < Math.min(5, newsData.size()); i++) {
                Map<String, Object> news = newsData.get(i);
                result.append("标题: ").append(news.get("新闻标题")).append("\n");
                result.append("情感: ").append(news.get("情感标签"));
                result.append("(评分: ").append(news.get("情感评分")).append(")\n");
                result.append("摘要: ").append(news.get("分析摘要")).append("\n\n");
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("获取股票{}新闻数据失败: {}", stockCode, e.getMessage());
            return "获取股票" + stockCode + "新闻数据失败: " + e.getMessage();
        }
    }

    /**
     * 获取资金流向数据
     */
    @Tool("获取指定股票的资金流向数据，包括主力资金、散户资金的流入流出情况")
    public String getMoneyFlowData(String stockCode) {
        try {
            log.info("AI工具调用: 获取股票{}的资金流向数据", stockCode);
            List<Map<String, Object>> moneyFlowData = pythonScriptService.getMoneyFlowData(stockCode);
            
            if (moneyFlowData == null || moneyFlowData.isEmpty()) {
                return "未获取到股票" + stockCode + "的资金流向数据";
            }
            
            StringBuilder result = new StringBuilder();
            result.append("股票").append(stockCode).append("资金流向:\n");
            
            // 提取资金数据
            Map<String, Object> stockData = moneyFlowData.get(0);
            if (stockData.containsKey("资金数据")) {
                Map<String, Object> fundData = (Map<String, Object>) stockData.get("资金数据");
                
                // 今日资金流向
                if (fundData.containsKey("今日")) {
                    List<Map<String, Object>> todayData = (List<Map<String, Object>>) fundData.get("今日");
                    result.append("今日资金流向:\n");
                    for (Map<String, Object> item : todayData) {
                        result.append(item.get("资金类型")).append(": ")
                              .append("净流入 ").append(item.get("净流入额"))
                              .append(", 占比 ").append(item.get("净占比")).append("%\n");
                    }
                }
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("获取股票{}资金流向数据失败: {}", stockCode, e.getMessage());
            return "获取股票" + stockCode + "资金流向数据失败: " + e.getMessage();
        }
    }

    /**
     * 获取融资融券数据
     */
    @Tool("获取指定股票的融资融券数据，包括融资余额、融券余额等")
    public String getMarginTradingData(String stockCode) {
        try {
            log.info("AI工具调用: 获取股票{}的融资融券数据", stockCode);
            List<Map<String, Object>> marginData = pythonScriptService.getMarginTradingData(stockCode);
            
            if (marginData == null || marginData.isEmpty()) {
                return "未获取到股票" + stockCode + "的融资融券数据";
            }
            
            StringBuilder result = new StringBuilder();
            result.append("股票").append(stockCode).append("融资融券数据:\n");
            
            // 显示最近3天的数据
            for (int i = Math.max(0, marginData.size() - 3); i < marginData.size(); i++) {
                Map<String, Object> dayData = marginData.get(i);
                result.append("日期: ").append(dayData.get("日期")).append("\n");
                result.append("融资余额: ").append(dayData.get("融资余额"));
                result.append(", 融券余额: ").append(dayData.get("融券余额"));
                result.append(", 融资净买入: ").append(dayData.get("融资净买入额")).append("\n");
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("获取股票{}融资融券数据失败: {}", stockCode, e.getMessage());
            return "获取股票" + stockCode + "融资融券数据失败: " + e.getMessage();
        }
    }
    
    /**
     * 获取行业成分股
     */
    @Tool("获取指定行业的成分股列表")
    public String getSectorStocks(String sectorName) {
        try {
            log.info("AI工具调用: 获取行业{}的成分股", sectorName);
            List<Map<String, Object>> stocks = pythonScriptService.getSectorStocks(sectorName);
            
            if (stocks == null || stocks.isEmpty()) {
                return "未获取到行业" + sectorName + "的成分股数据";
            }
            
            StringBuilder result = new StringBuilder();
            result.append("行业").append(sectorName).append("成分股列表:\n");
            
            // 显示前20只股票
            for (int i = 0; i < Math.min(20, stocks.size()); i++) {
                Map<String, Object> stock = stocks.get(i);
                result.append(stock.get("code")).append(" ").append(stock.get("name")).append("\n");
            }
            
            if (stocks.size() > 20) {
                result.append("... 还有").append(stocks.size() - 20).append("只股票\n");
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("获取行业{}成分股失败: {}", sectorName, e.getMessage());
            return "获取行业" + sectorName + "成分股失败: " + e.getMessage();
        }
    }

    /**
     * 获取分时分析数据
     */
    @Tool("获取指定股票的分时分析数据，包括当日走势和基本信息")
    public String getIntradayAnalysis(String stockCode) {
        try {
            log.info("AI工具调用: 获取股票{}的分时分析数据", stockCode);
            Map<String, Object> intradayData = pythonScriptService.getIntradayAnalysis(stockCode);
            
            if (intradayData == null || intradayData.isEmpty()) {
                return "未获取到股票" + stockCode + "的分时分析数据";
            }
            
            StringBuilder result = new StringBuilder();
            result.append("股票").append(stockCode).append("分时分析:\n");
            
            // 股票基本信息
            if (intradayData.containsKey("stockBasic")) {
                Map<String, Object> stockBasic = (Map<String, Object>) intradayData.get("stockBasic");
                result.append("股票名称: ").append(stockBasic.get("stockName")).append("\n");
                result.append("当前价格: ").append(stockBasic.get("currentPrice"));
                result.append(", 涨跌幅: ").append(stockBasic.get("changePercent")).append("%\n");
                result.append("成交量: ").append(stockBasic.get("volume"));
                result.append(", 成交额: ").append(stockBasic.get("turnover")).append("\n");
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("获取股票{}分时分析数据失败: {}", stockCode, e.getMessage());
            return "获取股票" + stockCode + "分时分析数据失败: " + e.getMessage();
        }
    }

    /**
     * 获取同业对比数据
     */
    @Tool("获取指定股票的同业对比数据，包括行业排名和竞争对手分析")
    public String getPeerComparison(String stockCode) {
        try {
            log.info("AI工具调用: 获取股票{}的同业对比数据", stockCode);
            Map<String, Object> peerData = pythonScriptService.getPeerComparisonData(stockCode);
            
            if (peerData == null || peerData.isEmpty()) {
                return "未获取到股票" + stockCode + "的同业对比数据";
            }
            
            StringBuilder result = new StringBuilder();
            result.append("股票").append(stockCode).append("同业对比:\n");
            result.append(peerData.toString());
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("获取股票{}同业对比数据失败: {}", stockCode, e.getMessage());
            return "获取股票" + stockCode + "同业对比数据失败: " + e.getMessage();
        }
    }

    /**
     * 获取财务分析数据
     */
    @Tool("获取指定股票的财务分析数据，包括财务指标和基本面分析")
    public String getFinancialAnalysis(String stockCode) {
        try {
            log.info("AI工具调用: 获取股票{}的财务分析数据", stockCode);
            Map<String, Object> financialData = pythonScriptService.getFinancialAnalysisData(stockCode);
            
            if (financialData == null || financialData.isEmpty()) {
                return "未获取到股票" + stockCode + "的财务分析数据";
            }
            
            StringBuilder result = new StringBuilder();
            result.append("股票").append(stockCode).append("财务分析:\n");
            result.append(financialData.toString());
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("获取股票{}财务分析数据失败: {}", stockCode, e.getMessage());
            return "获取股票" + stockCode + "财务分析数据失败: " + e.getMessage();
        }
    }

    /**
     * 获取核心概念标签
     */
    @Tool("获取指定股票的核心概念标签和行业分类信息")
    public String getCoreTagsData(String stockCode) {
        try {
            log.info("AI工具调用: 获取股票{}的核心概念标签", stockCode);
            Map<String, Object> coreTagsData = pythonScriptService.getCoreTagsData(stockCode);
            
            if (coreTagsData == null || coreTagsData.isEmpty()) {
                return "未获取到股票" + stockCode + "的核心概念标签";
            }
            
            StringBuilder result = new StringBuilder();
            result.append("股票").append(stockCode).append("核心概念:\n");
            
            if (coreTagsData.containsKey("concepts")) {
                result.append("概念标签: ").append(coreTagsData.get("concepts")).append("\n");
            }
            if (coreTagsData.containsKey("industries")) {
                result.append("行业分类: ").append(coreTagsData.get("industries")).append("\n");
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("获取股票{}核心概念标签失败: {}", stockCode, e.getMessage());
            return "获取股票" + stockCode + "核心概念标签失败: " + e.getMessage();
        }
    }


    /**
     * 获取所有行业列表
     */
    @Tool("获取所有行业列表")
    public List<String> getAllSectors() {
        try {
            log.info("开始获取行业列表");
            // 调用Python脚本获取行业列表
            String result = pythonScriptService.executePythonScript("EastMoneySectorStocks.py", "--list");
            
            // 简单解析结果，按行分割
            if (result != null && !result.isEmpty()) {
                log.info("获取行业列表成功");
                return Arrays.asList(result.split("\n"));
            }
            
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("获取行业列表失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
}
