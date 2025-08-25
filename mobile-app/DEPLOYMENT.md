# 🚀 Web版本部署说明

## 部署方式

本项目支持两种Web部署方式：

### 方式1: 静态导出部署（推荐用于生产环境）

```bash
cd mobile-app
npx expo export --platform web
xcopy /E /I /Y dist D:\nginx-1.29.0\html\stock-app
cd D:\nginx-1.29.0
.\nginx.exe -s reload
```

### 方式2: 开发服务器部署

```bash
cd mobile-app
npm run web
```

## 静态导出部署步骤

### 1. 导出Web版本

```bash
cd mobile-app
npx expo export --platform web
```

这会在 `dist` 目录下生成静态文件。

### 2. 部署到Nginx

```bash
# 复制文件到Nginx目录
xcopy /E /I /I /Y dist D:\nginx-1.29.0\html\stock-app

# 重启Nginx
cd D:\nginx-1.29.0
.\nginx.exe -s reload
```

### 3. 访问应用

打开浏览器访问：`http://your-domain/stock-app`

## Nginx配置建议

在 `nginx.conf` 中添加以下配置：

```nginx
server {
    listen 80;
    server_name your-domain;
    
    location /stock-app {
        alias D:/nginx-1.29.0/html/stock-app;
        try_files $uri $uri/ /stock-app/index.html;
        
        # 启用gzip压缩
        gzip on;
        gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;
        
        # 缓存静态资源
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
            expires 1y;
            add_header Cache-Control "public, immutable";
        }
    }
}
```

## Mermaid图表库加载

### 自动加载机制

Web版本会自动尝试从多个CDN源加载Mermaid库：

1. `https://cdn.jsdelivr.net/npm/mermaid@10.6.1/dist/mermaid.min.js`
2. `https://unpkg.com/mermaid@10.6.1/dist/mermaid.min.js`
3. `https://cdnjs.cloudflare.com/ajax/libs/mermaid/10.6.1/mermaid.min.js`

### 加载状态指示

- 右上角显示Mermaid库加载状态
- 绿色：✅ 库已加载
- 红色：❌ 库加载失败
- 黑色：⏳ 正在加载

### 故障排除

如果Mermaid库加载失败：

1. **检查网络连接**
   - 确保服务器能访问外部CDN
   - 检查防火墙设置

2. **查看浏览器控制台**
   - 按F12打开开发者工具
   - 查看Console和Network标签页

3. **手动重试**
   - 页面会显示"重试加载"按钮
   - 点击按钮重新加载Mermaid库

4. **本地部署Mermaid库**
   - 下载 `mermaid.min.js` 到本地
   - 修改 `web/index.html` 中的CDN链接

## 性能优化

### 1. 启用Gzip压缩

```nginx
gzip on;
gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;
```

### 2. 设置缓存策略

```nginx
# 静态资源缓存
location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
    expires 1y;
    add_header Cache-Control "public, immutable";
}

# HTML文件不缓存
location ~* \.html$ {
    add_header Cache-Control "no-cache, no-store, must-revalidate";
}
```

### 3. 启用HTTP/2

```nginx
listen 443 ssl http2;
```

## 安全配置

### 1. 添加安全头

```nginx
add_header X-Frame-Options "SAMEORIGIN" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-XSS-Protection "1; mode=block" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
```

### 2. 限制文件访问

```nginx
# 禁止访问敏感文件
location ~ /\. {
    deny all;
}

location ~ \.(htaccess|htpasswd|ini|log|sh|sql|conf)$ {
    deny all;
}
```

## 监控和日志

### 1. 访问日志

```nginx
access_log logs/stock-app.access.log;
error_log logs/stock-app.error.log;
```

### 2. 性能监控

在 `web/index.html` 中添加性能监控：

```javascript
// 监控页面加载性能
window.addEventListener('load', () => {
    const perfData = performance.getEntriesByType('navigation')[0];
    console.log('页面加载时间:', perfData.loadEventEnd - perfData.loadEventStart, 'ms');
});
```

## 常见问题

### Q1: Mermaid图表不显示
**A:** 检查Mermaid库是否成功加载，查看右上角状态指示器。

### Q2: 页面显示空白
**A:** 检查Nginx配置，确保 `try_files` 正确设置。

### Q3: 静态资源404错误
**A:** 检查文件路径和权限，确保Nginx能访问部署目录。

### Q4: 图表渲染缓慢
**A:** 启用Gzip压缩，检查网络延迟，考虑使用本地Mermaid库。

## 更新部署

当代码更新后，重新部署：

```bash
cd mobile-app
npx expo export --platform web
xcopy /E /I /Y dist D:\nginx-1.29.0\html\stock-app
cd D:\nginx-1.29.0
.\nginx.exe -s reload
```

## 技术支持

如果遇到部署问题：

1. 检查Nginx错误日志
2. 查看浏览器控制台错误
3. 确认文件权限和路径
4. 验证网络连接和防火墙设置
