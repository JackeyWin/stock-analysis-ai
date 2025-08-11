#!/bin/bash

echo "=== 股票分析应用启动脚本 ==="

# 检查Java版本
echo "检查Java版本..."
java -version
if [ $? -ne 0 ]; then
    echo "错误: 未找到Java，请确保Java 17+已安装"
    exit 1
fi

# 检查Python版本
echo "检查Python版本..."
python --version
if [ $? -ne 0 ]; then
    echo "错误: 未找到Python，请确保Python 3.8+已安装"
    exit 1
fi

# 检查Python依赖
echo "检查Python依赖..."
python -c "import pandas, numpy, curl_cffi" 2>/dev/null
if [ $? -ne 0 ]; then
    echo "安装Python依赖..."
    pip install pandas numpy curl-cffi
fi

# 检查DeepSeek API密钥
if [ -z "$DEEPSEEK_API_KEY" ]; then
    echo "警告: 未设置DEEPSEEK_API_KEY环境变量"
    echo "请设置: export DEEPSEEK_API_KEY=your-api-key"
fi

# 构建并启动应用
echo "构建应用..."
./gradlew build

if [ $? -eq 0 ]; then
    echo "启动应用..."
    ./gradlew bootRun
else
    echo "构建失败，请检查错误信息"
    exit 1
fi