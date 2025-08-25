import axios from 'axios';

// Web绔娇鐢ㄥ悓婧愶紝鍘熺敓绔娇鐢ㄥ浐瀹氬煙鍚嶏紝閬垮厤iOS涓奾ttp/https璺ㄦ簮闂
const isWeb = typeof window !== 'undefined' && typeof document !== 'undefined';
const API_BASE_URL = isWeb ? (window.location?.origin || 'https://tickermind.qzz.io') : 'https://tickermind.qzz.io';

// Create a shared axios instance for the recommendation API
const recommendationClient = axios.create({
  baseURL: `${API_BASE_URL}/api/recommendations`,
  timeout: 300000,
      headers: {
        'Content-Type': 'application/json',
      },
      responseType: 'json',
  responseEncoding: 'utf8',
    });
    
// Attach interceptors once
recommendationClient.interceptors.request.use(
      (config) => {
    console.log(`馃殌 鎺ㄨ崘API璇锋眰: ${config.method?.toUpperCase()} ${config.url}`);
        return config;
      },
      (error) => {
    console.error('鉂?鎺ㄨ崘API璇锋眰閿欒:', error);
        return Promise.reject(error);
      }
    );

recommendationClient.interceptors.response.use(
      (response) => {
    console.log(`鉁?鎺ㄨ崘API鍝嶅簲: ${response.config.url} - ${response.status}`);
        return response;
      },
      (error) => {
    console.error('鉂?鎺ㄨ崘API鍝嶅簲閿欒:', error.response?.data || error.message);
    return Promise.reject(RecommendationService.handleError(error));
      }
    );

class RecommendationService {
  static handleError(error) {
    if (error.response) {
      const { status, data } = error.response;
      return {
        type: 'SERVER_ERROR',
        status,
        message: data?.message || '鏈嶅姟鍣ㄩ敊璇?',
        code: data?.code || 'UNKNOWN_ERROR',
      };
    }
    if (error.request) {
      return {
        type: 'NETWORK_ERROR',
        message: '缃戠粶杩炴帴澶辫触锛岃妫€鏌ョ綉缁滆缃?',
        code: 'NETWORK_ERROR',
      };
    }
      return {
        type: 'UNKNOWN_ERROR',
      message: error.message || '鏈煡閿欒',
        code: 'UNKNOWN_ERROR',
      };
    }

  /**
   * 鑾峰彇浠婃棩鎺ㄨ崘
   */
  static async getTodayRecommendation() {
    const response = await recommendationClient.get('/today');
      return response.data;
  }

  /**
   * 鑾峰彇浠婃棩鎺ㄨ崘鎽樿锛堢敤浜庨椤靛睍绀猴級
   */
  static async getTodayRecommendations() {
    const response = await recommendationClient.get('/summary');
      return response.data;
  }

  /**
   * 鑾峰彇鎺ㄨ崘鎽樿锛堢敤浜庨椤靛睍绀猴級
   */
  static async getRecommendationSummary() {
    const response = await recommendationClient.get('/summary');
      return response.data;
  }

  /**
   * 鑾峰彇鎺ㄨ崘璇︽儏
   */
  static async getRecommendationDetail(stockCode) {
    const response = await recommendationClient.get(`/detail/${stockCode}`);
      return response.data;
  }

  /**
   * 鑾峰彇鎺ㄨ崘鍘嗗彶
   */
  static async getRecommendationHistory(days = 7) {
    const response = await recommendationClient.get(`/history?days=${days}`);
      return response.data;
  }

  /**
   * 鎵嬪姩鍒锋柊鎺ㄨ崘
   */
  static async refreshRecommendation() {
    const response = await recommendationClient.post('/refresh');
      return response.data;
  }

  /**
   * 鍒锋柊鎺ㄨ崘
   */
  static async refreshRecommendations() {
    const response = await recommendationClient.post('/refresh');
      return response.data;
  }

  /**
   * 鑾峰彇鎺ㄨ崘鐘舵€?
   */
  static async getRecommendationStatus() {
    const response = await recommendationClient.get('/status');
    return response.data;
  }

  /**
   * 获取可用的推荐日期列表
   */
  static async getAvailableDates() {
    const response = await recommendationClient.get('/dates');
      return response.data;
  }

  /**
   * 根据日期获取推荐数据
   */
  static async getRecommendationByDate(date) {
    const response = await recommendationClient.get(`/by-date/${date}`);
    return response.data;
  }
}

export default RecommendationService;