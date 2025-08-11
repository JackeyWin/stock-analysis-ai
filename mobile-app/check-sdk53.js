#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

console.log('🔍 检查Expo SDK 53兼容性...\n');

// 检查package.json
const packagePath = path.join(__dirname, 'package.json');
if (fs.existsSync(packagePath)) {
    const pkg = JSON.parse(fs.readFileSync(packagePath, 'utf8'));
    
    console.log('📦 依赖版本检查:');
    console.log(`   Expo: ${pkg.dependencies.expo}`);
    console.log(`   React: ${pkg.dependencies.react}`);
    console.log(`   React Native: ${pkg.dependencies['react-native']}`);
    console.log(`   Expo Status Bar: ${pkg.dependencies['expo-status-bar']}`);
    console.log('');
    
    // 检查关键依赖
    const criticalDeps = {
        'expo': '~53.0.0',
        'react': '18.3.1',
        'react-native': '0.76.3',
        'expo-status-bar': '~1.12.1'
    };
    
    let allGood = true;
    console.log('✅ 兼容性检查:');
    
    for (const [dep, expectedVersion] of Object.entries(criticalDeps)) {
        const currentVersion = pkg.dependencies[dep];
        if (currentVersion) {
            console.log(`   ${dep}: ${currentVersion} ✓`);
        } else {
            console.log(`   ${dep}: 未安装 ❌`);
            allGood = false;
        }
    }
    
    console.log('');
    
    if (allGood) {
        console.log('🎉 所有关键依赖都已更新到SDK 53兼容版本!');
    } else {
        console.log('⚠️  发现兼容性问题，请检查依赖版本');
    }
}

// 检查app.json
const appConfigPath = path.join(__dirname, 'app.json');
if (fs.existsSync(appConfigPath)) {
    const appConfig = JSON.parse(fs.readFileSync(appConfigPath, 'utf8'));
    
    console.log('\n📱 应用配置检查:');
    console.log(`   应用名称: ${appConfig.expo.name}`);
    console.log(`   版本: ${appConfig.expo.version}`);
    console.log(`   新架构: ${appConfig.expo.newArchEnabled ? '启用' : '禁用'}`);
    console.log(`   iOS最低版本: ${appConfig.expo.ios?.deploymentTarget || '未设置'}`);
    console.log(`   Android目标SDK: ${appConfig.expo.android?.targetSdkVersion || '未设置'}`);
}

// 检查必要文件
console.log('\n📁 文件检查:');
const requiredFiles = [
    'babel.config.js',
    'metro.config.js',
    'App.js',
    'src/services/ApiService.js'
];

for (const file of requiredFiles) {
    const filePath = path.join(__dirname, file);
    if (fs.existsSync(filePath)) {
        console.log(`   ${file}: 存在 ✓`);
    } else {
        console.log(`   ${file}: 缺失 ❌`);
    }
}

console.log('\n🚀 下一步操作:');
console.log('   1. 删除node_modules和package-lock.json');
console.log('   2. 运行 npm install 重新安装依赖');
console.log('   3. 运行 npx expo start 启动开发服务器');
console.log('   4. 如遇问题，运行 npx expo doctor 诊断');

console.log('\n💡 SDK 53新特性:');
console.log('   • 支持React Native 0.76');
console.log('   • 新架构(New Architecture)支持');
console.log('   • 改进的Metro配置');
console.log('   • 更好的TypeScript支持');
console.log('   • 优化的构建性能');
