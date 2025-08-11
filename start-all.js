#!/usr/bin/env node

const { spawn } = require('child_process');
const path = require('path');

console.log('🚀 启动完整的股票分析系统...\n');

const services = [];

// 启动后端Java服务
function startBackend() {
  console.log('📊 启动后端股票分析服务...');
  
  const backend = spawn('java', ['-jar', 'build/libs/stock-analysis-app-1.0.0.jar'], {
    stdio: 'pipe',
    cwd: __dirname
  });
  
  backend.stdout.on('data', (data) => {
    console.log(`[后端] ${data.toString().trim()}`);
  });
  
  backend.stderr.on('data', (data) => {
    console.error(`[后端错误] ${data.toString().trim()}`);
  });
  
  backend.on('error', (error) => {
    console.error('❌ 后端启动失败:', error.message);
    console.log('💡 请先构建后端项目: ./gradlew build');
  });
  
  services.push({ name: '后端服务', process: backend });
  return backend;
}

// 启动API网关
function startApiGateway() {
  console.log('🌐 启动API网关...');
  
  const gateway = spawn('node', ['server.js'], {
    stdio: 'pipe',
    cwd: path.join(__dirname, 'api-gateway')
  });
  
  gateway.stdout.on('data', (data) => {
    console.log(`[网关] ${data.toString().trim()}`);
  });
  
  gateway.stderr.on('data', (data) => {
    console.error(`[网关错误] ${data.toString().trim()}`);
  });
  
  gateway.on('error', (error) => {
    console.error('❌ API网关启动失败:', error.message);
    console.log('💡 请先安装网关依赖: cd api-gateway && npm install');
  });
  
  services.push({ name: 'API网关', process: gateway });
  return gateway;
}

// 启动移动端应用
function startMobileApp() {
  console.log('📱 启动移动端应用...');
  
  const mobile = spawn('npx', ['expo', 'start'], {
    stdio: 'pipe',
    cwd: path.join(__dirname, 'mobile-app'),
    shell: true
  });
  
  mobile.stdout.on('data', (data) => {
    console.log(`[移动端] ${data.toString().trim()}`);
  });
  
  mobile.stderr.on('data', (data) => {
    console.error(`[移动端错误] ${data.toString().trim()}`);
  });
  
  mobile.on('error', (error) => {
    console.error('❌ 移动端启动失败:', error.message);
    console.log('💡 请先安装移动端依赖: cd mobile-app && npm install');
  });
  
  services.push({ name: '移动端应用', process: mobile });
  return mobile;
}

// 延迟启动函数
function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// 按顺序启动所有服务
async function startAll() {
  try {
    // 1. 启动后端服务
    startBackend();
    console.log('⏳ 等待后端服务启动...\n');
    await delay(10000); // 等待10秒
    
    // 2. 启动API网关
    startApiGateway();
    console.log('⏳ 等待API网关启动...\n');
    await delay(3000); // 等待3秒
    
    // 3. 启动移动端应用
    startMobileApp();
    
    console.log('\n✅ 所有服务启动完成!');
    console.log('📋 服务列表:');
    console.log('   • 后端服务: http://localhost:8080');
    console.log('   • API网关: http://localhost:3001');
    console.log('   • 移动端: http://localhost:19006 (Expo DevTools)');
    console.log('\n💡 使用 Ctrl+C 停止所有服务\n');
    
  } catch (error) {
    console.error('❌ 启动过程中出现错误:', error.message);
    process.exit(1);
  }
}

// 优雅关闭所有服务
function shutdown() {
  console.log('\n🛑 正在关闭所有服务...');
  
  services.forEach(service => {
    console.log(`   停止 ${service.name}...`);
    service.process.kill('SIGTERM');
  });
  
  setTimeout(() => {
    console.log('✅ 所有服务已停止');
    process.exit(0);
  }, 3000);
}

// 监听退出信号
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

// 启动所有服务
startAll();
