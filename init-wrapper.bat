@echo off
echo === 初始化Gradle Wrapper ===

REM 检查是否已安装Gradle
gradle --version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未找到Gradle，请先安装Gradle
    echo.
    echo 安装方法：
    echo 1. 下载Gradle: https://gradle.org/install/
    echo 2. 或使用Chocolatey: choco install gradle
    echo 3. 或使用Scoop: scoop install gradle
    pause
    exit /b 1
)

echo 当前Gradle版本:
gradle --version
echo.

REM 生成Gradle Wrapper
echo 生成Gradle Wrapper...
gradle wrapper --gradle-version 8.4 --distribution-type bin

if %errorlevel% equ 0 (
    echo.
    echo ✅ Gradle Wrapper初始化成功！
    echo.
    echo 现在可以使用以下命令：
    echo - gradlew.bat --version  # 查看版本
    echo - gradlew.bat build      # 构建项目
    echo - gradlew.bat bootRun    # 运行应用
    echo.
    echo 测试Gradle Wrapper...
    gradlew.bat --version
) else (
    echo ❌ Gradle Wrapper初始化失败
)

pause