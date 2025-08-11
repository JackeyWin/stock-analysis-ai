@echo off
echo 🚀 启动股票分析系统调试环境
echo.

echo 📋 可用的调试选项:
echo    1. Web浏览器调试 (推荐)
echo    2. 启动所有服务
echo    3. 仅启动后端服务
echo    4. 仅启动API网关
echo    5. 检查服务状态
echo.

set /p choice="请选择调试方式 (1-5): "

if "%choice%"=="1" goto web_debug
if "%choice%"=="2" goto start_all
if "%choice%"=="3" goto start_backend
if "%choice%"=="4" goto start_gateway
if "%choice%"=="5" goto check_status
goto invalid_choice

:web_debug
echo.
echo 🌐 启动Web浏览器调试模式...
echo.
echo 📊 启动后端服务...
start "后端服务" cmd /k "gradle bootRun"
timeout /t 5 /nobreak >nul

echo 🌐 启动API网关...
start "API网关" cmd /k "cd api-gateway && node server.js"
timeout /t 3 /nobreak >nul

echo 📱 启动Web调试服务器...
start "Web调试" cmd /k "cd mobile-app && node start-web-debug.js"
timeout /t 2 /nobreak >nul

echo.
echo ✅ 所有服务启动完成!
echo.
echo 📋 访问地址:
echo    • 后端服务: http://localhost:8080
echo    • API网关: http://localhost:3001
echo    • Web调试: http://localhost:3000
echo.
echo 🌐 正在打开浏览器...
start http://localhost:3000/web-debug.html
goto end

:start_all
echo.
echo 🚀 启动所有服务...
node start-all.js
goto end

:start_backend
echo.
echo 📊 仅启动后端服务...
gradle bootRun
goto end

:start_gateway
echo.
echo 🌐 仅启动API网关...
cd api-gateway
node server.js
goto end

:check_status
echo.
echo 🔍 检查服务状态...
echo.
echo 检查后端服务 (端口8080)...
curl -s http://localhost:8080/api/stock/health >nul 2>&1
if %errorlevel%==0 (
    echo ✅ 后端服务: 正常运行
) else (
    echo ❌ 后端服务: 未运行
)

echo.
echo 检查API网关 (端口3001)...
curl -s http://localhost:3001/health >nul 2>&1
if %errorlevel%==0 (
    echo ✅ API网关: 正常运行
) else (
    echo ❌ API网关: 未运行
)

echo.
echo 检查Web调试服务器 (端口3000)...
curl -s http://localhost:3000 >nul 2>&1
if %errorlevel%==0 (
    echo ✅ Web调试服务器: 正常运行
) else (
    echo ❌ Web调试服务器: 未运行
)
goto end

:invalid_choice
echo.
echo ❌ 无效选择，请重新运行脚本
goto end

:end
echo.
echo 💡 按任意键退出...
pause >nul
