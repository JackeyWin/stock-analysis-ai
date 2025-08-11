@echo off
echo === 股票分析应用启动脚本 ===

REM 检查Java版本
echo 检查Java版本...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未找到Java，请确保Java 17+已安装
    pause
    exit /b 1
)

REM 检查Python版本
echo 检查Python版本...
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未找到Python，请确保Python 3.8+已安装
    pause
    exit /b 1
)

REM 检查Python依赖
echo 检查Python依赖...
python -c "import pandas, numpy, curl_cffi" >nul 2>&1
if %errorlevel% neq 0 (
    echo 安装Python依赖...
    pip install pandas numpy curl-cffi
)

REM 检查DeepSeek API密钥
if "%DEEPSEEK_API_KEY%"=="" (
    echo 警告: 未设置DEEPSEEK_API_KEY环境变量
    echo 请设置: set DEEPSEEK_API_KEY=your-api-key
)

REM 构建并启动应用
echo 构建应用...
gradlew.bat build

if %errorlevel% equ 0 (
    echo 启动应用...
    gradlew.bat bootRun
) else (
    echo 构建失败，请检查错误信息
    pause
    exit /b 1
)

pause