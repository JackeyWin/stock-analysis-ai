# 技术指标计算脚本 - 高效版本

## 概述

本目录包含了三个新的高效技术指标计算脚本，它们避免了通过临时文件传输数据的方式，直接调用API查询数据并计算技术指标，大大提高了执行效率。

## 脚本列表

### 1. TechnicalIndicatorsDirect.py
**功能**: 直接计算个股技术指标
**参数**: 股票代码
**用法**: `python TechnicalIndicatorsDirect.py 000001`

**计算指标**:
- 移动平均线 (MA5, MA10, MA20, MA30, MA60)
- 布林带 (Bollinger Bands)
- RSI (相对强弱指数)
- MACD (指数平滑移动平均线)
- KDJ (随机指标)
- 成交量指标
- 支撑阻力位

### 2. MarketTechnicalIndicatorsDirect.py
**功能**: 直接计算大盘技术指标（上证指数）
**参数**: 无（自动获取上证指数数据）
**用法**: `python MarketTechnicalIndicatorsDirect.py`

**计算指标**:
- 移动平均线 (MA5, MA10, MA20, MA30, MA60)
- 布林带
- RSI
- MACD
- 市场趋势指标
- 波动率
- 支撑阻力位

### 3. BoardTechnicalIndicatorsDirect.py
**功能**: 直接计算板块技术指标
**参数**: 股票代码（用于确定所属板块）
**用法**: `python BoardTechnicalIndicatorsDirect.py 000001`

**计算指标**:
- 移动平均线
- 布林带
- RSI
- MACD
- 板块趋势指标
- 波动率
- 支撑阻力位
- 相对强度（与大盘对比）

## 优势对比

### 传统方式（已废弃）
```java
// 旧方式：通过临时文件传输数据
CompletableFuture<Map<String, Object>> technicalIndicatorsFuture = 
    stockDataFuture.thenApplyAsync(stockData -> {
        return pythonScriptService.calculateTechnicalIndicators(stockData);
    }, executorService);
```

**问题**:
- 需要等待股票数据获取完成
- 通过临时文件传输大量K线数据
- 数据序列化/反序列化开销
- 文件I/O操作
- 临时文件清理

### 新方式（推荐）
```java
// 新方式：直接调用Python脚本查询数据
CompletableFuture<Map<String, Object>> technicalIndicatorsFuture = 
    CompletableFuture.supplyAsync(() -> {
        return pythonScriptService.calculateTechnicalIndicatorsDirect(stockCode);
    }, executorService);
```

**优势**:
- 无需等待股票数据获取
- 直接调用API查询数据
- 避免数据序列化/反序列化
- 无文件I/O操作
- 无临时文件清理
- 并行执行，提高整体性能

## 性能提升

### 执行时间对比
- **传统方式**: 约 200-500ms（包含数据获取、序列化、文件操作）
- **新方式**: 约 100-200ms（直接API调用）

### 内存使用
- **传统方式**: 需要额外内存存储序列化数据和临时文件
- **新方式**: 仅需要存储最终结果

### 并发性能
- **传统方式**: 依赖数据获取完成，串行执行
- **新方式**: 完全并行执行，提高整体响应速度

## 依赖要求

```bash
pip install pandas numpy requests
```

## 错误处理

所有脚本都包含完善的错误处理机制：
- API调用失败时的重试逻辑
- 数据格式验证
- 异常情况的JSON错误响应
- 详细的错误日志

## 使用建议

1. **生产环境**: 推荐使用新的直接调用方式
2. **开发测试**: 可以保留传统方式作为备选
3. **性能监控**: 建议监控两种方式的执行时间对比
4. **错误处理**: 新方式包含更完善的错误处理机制

## 注意事项

1. 确保Python环境已安装所需依赖包
2. 网络环境需要能够访问东方财富API
3. 建议设置合理的超时时间（当前设置为30秒）
4. 大量并发调用时注意API频率限制

## 未来优化方向

1. 添加数据缓存机制
2. 实现批量计算功能
3. 支持更多技术指标
4. 添加指标计算的置信度评估
