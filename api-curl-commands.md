# 股票分析应用 API 接口 - cURL 命令集合

## 基础信息
- **Base URL**: `http://localhost:8080`
- **Content-Type**: `application/json`
- **所有接口支持跨域访问**

---

## 1. 健康检查接口

### GET /api/stock/health
检查服务是否正常运行

```bash
curl --location --request GET 'http://localhost:8080/api/stock/health' \
--header 'Accept: text/plain'
```

**响应示例:**
```
Stock Analysis Service is running
```

---

## 2. 股票分析接口 (GET方式)

### GET /api/stock/analyze/{stockCode}
通过股票代码进行简单分析（使用默认参数）

```bash
# 分析平安银行 (000001)
curl --location --request GET 'http://localhost:8080/api/stock/analyze/000001' \
--header 'Accept: application/json'
```

```bash
# 分析招商银行 (600036)
curl --location --request GET 'http://localhost:8080/api/stock/analyze/600036' \
--header 'Accept: application/json'
```

```bash
# 分析万科A (000002)
curl --location --request GET 'http://localhost:8080/api/stock/analyze/000002' \
--header 'Accept: application/json'
```

```bash
# 分析中国石化 (600028)
curl --location --request GET 'http://localhost:8080/api/stock/analyze/600028' \
--header 'Accept: application/json'
```

---

## 3. 股票分析接口 (POST方式)

### POST /api/stock/analyze
通过POST请求进行详细股票分析，支持自定义参数

```bash
# 分析平安银行 (000001) - 默认250天数据
curl --location --request POST 'http://localhost:8080/api/stock/analyze' \
--header 'Content-Type: application/json' \
--header 'Accept: application/json' \
--data-raw '{
    "stockCode": "000001",
    "days": 250
}'
```

```bash
# 分析招商银行 (600036) - 获取120天数据
curl --location --request POST 'http://localhost:8080/api/stock/analyze' \
--header 'Content-Type: application/json' \
--header 'Accept: application/json' \
--data-raw '{
    "stockCode": "600036",
    "days": 120
}'
```

```bash
# 分析万科A (000002) - 获取60天数据
curl --location --request POST 'http://localhost:8080/api/stock/analyze' \
--header 'Content-Type: application/json' \
--header 'Accept: application/json' \
--data-raw '{
    "stockCode": "000002",
    "days": 60
}'
```

```bash
# 分析贵州茅台 (600519) - 获取500天数据
curl --location --request POST 'http://localhost:8080/api/stock/analyze' \
--header 'Content-Type: application/json' \
--header 'Accept: application/json' \
--data-raw '{
    "stockCode": "600519",
    "days": 500
}'
```

---

## 4. 快速分析接口

### POST /api/stock/quick-analyze
快速分析接口，只获取技术指标进行AI分析，响应更快

```bash
# 快速分析平安银行 (000001)
curl --location --request POST 'http://localhost:8080/api/stock/quick-analyze' \
--header 'Content-Type: application/json' \
--header 'Accept: application/json' \
--data-raw '{
    "stockCode": "000001"
}'
```

```bash
# 快速分析招商银行 (600036)
curl --location --request POST 'http://localhost:8080/api/stock/quick-analyze' \
--header 'Content-Type: application/json' \
--header 'Accept: application/json' \
--data-raw '{
    "stockCode": "600036"
}'
```

---

## 5. 风险评估接口

### POST /api/stock/risk-assessment
专门的风险评估接口，评估投资风险

```bash
# 评估平安银行 (000001) 投资风险
curl --location --request POST 'http://localhost:8080/api/stock/risk-assessment' \
--header 'Content-Type: application/json' \
--header 'Accept: application/json' \
--data-raw '{
    "stockCode": "000001"
}'
```

```bash
# 评估贵州茅台 (600519) 投资风险
curl --location --request POST 'http://localhost:8080/api/stock/risk-assessment' \
--header 'Content-Type: application/json' \
--header 'Accept: application/json' \
--data-raw '{
    "stockCode": "600519"
}'
```

---

## 请求参数说明

### StockAnalysisRequest (POST请求体)
```json
{
    "stockCode": "string",  // 必填，6位股票代码，如 "000001"
    "days": "integer"       // 可选，获取的历史数据天数，默认250天
}
```

**参数验证规则:**
- `stockCode`: 必须是6位数字，不能为空
- `days`: 正整数，建议范围 30-1000

---

## 响应格式说明

