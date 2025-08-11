#!/usr/bin/env node

const { spawn } = require('child_process');
const path = require('path');

console.log('🚀 启动股票分析API网关...\n');

// 检查环境
console.log('📋 环境检查:');
console.log(`   Node.js: ${process.version}`);
console.log(`   工作目录: ${process.cwd()}`);
console.log(`   平台: ${process.platform}\n`);

// 启动服务器
const server = spawn('node', ['server.js'], {
  stdio: 'inherit',
  cwd: __dirname
});

server.on('error', (error) => {
  console.error('❌ 启动失败:', error.message);
  process.exit(1);
});

server.on('close', (code) => {
  console.log(`\n📴 API网关已停止 (退出码: ${code})`);
  process.exit(code);
});

// 优雅关闭
process.on('SIGINT', () => {
  console.log('\n🛑 正在关闭API网关...');
  server.kill('SIGINT');
});

process.on('SIGTERM', () => {
  console.log('\n🛑 正在关闭API网关...');
  server.kill('SIGTERM');
});
