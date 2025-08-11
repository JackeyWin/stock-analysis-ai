#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

console.log('📱 启动Expo Go兼容模式...\n');

// 备份当前文件
const backupFiles = [
    'package.json',
    'app.json',
    'babel.config.js'
];

console.log('📦 备份当前配置文件...');
backupFiles.forEach(file => {
    if (fs.existsSync(file)) {
        fs.copyFileSync(file, `${file}.backup`);
        console.log(`   ✓ 备份 ${file}`);
    }
});

// 使用Expo Go兼容的配置
console.log('\n🔄 切换到Expo Go兼容配置...');
try {
    if (fs.existsSync('package-expo-go.json')) {
        fs.copyFileSync('package-expo-go.json', 'package.json');
        console.log('   ✓ 使用Expo Go兼容的package.json');
    }
    
    if (fs.existsSync('app-expo-go.json')) {
        fs.copyFileSync('app-expo-go.json', 'app.json');
        console.log('   ✓ 使用Expo Go兼容的app.json');
    }
    
    if (fs.existsSync('babel-expo-go.config.js')) {
        fs.copyFileSync('babel-expo-go.config.js', 'babel.config.js');
        console.log('   ✓ 使用Expo Go兼容的babel.config.js');
    }
    
    console.log('\n📦 安装Expo Go兼容依赖...');
    const install = spawn('npm', ['install'], {
        stdio: 'inherit',
        shell: true
    });
    
    install.on('close', (code) => {
        if (code === 0) {
            console.log('\n🚀 启动Expo开发服务器...');
            
            const expo = spawn('npx', ['expo', 'start', '--tunnel'], {
                stdio: 'inherit',
                shell: true
            });
            
            expo.on('close', () => {
                restoreFiles();
            });
            
            // 监听退出信号
            process.on('SIGINT', () => {
                console.log('\n🔄 恢复原始配置...');
                expo.kill();
                restoreFiles();
                process.exit(0);
            });
            
        } else {
            console.error('❌ 依赖安装失败');
            restoreFiles();
            process.exit(1);
        }
    });
    
} catch (error) {
    console.error('❌ 配置切换失败:', error.message);
    restoreFiles();
    process.exit(1);
}

// 恢复原始文件
function restoreFiles() {
    console.log('\n🔄 恢复原始配置文件...');
    backupFiles.forEach(file => {
        const backupFile = `${file}.backup`;
        if (fs.existsSync(backupFile)) {
            fs.copyFileSync(backupFile, file);
            fs.unlinkSync(backupFile);
            console.log(`   ✓ 恢复 ${file}`);
        }
    });
    console.log('✅ 配置文件已恢复');
}
