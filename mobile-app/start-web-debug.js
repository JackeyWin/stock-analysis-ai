#!/usr/bin/env node

const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 3000;

// MIME类型映射
const mimeTypes = {
  '.html': 'text/html',
  '.js': 'text/javascript',
  '.css': 'text/css',
  '.json': 'application/json',
  '.png': 'image/png',
  '.jpg': 'image/jpg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.wav': 'audio/wav',
  '.mp4': 'video/mp4',
  '.woff': 'application/font-woff',
  '.ttf': 'application/font-ttf',
  '.eot': 'application/vnd.ms-fontobject',
  '.otf': 'application/font-otf',
  '.wasm': 'application/wasm'
};

const server = http.createServer((req, res) => {
  console.log(`${req.method} ${req.url}`);

  // 设置CORS头
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    res.writeHead(200);
    res.end();
    return;
  }

  let filePath = '.' + req.url;
  if (filePath === './') {
    filePath = './web-debug.html';
  }

  const extname = String(path.extname(filePath)).toLowerCase();
  const mimeType = mimeTypes[extname] || 'application/octet-stream';

  fs.readFile(filePath, (error, content) => {
    if (error) {
      if (error.code === 'ENOENT') {
        // 文件不存在，返回404
        res.writeHead(404, { 'Content-Type': 'text/html' });
        res.end(`
          <html>
            <head><title>404 Not Found</title></head>
            <body>
              <h1>404 - 页面不存在</h1>
              <p>请访问 <a href="/web-debug.html">股票分析助手调试页面</a></p>
            </body>
          </html>
        `);
      } else {
        // 服务器错误
        res.writeHead(500);
        res.end('服务器内部错误: ' + error.code);
      }
    } else {
      // 成功返回文件
      res.writeHead(200, { 'Content-Type': mimeType });
      res.end(content, 'utf-8');
    }
  });
});

server.listen(PORT, () => {
  console.log('🌐 股票分析助手 Web调试服务器启动成功!');
  console.log(`📱 访问地址: http://localhost:${PORT}`);
  console.log(`🔗 调试页面: http://localhost:${PORT}/web-debug.html`);
  console.log('');
  console.log('📋 调试说明:');
  console.log('   1. 确保后端服务运行在 http://localhost:8080');
  console.log('   2. 确保API网关运行在 http://localhost:3001');
  console.log('   3. 在浏览器中打开上述地址进行调试');
  console.log('');
  console.log('💡 使用 Ctrl+C 停止服务器');
});

// 优雅关闭
process.on('SIGINT', () => {
  console.log('\n🛑 正在关闭Web调试服务器...');
  server.close(() => {
    console.log('✅ Web调试服务器已停止');
    process.exit(0);
  });
});

process.on('SIGTERM', () => {
  console.log('\n🛑 正在关闭Web调试服务器...');
  server.close(() => {
    console.log('✅ Web调试服务器已停止');
    process.exit(0);
  });
});
