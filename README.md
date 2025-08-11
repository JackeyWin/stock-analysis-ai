# 股票分析应用

这是一个基于Spring Boot和LangChain4j开发的智能股票分析应用，集成了DeepSeek AI模型进行专业的股票技术分析。

## 功能特性

- 📈 **K线数据获取**: 自动获取个股、大盘、板块的历史K线数据
- 🔢 **技术指标计算**: 计算RSI、MACD、移动平均线、布林带、KDJ等技术指标
- 📰 **新闻数据**: 获取公司最近的相关新闻
- 💰 **资金流向**: 分析主力资金、散户资金、机构资金的流向
- 📊 **融资融券**: 获取融资融券数据分析
- 🤖 **AI智能分析**: 使用DeepSeek-R1模型进行专业的股票分析

## 技术栈

- **后端**: Spring Boot 3.2.0 + Java 17
- **AI框架**: LangChain4j
- **AI模型**: DeepSeek-R1
- **数据获取**: Python脚本 + 东方财富API
- **构建工具**: Gradle

## 项目结构

```
stock-analysis-app/
├── src/main/java/com/stockanalysis/
│   ├── StockAnalysisApplication.java          # 主启动类
│   ├── controller/
│   │   └── StockAnalysisController.java       # REST API控制器
│   ├── service/
│   │   ├── StockAnalysisService.java          # 主要分析服务
│   │   ├── PythonScriptService.java           # Python脚本调用服务
│   │   └── AIAnalysisService.java             # AI分析服务
│   ├── model/                                 # 数据模型
│   └── config/
│       └── GlobalExceptionHandler.java       # 全局异常处理
├── python_scripts/                           # Python数据获取脚本
│   ├── EastMoneyTickHistoryKline.py          # 个股K线数据
│   ├── EastMoneyMarketHistoryKline.py        # 大盘K线数据
│   ├── EastMoneyBoardHistoryKline.py         # 板块K线数据
│   ├── TechnicalIndicators.py                # 技术指标计算
│   ├── EasyMoneyNewsData.py                  # 新闻数据
│   ├── EastMoneyFundFlow.py                  # 资金流向数据
│   └── EastMoneyRZRQData.py                  # 融资融券数据
└── src/main/resources/
    └── application.yml                        # 配置文件
```

## 快速开始

### 1. 环境要求

- Java 17+
- Python 3.8+
- Gradle 8.5+

### 2. 安装Python依赖

```bash
pip install pandas numpy curl-cffi
```

### 3. 配置DeepSeek API

在 `application.yml` 中配置你的DeepSeek API密钥：

```yaml
deepseek:
  api:
    key: your-deepseek-api-key-here
    base-url: https://api.deepseek.com
    model: deepseek-reasoner
```

或者设置环境变量：

```bash
export DEEPSEEK_API_KEY=your-deepseek-api-key-here
```

### 4. 运行应用

```bash
# 使用Gradle运行
./gradlew bootRun

# 或者构建后运行
./gradlew build
java -jar build/libs/stock-analysis-app-1.0.0.jar
```

应用将在 `http://localhost:8080` 启动。

## API使用

### 分析股票 (POST)

```bash
curl -X POST http://localhost:8080/api/stock/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "stockCode": "000001",
    "days": 250
  }'
```

### 分析股票 (GET)

```bash
curl http://localhost:8080/api/stock/analyze/000001
```

### 响应格式

```json
{
  "stockCode": "000001",
  "success": true,
  "stockData": [...],
  "marketData": [...],
  "boardData": [...],
  "technicalIndicators": {...},
  "newsData": [...],
  "moneyFlowData": {...},
  "marginTradingData": {...},
  "aiAnalysisResult": {
    "trendAnalysis": "当前处于上升趋势...",
    "technicalPattern": "形成双底形态...",
    "movingAverage": "MA5上穿MA10，呈现金叉...",
    "rsiAnalysis": "RSI指标显示超买状态...",
    "pricePredict": "未来1-2周预计上涨5-10%...",
    "tradingAdvice": "建议买入，止损位设在..."
  }
}
```

## AI分析内容

AI模型会基于以下数据进行分析：

1. **趋势分析**: 评估当前的技术趋势（上升、下降或盘整）
2. **技术形态**: 识别关键技术形态（头肩顶、双底、三角形等）
3. **移动平均线**: 分析均线排列情况（金叉、死叉、多头排列等）
4. **RSI指标**: 评估超买(>70)或超卖(<30)状态
5. **价格预测**: 给出未来1-2周的价格走势预测
6. **交易建议**: 提供具体的交易建议（买入、卖出、持有）及理由

## 技术指标分析格式

应用会输出详细的技术指标分析，包括：

```json
{
  "分析周期": "2024-09-20至2025-08-04",
  "核心指标": {
    "价格指标": {
      "20日支撑位": 3340.69,
      "20日压力位": 3497.48,
      "60日支撑位": 3267.66,
      "60日压力位": 3581.86
    },
    "均线系统": {
      "MA20": 3412.84,
      "MA60": 3346.39,
      "MA120": 3286.65
    },
    "量能特征": {
      "历史天量": {
        "成交量": 131346.02,
        "日期": "2024-10-08"
      },
      "近期量能中枢": 56863.12
    },
    "关键信号": [
      {
        "日期": "2024-09-24",
        "类型": "量价异动",
        "描述": "成交量47761.95，涨幅5.41%"
      }
    ]
  },
  "风险提示": [
    "近期量能萎缩，需警惕回调风险",
    "RSI接近超买区域，短期可能有调整"
  ]
}
```

## 注意事项

1. 确保Python环境正确配置，所有依赖包已安装
2. DeepSeek API密钥需要有效且有足够的调用额度
3. 网络环境需要能够访问东方财富API和DeepSeek API
4. 股票代码格式为6位数字（如：000001、600036）
5. 本应用仅供学习和研究使用，投资有风险，决策需谨慎

## 开发和调试

### 查看日志

应用使用SLF4J进行日志记录，可以通过修改 `application.yml` 中的日志级别来调整：

```yaml
logging:
  level:
    com.stockanalysis: DEBUG
    dev.langchain4j: DEBUG
```

### 测试Python脚本

可以单独测试Python脚本：

```bash
cd python_scripts
python EastMoneyTickHistoryKline.py 000001
python TechnicalIndicators.py '[{"d":"2024-01-01","o":10.0,"c":10.5,"h":11.0,"l":9.8,"v":"1000","tu":"1.05"}]'
```

## 许可证

本项目仅供学习和研究使用。