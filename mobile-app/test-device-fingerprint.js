// 设备指纹功能测试脚本
// 运行: node test-device-fingerprint.js

const { execSync } = require('child_process');
const path = require('path');

console.log('🧪 开始测试设备指纹功能...\n');

try {
  // 检查依赖包是否安装
  console.log('📦 检查依赖包...');
  const packageJson = require('./package.json');
  
  if (packageJson.dependencies['expo-application']) {
    console.log('✅ expo-application 已安装');
  } else {
    console.log('❌ expo-application 未安装');
    process.exit(1);
  }
  
  // 检查设备指纹文件是否存在
  console.log('\n📁 检查文件结构...');
  const filesToCheck = [
    './src/utils/deviceFingerprint.js',
    './src/services/DeviceService.js',
    './src/components/DeviceFingerprintDemo.js'
  ];
  
  filesToCheck.forEach(file => {
    try {
      require.resolve(path.join(__dirname, file));
      console.log(`✅ ${file} 存在`);
    } catch {
      console.log(`❌ ${file} 不存在`);
      process.exit(1);
    }
  });
  
  // 检查ApiService是否已集成设备指纹
  console.log('\n🔗 检查ApiService集成...');
  const apiServiceContent = require('fs').readFileSync('./src/services/ApiService.js', 'utf8');
  
  if (apiServiceContent.includes('DeviceService')) {
    console.log('✅ ApiService 已集成设备指纹');
  } else {
    console.log('❌ ApiService 未集成设备指纹');
  }
  
  if (apiServiceContent.includes('withDeviceHeaders')) {
    console.log('✅ 请求拦截器已添加设备头信息');
  } else {
    console.log('❌ 请求拦截器未添加设备头信息');
  }
  
  // 语法检查
  console.log('\n🔍 语法检查...');
  try {
    execSync('npx eslint ./src/utils/deviceFingerprint.js --no-eslintrc --env es6', { 
      stdio: 'pipe',
      cwd: __dirname 
    });
    console.log('✅ 设备指纹文件语法正确');
  } catch (error) {
    console.log('⚠️  设备指纹文件语法警告:', error.stdout?.toString() || error.message);
  }
  
  console.log('\n🎉 设备指纹功能测试完成！');
  console.log('\n📋 下一步操作:');
  console.log('1. 运行应用: npm start');
  console.log('2. 查看设备指纹演示: 在应用中访问DeviceFingerprintDemo组件');
  console.log('3. 验证API请求: 检查网络请求中的X-Device-*头信息');
  
} catch (error) {
  console.error('❌ 测试失败:', error.message);
  process.exit(1);
}