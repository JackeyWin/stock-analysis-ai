#!/bin/bash

echo "🚀 启动股票分析系统调试环境"
echo ""

echo "📋 可用的调试选项:"
echo "   1. Web浏览器调试 (推荐)"
echo "   2. 启动所有服务"
echo "   3. 仅启动后端服务"
echo "   4. 仅启动API网关"
echo "   5. 检查服务状态"
echo ""

read -p "请选择调试方式 (1-5): " choice

case $choice in
    1)
        echo ""
        echo "🌐 启动Web浏览器调试模式..."
        echo ""
        
        echo "📊 启动后端服务..."
        gnome-terminal --title="后端服务" -- bash -c "./gradlew bootRun; exec bash" 2>/dev/null || \
        osascript -e 'tell app "Terminal" to do script "./gradlew bootRun"' 2>/dev/null || \
        ./gradlew bootRun &
        sleep 5
        
        echo "🌐 启动API网关..."
        gnome-terminal --title="API网关" -- bash -c "cd api-gateway && node server.js; exec bash" 2>/dev/null || \
        osascript -e 'tell app "Terminal" to do script "cd api-gateway && node server.js"' 2>/dev/null || \
        (cd api-gateway && node server.js) &
        sleep 3
        
        echo "📱 启动Web调试服务器..."
        gnome-terminal --title="Web调试" -- bash -c "cd mobile-app && node start-web-debug.js; exec bash" 2>/dev/null || \
        osascript -e 'tell app "Terminal" to do script "cd mobile-app && node start-web-debug.js"' 2>/dev/null || \
        (cd mobile-app && node start-web-debug.js) &
        sleep 2
        
        echo ""
        echo "✅ 所有服务启动完成!"
        echo ""
        echo "📋 访问地址:"
        echo "   • 后端服务: http://localhost:8080"
        echo "   • API网关: http://localhost:3001"
        echo "   • Web调试: http://localhost:3000"
        echo ""
        echo "🌐 正在打开浏览器..."
        
        # 尝试打开浏览器
        if command -v xdg-open > /dev/null; then
            xdg-open http://localhost:3000/web-debug.html
        elif command -v open > /dev/null; then
            open http://localhost:3000/web-debug.html
        else
            echo "请手动打开浏览器访问: http://localhost:3000/web-debug.html"
        fi
        ;;
    2)
        echo ""
        echo "🚀 启动所有服务..."
        node start-all.js
        ;;
    3)
        echo ""
        echo "📊 仅启动后端服务..."
        ./gradlew bootRun
        ;;
    4)
        echo ""
        echo "🌐 仅启动API网关..."
        cd api-gateway
        node server.js
        ;;
    5)
        echo ""
        echo "🔍 检查服务状态..."
        echo ""
        
        echo "检查后端服务 (端口8080)..."
        if curl -s http://localhost:8080/api/stock/health > /dev/null 2>&1; then
            echo "✅ 后端服务: 正常运行"
        else
            echo "❌ 后端服务: 未运行"
        fi
        
        echo ""
        echo "检查API网关 (端口3001)..."
        if curl -s http://localhost:3001/health > /dev/null 2>&1; then
            echo "✅ API网关: 正常运行"
        else
            echo "❌ API网关: 未运行"
        fi
        
        echo ""
        echo "检查Web调试服务器 (端口3000)..."
        if curl -s http://localhost:3000 > /dev/null 2>&1; then
            echo "✅ Web调试服务器: 正常运行"
        else
            echo "❌ Web调试服务器: 未运行"
        fi
        ;;
    *)
        echo ""
        echo "❌ 无效选择，请重新运行脚本"
        ;;
esac

echo ""
echo "💡 调试完成"
