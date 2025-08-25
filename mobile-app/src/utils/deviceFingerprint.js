import { Platform } from 'react-native';
import * as Constants from 'expo-constants';
import { getUniqueId } from 'expo-application';

/**
 * 设备指纹工具类 - 基于浏览器/设备特征生成唯一标识
 */
class DeviceFingerprint {
  
  /**
   * 生成设备指纹
   * @returns {Promise<string>} 设备指纹哈希值
   */
  static async generateFingerprint() {
    try {
      // 收集设备特征信息
      const deviceFeatures = await this.collectDeviceFeatures();
      
      // 将特征信息转换为字符串并生成哈希
      const fingerprintString = JSON.stringify(deviceFeatures);
      const fingerprintHash = await this.hashString(fingerprintString);
      
      return fingerprintHash;
    } catch (error) {
      console.error('生成设备指纹失败:', error);
      // 返回备用标识
      return this.generateFallbackId();
    }
  }

  /**
   * 收集设备特征信息
   * @returns {Promise<Object>} 设备特征对象
   */
  static async collectDeviceFeatures() {
    const features = {
      // 平台信息
      platform: Platform.OS,
      platformVersion: Platform.Version,
      
      // 设备信息
      deviceId: await this.getDeviceId(),
      deviceName: Constants.deviceName,
      
      // 应用信息
      appVersion: Constants.manifest?.version || Constants.expoConfig?.version,
      appId: Constants.manifest?.id || Constants.expoConfig?.slug,
      
      // 系统信息
      systemVersion: Constants.systemVersion,
      deviceYearClass: Constants.deviceYearClass,
      
      // 屏幕信息
      screen: {
        width: Constants.width,
        height: Constants.height,
        scale: Constants.scale,
        fontScale: Constants.fontScale
      },
      
      // 时间信息
      timestamp: Date.now(),
      
      // 其他特征
      userAgent: this.getUserAgent(),
      language: Constants.locale,
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone
    };

    return features;
  }

  /**
   * 获取设备ID
   * @returns {Promise<string>} 设备唯一标识
   */
  static async getDeviceId() {
    try {
      // 尝试使用expo-application获取设备ID
      if (getUniqueId) {
        return await getUniqueId();
      }
      
      // 备用方案：使用平台特定的标识
      if (Platform.OS === 'android') {
        return Constants.androidId || 'android-unknown';
      } else if (Platform.OS === 'ios') {
        return Constants.installationId || 'ios-unknown';
      }
      
      return 'web-unknown';
    } catch (error) {
      return 'error-' + Date.now();
    }
  }

  /**
   * 获取用户代理信息
   * @returns {string} 用户代理字符串
   */
  static getUserAgent() {
    if (Platform.OS === 'web') {
      return navigator.userAgent;
    }
    return `${Platform.OS}-${Platform.Version}-react-native`;
  }

  /**
   * 字符串哈希函数
   * @param {string} str 要哈希的字符串
   * @returns {Promise<string>} 哈希值
   */
  static async hashString(str) {
    // 简单的哈希函数实现
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      const char = str.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash; // 转换为32位整数
    }
    
    // 添加随机数增加唯一性
    const randomSuffix = Math.random().toString(36).substring(2, 10);
    return Math.abs(hash).toString(36) + randomSuffix;
  }

  /**
   * 生成备用标识
   * @returns {string} 备用标识
   */
  static generateFallbackId() {
    const timestamp = Date.now().toString(36);
    const random = Math.random().toString(36).substring(2, 10);
    return `fallback-${timestamp}-${random}`;
  }

  /**
   * 验证设备指纹格式
   * @param {string} fingerprint 设备指纹
   * @returns {boolean} 是否有效
   */
  static isValidFingerprint(fingerprint) {
    return typeof fingerprint === 'string' && fingerprint.length >= 12 && fingerprint.length <= 64;
  }

  /**
   * 获取缓存的设备指纹
   * @returns {Promise<string>} 缓存的指纹或新生成的指纹
   */
  static async getCachedFingerprint() {
    try {
      // 这里可以集成AsyncStorage进行持久化缓存
      // import AsyncStorage from '@react-native-async-storage/async-storage';
      
      // const cachedFingerprint = await AsyncStorage.getItem('device_fingerprint');
      // if (cachedFingerprint && this.isValidFingerprint(cachedFingerprint)) {
      //   return cachedFingerprint;
      // }
      
      // 生成新的指纹并缓存
      const newFingerprint = await this.generateFingerprint();
      // await AsyncStorage.setItem('device_fingerprint', newFingerprint);
      
      return newFingerprint;
    } catch (error) {
      return this.generateFallbackId();
    }
  }
}

export default DeviceFingerprint;