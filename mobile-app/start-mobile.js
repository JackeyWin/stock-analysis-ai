#!/usr/bin/env node

const { spawn } = require('child_process');
const path = require('path');

console.log('📱 启动股票分析移动端应用...\n');

// 检查环境
console.log('📋 环境检查:');
console.log(`   Node.js: ${process.version}`);
console.log(`   工作目录: ${process.cwd()}`);
console.log(`   平台: ${process.platform}\n`);

// 检查是否安装了依赖
const fs = require('fs');
if (!fs.existsSync('node_modules')) {
  console.log('📦 正在安装依赖...');
  const install = spawn('npm', ['install'], {
    stdio: 'inherit',
    cwd: __dirname,
    shell: true
  });
  
  install.on('close', (code) => {
    if (code === 0) {
      startApp();
    } else {
      console.error('❌ 依赖安装失败');
      process.exit(1);
    }
  });
} else {
  startApp();
}

function startApp() {
  console.log('🚀 启动Expo开发服务器...\n');
  
  // 启动Expo
  const expo = spawn('npx', ['expo', 'start'], {
    stdio: 'inherit',
    cwd: __dirname,
    shell: true
  });

  expo.on('error', (error) => {
    console.error('❌ 启动失败:', error.message);
    console.log('\n💡 请确保已安装Expo CLI:');
    console.log('   npm install -g @expo/cli');
    process.exit(1);
  });

  expo.on('close', (code) => {
    console.log(`\n📴 移动端应用已停止 (退出码: ${code})`);
    process.exit(code);
  });

  // 优雅关闭
  process.on('SIGINT', () => {
    console.log('\n🛑 正在关闭移动端应用...');
    expo.kill('SIGINT');
  });

  process.on('SIGTERM', () => {
    console.log('\n🛑 正在关闭移动端应用...');
    expo.kill('SIGTERM');
  });
}
