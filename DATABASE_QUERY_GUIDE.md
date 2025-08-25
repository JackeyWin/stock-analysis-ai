# 数据库查询工具使用指南

## 概述

我为您创建了一个完整的数据库查询工具，无需安装任何数据库客户端软件，直接通过浏览器即可查询数据库。

## 功能特性

### 🔧 已创建的文件
1. **DatabaseQueryController.java** - 数据库查询API控制器
2. **DatabaseQueryService.java** - 数据库查询服务类
3. **database-query.html** - 网页查询界面

### 📊 主要功能
- **数据库统计信息** - 查看数据库版本、表数量、各表记录数
- **表管理** - 列出所有表，快速查看表结构和数据
- **表结构查询** - 查看表的列名、数据类型、约束等信息
- **表数据查询** - 分页查看表数据，支持翻页
- **自定义SQL查询** - 执行SELECT查询语句

## 使用方法

### 1. 启动应用
确保您的Spring Boot应用正在运行：
```bash
./gradlew bootRun
```

### 2. 访问查询工具
在浏览器中打开：
```
http://localhost:8080/database-query.html
```

### 3. 功能使用

#### 📈 查看数据库统计信息
- 点击"获取统计信息"按钮
- 自动显示数据库版本、表数量、各表记录数

#### 📋 查看所有表
- 点击"获取所有表"按钮
- 显示数据库中所有表的列表
- 每个表都有"查看结构"和"查看数据"按钮

#### 🔍 查看表结构
- 在"表结构查询"区域输入表名
- 点击"获取表结构"按钮
- 显示表的列名、数据类型、大小、是否可空等信息

#### 📄 查看表数据
- 在"表数据查询"区域输入表名
- 设置页码和每页大小
- 点击"获取表数据"按钮
- 支持分页浏览，可以翻页查看

#### ⚡ 执行自定义查询
- 在"自定义SQL查询"区域输入SELECT语句
- 例如：`SELECT * FROM analysis_history LIMIT 10`
- 点击"执行查询"按钮
- 显示查询结果

## API接口

### 获取数据库统计信息
```
GET /api/database/stats
```

### 获取所有表
```
GET /api/database/tables
```

### 获取表结构
```
GET /api/database/table/{tableName}/structure
```

### 获取表数据（分页）
```
GET /api/database/table/{tableName}/data?page=1&size=10
```

### 执行自定义查询
```
POST /api/database/query
Content-Type: application/json

{
  "sql": "SELECT * FROM your_table LIMIT 10"
}
```

## 安全特性

### 🔒 查询限制
- 只允许执行SELECT查询语句
- 防止SQL注入攻击
- 查询结果有大小限制

### 🛡️ 错误处理
- 完善的异常处理机制
- 友好的错误提示信息
- 详细的日志记录

## 常见查询示例

### 查看分析历史记录
```sql
SELECT * FROM analysis_history ORDER BY timestamp DESC LIMIT 20
```

### 查看推荐数据
```sql
SELECT * FROM daily_recommendation ORDER BY created_at DESC LIMIT 10
```

### 统计各表记录数
```sql
SELECT 
    schemaname,
    tablename,
    n_tup_ins as inserts,
    n_tup_upd as updates,
    n_tup_del as deletes
FROM pg_stat_user_tables
ORDER BY n_tup_ins DESC
```

### 查看表大小
```sql
SELECT 
    table_name,
    pg_size_pretty(pg_total_relation_size(quote_ident(table_name))) as size
FROM information_schema.tables 
WHERE table_schema = 'public'
ORDER BY pg_total_relation_size(quote_ident(table_name)) DESC
```

## 故障排除

### 问题1：无法访问页面
**解决方案：**
- 确保Spring Boot应用正在运行
- 检查端口8080是否被占用
- 确认防火墙设置

### 问题2：数据库连接失败
**解决方案：**
- 检查PostgreSQL服务是否运行
- 验证数据库连接配置（application.yml）
- 确认数据库用户权限

### 问题3：查询无结果
**解决方案：**
- 检查表名是否正确
- 确认表中是否有数据
- 验证SQL语法是否正确

### 问题4：查询超时
**解决方案：**
- 减少查询的数据量
- 添加LIMIT限制
- 优化查询条件

## 注意事项

1. **只读操作** - 此工具只支持查询操作，不会修改数据
2. **性能考虑** - 大数据量查询可能较慢，建议使用分页
3. **权限控制** - 确保数据库用户有足够的查询权限
4. **日志记录** - 所有查询操作都会记录在应用日志中

## 扩展功能

如需添加更多功能，可以：
1. 在`DatabaseQueryService`中添加新的查询方法
2. 在`DatabaseQueryController`中添加对应的API接口
3. 在HTML页面中添加新的功能按钮

---

现在您可以通过浏览器轻松查询数据库，无需安装任何额外的数据库工具！