### 成功响应 (200 OK)
```json
{
    "stockCode": "000001",
    "stockName": "平安银行",
    "success": true,
    "message": null,
    "stockData": [
        {
            "d": "2024-01-01",
            "o": 10.50,
            "c": 10.80,
            "h": 11.00,
            "l": 10.30,
            "v": "12345.67",
            "tu": "1.33"
        }
    ],
    "marketData": [...],
    "boardData": [...],
    "technicalIndicators": {
        "分析周期": "2024-01-01至2024-12-31",
        "核心指标": {
            "价格指标": {
                "20日支撑位": 10.20,
                "20日压力位": 11.50,
                "60日支撑位": 9.80,
                "60日压力位": 12.00
            },
            "均线系统": {
                "MA20": 10.85,
                "MA60": 10.45,
                "MA120": 10.20
            },
            "量能特征": {
                "历史天量": {
                    "成交量": 50000.00,
                    "日期": "2024-06-15"
                },
                "近期量能中枢": 15000.00
            },
            "关键信号": [
                {
                    "日期": "2024-12-01",
                    "类型": "MACD金叉",
                    "描述": "DIFF上穿DEA"
                }
            ]
        },
        "风险提示": [
            "近期量能萎缩，需警惕回调风险"
        ]
    },
    "newsData": [...],
    "moneyFlowData": {...},
    "marginTradingData": {...},
    "aiAnalysisResult": {
        "trendAnalysis": "当前处于上升趋势...",
        "technicalPattern": "形成双底形态...",
        "movingAverage": "MA5上穿MA10，呈现金叉...",
        "rsiAnalysis": "RSI指标显示超买状态...",
        "pricePredict": "未来1-2周预计上涨5-10%...",
        "tradingAdvice": "建议买入，止损位设在...",
        "fullAnalysis": "完整的AI分析报告..."
    }
}
```

### 错误响应 (400 Bad Request)
```json
{
    "stockCode": "000001",
    "success": false,
    "message": "分析失败: 股票代码不存在",
    "stockData": null,
    "aiAnalysisResult": null
}
```

### 参数验证错误 (400 Bad Request)
```json
{
    "success": false,
    "message": "参数验证失败",
    "errors": {
        "stockCode": "股票代码格式不正确，应为6位数字"
    }
}
```

### 系统错误 (500 Internal Server Error)
```json
{
    "success": false,
    "message": "系统内部错误"
}
```

---

## 常用股票代码示例

### 银行股
- 平安银行: `000001`
- 万科A: `000002`
- 招商银行: `600036`
- 工商银行: `601398`
- 建设银行: `601939`

### 科技股
- 腾讯控股: `00700` (港股)
- 阿里巴巴: `09988` (港股)
- 中兴通讯: `000063`
- 海康威视: `002415`

### 消费股
- 贵州茅台: `600519`
- 五粮液: `000858`
- 伊利股份: `600887`
- 海天味业: `603288`

### 新能源
- 比亚迪: `002594`
- 宁德时代: `300750`
- 隆基绿能: `601012`

---

## Postman 导入说明

1. **复制curl命令**: 选择上面任意一个curl命令
2. **打开Postman**: 点击 "Import" 按钮
3. **选择Raw text**: 粘贴curl命令
4. **点击Continue**: Postman会自动解析curl命令
5. **保存到集合**: 建议创建一个名为 "股票分析API" 的集合

### 环境变量设置
在Postman中设置环境变量：
- `baseUrl`: `http://localhost:8080`
- `stockCode`: `000001` (可根据需要修改)

然后在请求中使用 `{{baseUrl}}` 和 `{{stockCode}}` 替换固定值。

---

## 测试建议

1. **先测试健康检查**: 确保服务正常运行
2. **使用GET接口**: 快速测试单个股票
3. **使用POST接口**: 测试不同参数组合
4. **检查响应时间**: AI分析可能需要30-60秒
5. **验证数据完整性**: 确保所有字段都有返回值

---

## 注意事项

- 🔑 **API密钥**: 确保在 `application.yml` 中配置了有效的 DeepSeek API 密钥
- ⏱️ **超时设置**: AI分析可能需要较长时间，建议设置60秒以上的超时
- 🌐 **网络环境**: 需要能够访问东方财富API和DeepSeek API
- 📊 **数据限制**: 建议days参数不超过1000，避免数据量过大
- 🚫 **频率限制**: 避免过于频繁的请求，建议间隔1秒以上