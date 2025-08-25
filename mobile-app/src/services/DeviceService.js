import DeviceFingerprint from '../utils/deviceFingerprint';

/**
 * 设备服务 - 集成设备指纹到API调用
 */
class DeviceService {
  
  static deviceFingerprint = null;

  /**
   * 初始化设备服务
   */
  static async initialize() {
    try {
      this.deviceFingerprint = await DeviceFingerprint.getCachedFingerprint();
      console.log('设备指纹初始化完成:', this.deviceFingerprint);
      return this.deviceFingerprint;
    } catch (error) {
      console.error('设备服务初始化失败:', error);
      this.deviceFingerprint = DeviceFingerprint.generateFallbackId();
      return this.deviceFingerprint;
    }
  }

  /**
   * 获取当前设备指纹
   * @returns {Promise<string>} 设备指纹
   */
  static async getFingerprint() {
    if (this.deviceFingerprint) {
      return this.deviceFingerprint;
    }
    return await this.initialize();
  }

  /**
   * 为API请求添加设备指纹头信息
   * @param {Object} config Axios请求配置
   * @returns {Promise<Object>} 包含设备指纹头的配置
   */
  static async withDeviceHeaders(config = {}) {
    try {
      const fingerprint = await this.getFingerprint();
      
      const headers = {
        ...config.headers,
        'X-Device-Fingerprint': fingerprint,
        'X-Device-Platform': DeviceFingerprint.getUserAgent(),
        'X-Device-Timestamp': Date.now().toString()
      };

      return {
        ...config,
        headers
      };
    } catch (error) {
      console.error('添加设备头信息失败:', error);
      return config;
    }
  }

  /**
   * 获取完整的设备信息
   * @returns {Promise<Object>} 设备信息对象
   */
  static async getDeviceInfo() {
    try {
      const fingerprint = await this.getFingerprint();
      const features = await DeviceFingerprint.collectDeviceFeatures();
      
      return {
        fingerprint,
        ...features,
        isValid: DeviceFingerprint.isValidFingerprint(fingerprint)
      };
    } catch (error) {
      console.error('获取设备信息失败:', error);
      return {
        fingerprint: DeviceFingerprint.generateFallbackId(),
        isValid: false,
        error: error.message
      };
    }
  }

  /**
   * 验证设备指纹有效性
   * @returns {Promise<boolean>} 是否有效
   */
  static async validateFingerprint() {
    const fingerprint = await this.getFingerprint();
    return DeviceFingerprint.isValidFingerprint(fingerprint);
  }

  /**
   * 重置设备指纹（用于测试或重新生成）
   */
  static async resetFingerprint() {
    this.deviceFingerprint = null;
    // 这里可以清除AsyncStorage中的缓存
    // await AsyncStorage.removeItem('device_fingerprint');
    return await this.initialize();
  }

  /**
   * 获取设备统计信息
   * @returns {Object} 设备统计信息
   */
  static getDeviceStats() {
    return {
      platform: DeviceFingerprint.getUserAgent(),
      screen: {
        width: DeviceFingerprint.screen?.width,
        height: DeviceFingerprint.screen?.height,
        scale: DeviceFingerprint.screen?.scale
      },
      timestamp: Date.now()
    };
  }
}

// 自动初始化
export default DeviceService;