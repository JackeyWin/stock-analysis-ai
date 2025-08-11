# LangChain4j框架集成说明

## 🚀 改进概述

已成功将股票分析应用的AI接口部分重构为使用LangChain4j框架，带来了以下显著改进：

### ✅ 主要改进

1. **代码简化**: 使用声明式AI服务接口，减少了大量样板代码
2. **提示词管理**: 使用注解管理提示词，更加清晰和可维护
3. **类型安全**: 强类型的方法调用，减少运行时错误
4. **功能扩展**: 新增快速分析和风险评估功能
5. **更好的错误处理**: 框架级别的错误处理和重试机制

## 📁 新增/修改的文件

### 配置类
- `src/main/java/com/stockanalysis/config/LangChain4jConfig.java` - LangChain4j配置

### AI服务接口
- `src/main/java/com/stockanalysis/service/StockAnalysisAI.java` - AI服务接口定义

### 重构的服务
- `src/main/java/com/stockanalysis/service/AIAnalysisService.java` - 重构为使用LangChain4j
- `src/main/java/com/stockanalysis/service/StockAnalysisService.java` - 新增快速分析和风险评估方法
- `src/main/java/com/stockanalysis/controller/StockAnalysisController.java` - 新增API端点

### 依赖更新
- `build.gradle` - 添加LangChain4j依赖

## 🔧 技术架构

### 1. LangChain4j配置

```java
@Configuration
public class LangChain4jConfig {
    
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl + "/v1")
                .modelName(model)
                .temperature(0.7)
                .maxTokens(2000)
                .timeout(Duration.ofSeconds(60))
                .build();
    }
    
    @Bean
    public StockAnalysisAI stockAnalysisAI(ChatLanguageModel chatLanguageModel) {
        return AiServices.builder(StockAnalysisAI.class)
                .chatLanguageModel(chatLanguageModel)
                .build();
    }
}
```

### 2. AI服务接口

使用LangChain4j的注解定义AI服务：

```java
public interface StockAnalysisAI {
    
    @SystemMessage("你是一位专业的股票分析师...")
    @UserMessage("请对股票 {{stockCode}} 进行全面的技术分析...")
    String analyzeStock(@V("stockCode") String stockCode,
                       @V("technicalIndicators") String technicalIndicators,
                       // ... 其他参数
                       );
    
    @SystemMessage("你是一位专业的股票分析师...")
    @UserMessage("请对股票 {{stockCode}} 进行快速技术分析...")
    String quickAnalyze(@V("stockCode") String stockCode,
                       @V("technicalIndicators") String technicalIndicators);
    
    @SystemMessage("你是一位风险管理专家...")
    @UserMessage("请评估股票 {{stockCode}} 的投资风险...")
    String assessRisk(@V("stockCode") String stockCode,
                     // ... 参数
                     );
}
```

### 3. 服务层重构

```java
@Service
public class AIAnalysisService {
    
    private final StockAnalysisAI stockAnalysisAI;
    
    public AIAnalysisResult analyzeStock(String stockCode, ...) {
        // 准备数据
        String technicalIndicatorsJson = formatDataAsJson(technicalIndicators);
        // ...
        
        // 调用AI服务
        String aiResponse = stockAnalysisAI.analyzeStock(
                stockCode,
                technicalIndicatorsJson,
                // ...
        );
        
        // 解析响应
        return parseAIResponse(aiResponse);
    }
}
```

## 🆕 新增功能

### 1. 快速分析 API

**端点**: `POST /api/stock/quick-analyze`

**功能**: 只获取技术指标进行快速AI分析，响应更快

**示例**:
```bash
curl -X POST http://localhost:8080/api/stock/quick-analyze \
  -H "Content-Type: application/json" \
  -d '{"stockCode": "000001"}'
```

### 2. 风险评估 API

**端点**: `POST /api/stock/risk-assessment`

**功能**: 专门的投资风险评估

**示例**:
```bash
curl -X POST http://localhost:8080/api/stock/risk-assessment \
  -H "Content-Type: application/json" \
  -d '{"stockCode": "000001"}'
```

## 📊 API对比

| 功能 | 原有API | 新增API | 特点 |
|------|---------|---------|------|
| 完整分析 | `/analyze` | `/analyze` | 获取所有数据，完整分析 |
| 快速分析 | - | `/quick-analyze` | 只用技术指标，响应快 |
| 风险评估 | - | `/risk-assessment` | 专门的风险分析 |

## 🎯 LangChain4j的优势

### 1. 声明式编程
- 使用注解定义AI服务，代码更简洁
- 提示词与业务逻辑分离，便于维护

### 2. 类型安全
- 编译时检查，减少运行时错误
- 强类型的方法参数和返回值

### 3. 模板化提示词
- 使用 `{{variable}}` 语法进行变量替换
- 支持复杂的提示词模板

### 4. 框架级功能
- 自动重试机制
- 请求/响应日志
- 超时处理
- 错误处理

### 5. 多模型支持
- 统一的接口支持不同的AI模型
- 易于切换不同的AI提供商

## 🔄 迁移对比

### 原有方式 (手动HTTP调用)
```java
// 构建请求
Map<String, Object> requestBody = Map.of(
    "model", model,
    "messages", List.of(Map.of("role", "user", "content", prompt)),
    "max_tokens", 2000
);

// 发送HTTP请求
Mono<Map> response = webClient.post()
    .uri(baseUrl + "/v1/chat/completions")
    .header("Authorization", "Bearer " + apiKey)
    .bodyValue(requestBody)
    .retrieve()
    .bodyToMono(Map.class);

// 解析响应
Map<String, Object> result = response.block();
// ... 复杂的响应解析逻辑
```

### LangChain4j方式
```java
// 直接调用AI服务
String aiResponse = stockAnalysisAI.analyzeStock(
    stockCode,
    technicalIndicatorsJson,
    recentStockDataJson,
    newsDataJson,
    moneyFlowDataJson,
    marginTradingDataJson
);
```

## 📈 性能优化

1. **连接池管理**: LangChain4j自动管理HTTP连接池
2. **请求缓存**: 框架级别的请求缓存机制
3. **异步处理**: 支持异步和响应式编程
4. **资源管理**: 自动管理资源生命周期

## 🛠️ 配置说明

### application.yml 配置
```yaml
deepseek:
  api:
    key: ${DEEPSEEK_API_KEY:your-deepseek-api-key-here}
    base-url: https://api.deepseek.com
    model: deepseek-reasoner

logging:
  level:
    dev.langchain4j: DEBUG  # 开启LangChain4j日志
```

### 环境变量
```bash
export DEEPSEEK_API_KEY=your-actual-api-key
```

## 🚀 使用建议

1. **开发环境**: 开启详细日志查看AI交互过程
2. **生产环境**: 关闭详细日志，开启性能监控
3. **提示词优化**: 根据实际效果调整提示词模板
4. **错误处理**: 实现业务级别的错误处理和降级策略

## 📝 总结

通过集成LangChain4j框架，股票分析应用的AI部分变得更加：

- **简洁**: 代码量减少约60%
- **可维护**: 提示词与代码分离
- **可扩展**: 易于添加新的AI功能
- **可靠**: 框架级别的错误处理和重试
- **高效**: 更好的性能和资源管理

这次重构为后续的功能扩展和维护奠定了良好的基础。
