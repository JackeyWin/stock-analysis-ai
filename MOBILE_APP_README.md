# 股票分析移动端应用

一个基于React Native和Expo开发的专业股票分析移动应用，提供实时股票数据分析、技术指标计算、AI智能分析等功能。

## 🏗️ 架构设计

### 前后端分离架构
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   移动端应用     │    │    API网关      │    │   后端服务      │
│  (React Native) │◄──►│   (Node.js)     │◄──►│   (Spring Boot) │
│                 │    │                 │    │                 │
│  • 用户界面     │    │  • 请求代理     │    │  • 股票分析     │
│  • 数据展示     │    │  • 速率限制     │    │  • AI分析       │
│  • 交互逻辑     │    │  • 错误处理     │    │  • Python脚本   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### 技术栈
- **移动端**: React Native + Expo
- **API网关**: Node.js + Express
- **后端**: Spring Boot + Java
- **数据分析**: Python脚本
- **图表**: React Native Chart Kit
- **UI组件**: React Native Paper

## 📱 功能特性

### 核心功能
- 🔍 **股票搜索**: 支持股票代码和名称搜索
- 📊 **实时分析**: 综合分析、快速分析、风险评估
- 📈 **图表展示**: 价格走势图、技术指标图表
- 🤖 **AI分析**: 智能分析和投资建议
- ⚡ **快速操作**: 一键分析、收藏股票

### 界面设计
- 🎨 **Material Design**: 基于Google Material Design
- 📱 **响应式布局**: 适配不同屏幕尺寸
- 🌙 **主题支持**: 支持浅色/深色主题切换
- 🔄 **下拉刷新**: 实时更新数据

## 🚀 快速开始

### 环境要求
- Node.js 16.0+
- npm 或 yarn
- Expo CLI
- Android Studio 或 Xcode (可选)

### 安装步骤

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd stock-analysis-ai
   ```

2. **安装依赖**
   ```bash
   # 安装API网关依赖
   cd api-gateway
   npm install
   cd ..
   
   # 安装移动端依赖
   cd mobile-app
   npm install
   cd ..
   ```

3. **启动服务**
   
   **方式一: 一键启动所有服务**
   ```bash
   node start-all.js
   ```
   
   **方式二: 分别启动各服务**
   ```bash
   # 终端1: 启动后端服务
   ./gradlew bootRun
   
   # 终端2: 启动API网关
   cd api-gateway
   npm start
   
   # 终端3: 启动移动端应用
   cd mobile-app
   npm start
   ```

   # 启动cloudflared tunnel
   cloudflared tunnel run 7f4a57f9-f94f-410f-bca2-de60f86886ea 

4. **访问应用**
   - 后端服务: http://localhost:8080
   - API网关: http://localhost:3001
   - 移动端: 使用Expo Go扫描二维码

## 📂 项目结构

```
stock-analysis-ai/
├── api-gateway/                 # API网关
│   ├── server.js               # 网关服务器
│   ├── package.json            # 依赖配置
│   └── start.js                # 启动脚本
├── mobile-app/                 # 移动端应用
│   ├── src/
│   │   ├── components/         # 可复用组件
│   │   ├── screens/           # 页面组件
│   │   │   ├── HomeScreen.js  # 首页
│   │   │   ├── SearchScreen.js # 搜索页
│   │   │   ├── AnalysisScreen.js # 分析页
│   │   │   ├── SettingsScreen.js # 设置页
│   │   │   └── StockDetailScreen.js # 股票详情页
│   │   ├── services/          # API服务
│   │   │   └── ApiService.js  # API调用封装
│   │   └── utils/             # 工具函数
│   │       └── theme.js       # 主题配置
│   ├── App.js                 # 应用入口
│   ├── app.json              # Expo配置
│   └── package.json          # 依赖配置
├── src/                       # 后端源码
├── python_scripts/           # Python分析脚本
├── start-all.js             # 一键启动脚本
└── MOBILE_APP_README.md     # 本文档
```

## 🔧 配置说明

### API网关配置
在 `api-gateway/server.js` 中配置:
```javascript
const STOCK_ANALYSIS_SERVICE_URL = 'http://localhost:8080';
const PORT = 3001;
```

### 移动端配置
在 `mobile-app/app.json` 中配置:
```json
{
  "expo": {
    "extra": {
      "apiGatewayUrl": "http://localhost:3001"
    }
  }
}
```

## 📊 API接口

### 移动端专用接口
- `POST /api/mobile/stock/analyze` - 股票综合分析
- `POST /api/mobile/stock/quick-analyze` - 快速分析
- `POST /api/mobile/stock/risk-assessment` - 风险评估
- `GET /api/mobile/stock/analyze/:stockCode` - 简单分析
- `GET /api/mobile/stocks/popular` - 热门股票列表

### 响应格式
```json
{
  "success": true,
  "data": {...},
  "message": "操作成功",
  "timestamp": "2024-08-08T08:00:00.000Z"
}
```

## 🎨 界面截图

### 主要页面
- **首页**: 显示热门股票、市场概览、服务状态
- **搜索页**: 股票搜索、最近搜索、热门推荐
- **分析页**: 综合分析、快速分析、风险评估
- **详情页**: 股票详情、价格走势、技术指标
- **设置页**: 应用设置、数据管理、帮助支持

## 🔒 安全特性

### API网关安全
- **CORS配置**: 限制跨域访问
- **速率限制**: 防止API滥用
- **请求验证**: 参数校验和过滤
- **错误处理**: 统一错误响应格式

### 移动端安全
- **网络超时**: 防止长时间等待
- **错误重试**: 自动重试机制
- **数据缓存**: 本地数据缓存
- **输入验证**: 用户输入验证

## 🚀 部署指南

### 开发环境
```bash
# 启动开发服务器
npm run dev

# 在iOS模拟器中运行
npm run ios

# 在Android模拟器中运行
npm run android
```

### 生产环境
```bash
# 构建生产版本
expo build:android
expo build:ios

# 发布到应用商店
expo publish
```

## 🐛 故障排除

### 常见问题

1. **Expo启动失败**
   ```bash
   npm install -g @expo/cli
   expo doctor
   ```

2. **API连接失败**
   - 检查后端服务是否启动
   - 确认API网关配置正确
   - 检查网络连接

3. **图表不显示**
   ```bash
   npm install react-native-svg
   expo install react-native-svg
   ```

4. **依赖安装失败**
   ```bash
   rm -rf node_modules
   npm cache clean --force
   npm install
   ```

## 📈 性能优化

### 移动端优化
- **图片优化**: 使用适当的图片格式和尺寸
- **列表优化**: 使用FlatList进行长列表渲染
- **状态管理**: 合理使用React状态管理
- **网络优化**: 请求缓存和防抖处理

### API网关优化
- **响应压缩**: 启用gzip压缩
- **请求缓存**: 缓存频繁请求的数据
- **连接池**: 复用HTTP连接
- **监控日志**: 记录性能指标

## 🤝 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 📞 联系我们

- 项目主页: [GitHub Repository]
- 问题反馈: [Issues]
- 邮箱: support@stockanalysis.com

---

**股票分析移动端应用** - 让投资分析更简单、更智能！
