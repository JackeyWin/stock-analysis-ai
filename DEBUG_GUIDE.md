# 📱 股票分析移动端应用调试指南

由于无法使用Expo Go，我为您提供了多种调试方案。

## 🌐 方案一：Web浏览器调试（推荐）

这是最简单且功能完整的调试方案，无需安装任何移动端工具。

### 快速启动

**Windows用户：**
```bash
# 双击运行
start-debug.bat

# 或者命令行运行
.\start-debug.bat
```

**Linux/Mac用户：**
```bash
# 运行启动脚本
./start-debug.sh

# 或者手动启动
chmod +x start-debug.sh
./start-debug.sh
```

### 手动启动步骤

1. **启动后端服务**
   ```bash
   gradle bootRun
   ```

2. **启动API网关**
   ```bash
   cd api-gateway
   node server.js
   ```

3. **启动Web调试服务器**
   ```bash
   cd mobile-app
   node start-web-debug.js
   ```

4. **打开浏览器**
   访问：http://localhost:3000/web-debug.html

### 功能特性

✅ **完整的移动端界面模拟**
- 响应式设计，适配手机屏幕
- 标签页导航（首页、搜索、分析、设置）
- Material Design风格

✅ **完整的API功能测试**
- 服务状态检查
- 热门股票列表
- 股票搜索功能
- 综合分析、快速分析、风险评估
- 实时错误处理和状态显示

✅ **开发者友好**
- 实时API响应显示
- 详细的错误信息
- 网络状态监控
- JSON数据格式化显示

## 📱 方案二：Android模拟器调试

如果您有Android Studio，可以使用Android模拟器：

### 前置条件
- 安装Android Studio
- 配置Android SDK
- 创建Android虚拟设备(AVD)

### 启动步骤
```bash
# 启动后端和API网关
node start-all.js

# 在新终端中启动Expo
cd mobile-app
npx expo start --android
```

## 🍎 方案三：iOS模拟器调试（仅macOS）

如果您使用macOS并安装了Xcode：

### 前置条件
- macOS系统
- 安装Xcode
- 安装iOS模拟器

### 启动步骤
```bash
# 启动后端和API网关
node start-all.js

# 在新终端中启动Expo
cd mobile-app
npx expo start --ios
```

## 📲 方案四：物理设备调试

使用真实的Android/iOS设备进行调试：

### 前置条件
- Android设备：启用开发者模式和USB调试
- iOS设备：需要Apple开发者账号

### 启动步骤
```bash
# 启动后端和API网关
node start-all.js

# 启动Expo并生成二维码
cd mobile-app
npx expo start

# 使用设备扫描二维码或通过USB连接
```

## 🔧 故障排除

### 常见问题

1. **端口被占用**
   ```bash
   # 检查端口占用
   netstat -ano | findstr :8080
   netstat -ano | findstr :3001
   netstat -ano | findstr :3000
   
   # 杀死占用进程
   taskkill /PID <进程ID> /F
   ```

2. **Node.js版本问题**
   ```bash
   # 检查版本
   node --version
   npm --version
   
   # 应该是 Node.js 14+ 和 npm 6+
   ```

3. **依赖安装失败**
   ```bash
   # 清理并重新安装
   rm -rf node_modules package-lock.json
   npm install
   ```

4. **CORS错误**
   - 确保API网关正在运行
   - 检查浏览器控制台错误信息
   - 尝试刷新页面

### 服务状态检查

**检查后端服务：**
```bash
curl http://localhost:8080/api/stock/health
```

**检查API网关：**
```bash
curl http://localhost:3001/health
```

**检查Web调试服务器：**
```bash
curl http://localhost:3000
```

## 📊 调试界面说明

### 首页
- **服务状态**：显示后端和API网关的运行状态
- **热门股票**：展示预设的热门股票列表
- **快速操作**：一键分析功能

### 搜索页
- **股票搜索**：支持股票代码和名称搜索
- **搜索历史**：显示最近搜索的股票
- **搜索建议**：提供常用股票代码

### 分析页
- **综合分析**：完整的股票分析报告
- **快速分析**：基础技术指标分析
- **风险评估**：投资风险评估报告

### 设置页
- **应用信息**：版本和配置信息
- **连接测试**：测试与后端服务的连接
- **调试选项**：显示可用的调试方案

## 🚀 性能优化建议

1. **使用Chrome DevTools**
   - 打开F12开发者工具
   - 使用Network标签监控API请求
   - 使用Console查看错误日志

2. **移动端视图模拟**
   - 在Chrome中按F12
   - 点击设备图标切换到移动端视图
   - 选择iPhone或Android设备尺寸

3. **网络状况模拟**
   - 在Network标签中选择网络速度
   - 测试慢网络下的应用表现

## 📞 技术支持

如果遇到问题，请检查：

1. **控制台错误信息**
2. **网络请求状态**
3. **服务器日志输出**
4. **端口占用情况**

---

**推荐使用Web浏览器调试方案**，它提供了完整的功能测试环境，无需额外配置，是最快速有效的调试方式！
