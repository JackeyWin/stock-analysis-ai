# 盯盘任务关闭清理功能

## 功能概述

本功能确保在Spring Boot应用关闭时，所有正在运行的盯盘任务能够被自动清理，并将状态保存到数据库中，避免任务状态丢失和数据不一致的问题。

## 主要特性

### 1. 自动清理机制
- **应用关闭时自动清理**：实现`DisposableBean`接口，在应用关闭时自动执行清理逻辑
- **数据库状态更新**：将所有运行中的盯盘任务状态更新为`stopped`
- **内存任务清理**：同步清理内存中的任务对象
- **线程池优雅关闭**：等待正在执行的任务完成，最多等待30秒

### 2. 手动清理功能
- **API端点**：`POST /api/stocks/analysis/monitor/cleanup-all`
- **前端按钮**：在分析结果页面提供"清理所有盯盘任务"按钮
- **确认机制**：操作前会弹出确认对话框，防止误操作

### 3. 状态持久化
- **实时状态同步**：内存状态和数据库状态保持同步
- **关闭原因记录**：记录任务停止的原因（应用关闭/手动清理/非交易时段）
- **时间戳记录**：记录任务停止的时间

## 技术实现

### 后端实现

#### 1. 实体类
```java
@Entity
@Table(name = "stock_monitoring_job")
public class StockMonitoringJobEntity {
    private String jobId;           // 任务ID
    private String stockCode;       // 股票代码
    private Integer intervalMinutes; // 监控间隔
    private String status;          // 任务状态 (running/stopped)
    private String lastMessage;     // 最后消息
    private LocalDateTime lastRunTime; // 最后运行时间
    // ... 其他字段
}
```

#### 2. 服务层
```java
@Service
public class StockAnalysisService implements DisposableBean {
    
    @Override
    public void destroy() throws Exception {
        // 应用关闭时的清理逻辑
        cleanupAllMonitoringJobsOnShutdown();
    }
    
    public void cleanupAllMonitoringJobs() {
        // 手动清理所有盯盘任务
    }
}
```

#### 3. 控制器
```java
@PostMapping("/analysis/monitor/cleanup-all")
public ResponseEntity<Map<String, Object>> cleanupAllMonitoringJobs() {
    // 手动清理所有盯盘任务
}
```

### 前端实现

#### 1. API服务
```javascript
async cleanupAllMonitoringJobs() {
    const response = await this.client.post('/api/mobile/monitor/cleanup-all');
    return response.data;
}
```

#### 2. 清理按钮
```javascript
<Button
    mode="outlined"
    onPress={handleCleanupAllMonitoring}
    icon="delete-sweep"
    style={{ borderColor: '#f44336', borderWidth: 2 }}
    textColor="#f44336"
>
    清理所有盯盘任务
</Button>
```

## 配置说明

### 1. 应用配置
```yaml
server:
  shutdown: graceful        # 启用优雅关闭
  grace-period: 30s         # 优雅关闭等待时间
```

### 2. 线程池配置
```java
@Bean("taskExecutor")
public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setWaitForTasksToCompleteOnShutdown(true);  // 等待任务完成
    executor.setAwaitTerminationSeconds(30);            // 等待30秒
    // ... 其他配置
}
```

## 使用场景

### 1. 应用正常关闭
- 用户按Ctrl+C关闭应用
- 系统发送SIGTERM信号
- Spring Boot触发优雅关闭流程
- 自动清理所有盯盘任务

### 2. 应用异常关闭
- 系统崩溃或强制终止
- 数据库中的任务状态保持为`running`
- 下次启动时可以通过手动清理功能清理

### 3. 手动清理
- 管理员需要停止所有盯盘任务
- 通过API或前端按钮执行清理
- 立即停止所有任务并更新状态

## 测试方法

### 1. 自动清理测试
```bash
# 1. 启动应用
./gradlew bootRun

# 2. 启动一个盯盘任务（通过API或前端）

# 3. 关闭应用（Ctrl+C）
# 4. 查看日志中的清理信息
# 5. 检查数据库中任务状态是否为'stopped'
```

### 2. 手动清理测试
```bash
# 使用测试脚本
python test_shutdown_cleanup.py

# 或直接调用API
curl -X POST http://localhost:8080/api/stocks/analysis/monitor/cleanup-all
```

### 3. 前端测试
1. 在分析结果页面启动盯盘任务
2. 点击"清理所有盯盘任务"按钮
3. 确认操作
4. 验证任务状态变化

## 监控和日志

### 1. 关键日志
```
INFO  - 应用正在关闭，开始清理所有运行中的盯盘任务...
INFO  - 发现 X 个运行中的盯盘任务，开始清理...
INFO  - 已清理盯盘任务: 000001 - monitor_000001_1234567890 (5)
INFO  - 正在关闭线程池...
INFO  - 盯盘任务清理完成，共清理 X 个任务
```

### 2. 错误处理
- 清理失败的任务会记录错误日志
- 线程池关闭超时会记录警告日志
- 所有异常都会被捕获并记录

## 注意事项

### 1. 性能考虑
- 清理过程是同步的，可能会延长应用关闭时间
- 设置了30秒的最大等待时间，避免无限等待
- 大量任务时清理时间会相应增加

### 2. 数据一致性
- 确保内存状态和数据库状态同步
- 清理失败的任务会在下次启动时被标记为异常状态
- 建议定期检查数据库中的任务状态

### 3. 安全考虑
- 清理功能需要谨慎使用，会停止所有盯盘任务
- 建议在生产环境中限制清理API的访问权限
- 可以考虑添加操作日志记录

## 故障排除

### 1. 常见问题
- **任务状态不一致**：检查数据库和内存状态是否同步
- **清理超时**：检查线程池配置和任务执行时间
- **数据库连接失败**：检查数据库连接配置

### 2. 调试方法
- 查看应用关闭日志
- 检查数据库中的任务状态
- 使用测试脚本验证功能

### 3. 恢复措施
- 重启应用后检查任务状态
- 使用手动清理功能清理异常状态
- 检查数据库连接和配置

## 总结

盯盘任务关闭清理功能确保了应用在各种关闭场景下都能正确处理正在运行的任务，避免了任务状态丢失和数据不一致的问题。通过自动清理和手动清理两种方式，为用户提供了灵活的任务管理能力。
