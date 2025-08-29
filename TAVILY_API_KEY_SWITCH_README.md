# Tavily API Key 自动切换功能

## 功能概述

本功能实现了Tavily API key的自动切换机制，当API调用遇到432错误（API key限制）时，系统会自动切换到下一个可用的API key，提高系统的稳定性和可用性。

## 主要特性

- 🔑 **多Key支持**: 支持配置多个Tavily API key
- 🔄 **自动切换**: 遇到432错误时自动切换到下一个key
- 📊 **智能轮询**: 支持key的轮询使用
- 🛡️ **错误处理**: 完善的错误处理和重试机制
- 📝 **详细日志**: 记录key切换过程和状态

## 配置说明

### 1. 配置文件修改

在 `src/main/resources/application.yml` 中添加多key配置：

```yaml
tavily:
  api:
    # 支持多个API key，用逗号分隔，系统会自动切换
    keys: tvly-dev-key1,tvly-dev-key2,tvly-dev-key3
    # 单个key配置（已废弃，保留兼容性）
    key: tvly-dev-key1
```

### 2. 配置优先级

- 优先使用 `tavily.api.keys` 配置（多key模式）
- 如果没有配置多key，则使用 `tavily.api.key` 配置（单key模式）
- 如果都没有配置，系统会显示警告并禁用Tavily功能

## 技术实现

### 1. 核心组件

#### TavilyApiKeyManager
- 管理多个API key的配置和状态
- 提供key的获取、切换、重置等功能
- 支持key的轮询使用

#### 工具类改造
- `MarketResearchTools`: 市场研究工具
- `StockPoolTools`: 股票池工具
- 所有工具类都支持自动key切换

### 2. 工作流程

```
API调用 → 检查响应状态码
    ↓
432错误？ → 是 → 切换下一个key → 重试
    ↓ 否
正常响应 → 返回结果
```

### 3. 重试机制

- 最大重试次数 = 配置的key数量
- 每次重试前自动切换到下一个key
- 所有key都尝试后返回错误信息

## 使用方法

### 1. 启动应用

启动Spring Boot应用后，观察启动日志：

```
🔑 配置了 3 个Tavily API keys
🔧 Tavily API Key配置状态: 已配置
🔧 可用Tavily API Key数量: 3
```

### 2. 正常使用

系统会自动使用第一个可用的key进行API调用，无需额外配置。

### 3. 自动切换

当遇到432错误时，系统会自动切换key并重试，日志会显示：

```
⚠️ Tavily API返回432错误（API key限制），尝试切换key
🔄 切换到下一个Tavily API key: tvly-dev-...key1 -> tvly-dev-...key2
```

## 监控和调试

### 1. 日志监控

关键日志信息：

- `🔑 配置了 X 个Tavily API keys`: 显示配置的key数量
- `🔄 切换到下一个Tavily API key`: 显示key切换过程
- `⚠️ Tavily API返回432错误`: 显示遇到key限制错误
- `🚀 发送HTTP请求到Tavily API (尝试 X/Y)`: 显示重试次数

### 2. 状态检查

可以通过日志查看当前使用的key状态和切换历史。

## 测试验证

### 1. 功能测试

使用提供的测试脚本 `test_tavily_key_switch.py` 进行功能验证：

```bash
python test_tavily_key_switch.py
```

### 2. 测试要点

- 应用启动时的key配置状态
- 正常API调用的响应
- 432错误时的key自动切换
- 所有key都尝试后的错误处理

## 注意事项

### 1. 配置要求

- 确保所有配置的API key都是有效的
- 建议配置至少2个key以提高可用性
- key之间用逗号分隔，不要有空格

### 2. 性能考虑

- key切换会增加API调用的延迟
- 建议合理配置key数量，避免过多无效重试
- 监控key的使用频率和错误率

### 3. 错误处理

- 系统会记录所有key切换和错误信息
- 当所有key都不可用时，会返回明确的错误信息
- 建议定期检查日志，及时发现key相关问题

## 故障排除

### 1. 常见问题

#### 问题：应用启动时显示"未配置Tavily API key"
**解决方案**: 检查 `application.yml` 中的配置，确保 `tavily.api.keys` 或 `tavily.api.key` 已正确配置

#### 问题：API调用总是失败，没有key切换
**解决方案**: 检查日志中的错误信息，确认是否真的是432错误，其他错误不会触发key切换

#### 问题：所有key都尝试后仍然失败
**解决方案**: 检查所有配置的key是否有效，可能需要更新或重新生成API key

### 2. 调试步骤

1. 检查应用启动日志中的key配置状态
2. 观察API调用过程中的key切换日志
3. 确认Tavily API的响应状态码
4. 验证所有配置的key的有效性

## 更新日志

### v1.0.0 (2025-08-28)
- 实现多key配置支持
- 添加自动key切换功能
- 集成到所有Tavily工具类
- 添加详细的日志记录
- 提供测试脚本和文档

## 技术支持

如果在使用过程中遇到问题，请：

1. 查看应用日志中的错误信息
2. 检查配置文件中的key配置
3. 验证Tavily API key的有效性
4. 参考本文档的故障排除部分

---

**注意**: 本功能需要有效的Tavily API key才能正常工作。请确保在配置中提供了正确的API key。
