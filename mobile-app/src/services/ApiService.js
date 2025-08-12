import axios from 'axios';
import Constants from 'expo-constants';

// Web端使用同源，原生端使用固定域名，避免iOS上http/https跨源问题
const isWeb = typeof window !== 'undefined' && typeof document !== 'undefined';
const API_BASE_URL = isWeb ? (window.location?.origin || 'https://tickermind.qzz.io') : 'https://tickermind.qzz.io';

class ApiService {
  constructor() {
    console.log('🚀 ApiService初始化，使用URL:', API_BASE_URL);
    this.client = axios.create({
      baseURL: API_BASE_URL,
      timeout: 300000, // 5分钟 = 300秒
      headers: {
        'Content-Type': 'application/json',
      },
    });
    
    // 轮询限制管理
    this.pollingQueue = [];
    this.isProcessingQueue = false;
    this.lastPollTime = 0;
    this.minPollInterval = 1000; // 全局最小轮询间隔1秒（因为每只股票有自己的限制）

    // 请求拦截器
    this.client.interceptors.request.use(
      (config) => {
        console.log(`🚀 API请求: ${config.method?.toUpperCase()} ${config.url}`);
        return config;
      },
      (error) => {
        console.error('❌ API请求错误:', error);
        return Promise.reject(error);
      }
    );

    // 响应拦截器
    this.client.interceptors.response.use(
      (response) => {
        console.log(`✅ API响应: ${response.config.url} - ${response.status}`);
        return response;
      },
      (error) => {
        console.error('❌ API响应错误:', error.response?.data || error.message);
        return Promise.reject(this.handleError(error));
      }
    );
  }

  // 错误处理
  handleError(error) {
    if (error.response) {
      // 服务器响应错误
      const { status, data } = error.response;
      return {
        type: 'SERVER_ERROR',
        status,
        message: data.message || '服务器错误',
        code: data.code || 'UNKNOWN_ERROR',
      };
    } else if (error.request) {
      // 网络错误
      return {
        type: 'NETWORK_ERROR',
        message: '网络连接失败，请检查网络设置',
        code: 'NETWORK_ERROR',
      };
    } else {
      // 其他错误
      return {
        type: 'UNKNOWN_ERROR',
        message: error.message || '未知错误',
        code: 'UNKNOWN_ERROR',
      };
    }
  }

  // 启动异步分析任务（不等待完成）
  async startAnalysisTask(stockCode, options = {}) {
    try {
      const response = await this.client.post('/api/mobile/stock/analyze-async', {
        stockCode,
        ...options,
      });
      
      const taskId = response.data.taskId;
      console.log(`📊 启动异步分析任务: ${taskId}`);
      
      return {
        taskId,
        stockCode,
        status: 'pending',
        progress: 0,
        message: '任务已启动',
        startTime: new Date(),
        ...response.data
      };
    } catch (error) {
      // 如果异步接口不存在，回退到同步接口
      if (error.response?.status === 404) {
        console.log('🔄 异步接口不存在，使用同步接口');
        return await this.analyzeStockSync(stockCode, options);
      }
      throw error;
    }
  }

  // 获取分析任务状态（带轮询限制）
  async getAnalysisStatus(taskId) {
    return new Promise((resolve, reject) => {
      const pollRequest = async () => {
        try {
          const response = await this.client.get(`/api/mobile/stock/analyze-status/${taskId}`);
          resolve(response.data);
        } catch (error) {
          reject(error);
        }
      };

      // 检查是否需要等待
      const now = Date.now();
      const timeSinceLastPoll = now - this.lastPollTime;
      
      if (timeSinceLastPoll < this.minPollInterval) {
        // 需要等待
        const waitTime = this.minPollInterval - timeSinceLastPoll;
        setTimeout(() => {
          this.lastPollTime = Date.now();
          pollRequest();
        }, waitTime);
      } else {
        // 可以立即执行
        this.lastPollTime = now;
        pollRequest();
      }
    });
  }

  // 股票分析 - 异步处理（保持兼容性）
  async analyzeStock(stockCode, options = {}, onProgress = null) {
    try {
      const taskInfo = await this.startAnalysisTask(stockCode, options);
      return await this.pollAnalysisResult(taskInfo.taskId, 60, 5000, onProgress);
    } catch (error) {
      throw error;
    }
  }

  // 同步股票分析（备用）
  async analyzeStockSync(stockCode, options = {}) {
    try {
      const response = await this.client.post('/api/mobile/stock/analyze', {
        stockCode,
        ...options,
      });
      return response.data;
    } catch (error) {
      throw error;
    }
  }

  // 轮询分析结果
  async pollAnalysisResult(taskId, maxAttempts = 60, interval = 5000, onProgress = null) {
    let currentInterval = interval;
    let consecutiveErrors = 0;
    
    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        console.log(`🔍 检查分析进度 (${attempt}/${maxAttempts})`);
        
        const response = await this.client.get(`/api/mobile/stock/analyze-status/${taskId}`);
        const { status, progress, result, error, message } = response.data;
        
        // 重置错误计数
        consecutiveErrors = 0;
        
        // 调用进度回调
        if (onProgress) {
          onProgress({
            status,
            progress: progress || Math.min((attempt / maxAttempts) * 100, 95),
            message: message || this.getStatusMessage(status, progress)
          });
        }
        
        if (status === 'completed') {
          console.log('✅ 分析完成');
          if (onProgress) {
            onProgress({ status: 'completed', progress: 100, message: '分析完成' });
          }
          return result;
        } else if (status === 'failed') {
          console.error('❌ 分析失败:', error);
          throw new Error(error || '分析失败');
        } else if (status === 'running') {
          console.log(`⏳ 分析进行中... ${progress || 0}%`);
          // 等待指定间隔后继续轮询
          await new Promise(resolve => setTimeout(resolve, currentInterval));
        } else {
          console.log(`⏳ 任务状态: ${status}`);
          await new Promise(resolve => setTimeout(resolve, currentInterval));
        }
      } catch (error) {
        consecutiveErrors++;
        
        // 处理速率限制错误
        if (error.response?.status === 429 || error.message?.includes('RATE_LIMIT_EXCEEDED')) {
          console.warn(`⚠️ 速率限制，等待更长时间 (${consecutiveErrors}次连续错误)`);
          // 指数退避策略
          currentInterval = Math.min(interval * Math.pow(2, consecutiveErrors), 30000); // 最大30秒
          await new Promise(resolve => setTimeout(resolve, currentInterval));
          continue;
        }
        
        if (error.response?.status === 404) {
          console.log('❌ 任务不存在或已过期');
          throw new Error('分析任务不存在或已过期');
        }
        
        if (attempt === maxAttempts) {
          console.error('❌ 轮询超时');
          throw new Error('分析超时，请稍后重试');
        }
        
        console.warn(`⚠️ 轮询错误 (${attempt}/${maxAttempts}):`, error.message);
        // 增加错误时的等待时间
        currentInterval = Math.min(currentInterval * 1.5, 15000); // 最大15秒
        await new Promise(resolve => setTimeout(resolve, currentInterval));
      }
    }
    
    throw new Error('分析超时，请稍后重试');
  }

  // 获取状态消息
  getStatusMessage(status, progress) {
    switch (status) {
      case 'pending':
        return '等待开始分析...';
      case 'running':
        if (progress < 20) return '正在获取股票数据...';
        if (progress < 40) return '正在计算技术指标...';
        if (progress < 60) return '正在分析基本面...';
        if (progress < 80) return '正在生成AI分析...';
        if (progress < 95) return '正在整理分析结果...';
        return '即将完成...';
      case 'completed':
        return '分析完成';
      case 'failed':
        return '分析失败';
      default:
        return '处理中...';
    }
  }

  // 简单股票分析 (GET)
  async analyzeStockSimple(stockCode) {
    try {
      const response = await this.client.get(`/api/mobile/stock/analyze/${stockCode}`);
      return response.data;
    } catch (error) {
      throw error;
    }
  }

  // 快速分析
  async quickAnalyze(stockCode) {
    try {
      const response = await this.client.post('/api/mobile/stock/quick-analyze', {
        stockCode,
      });
      return response.data;
    } catch (error) {
      throw error;
    }
  }

  // 风险评估
  async assessRisk(stockCode) {
    try {
      const response = await this.client.post('/api/mobile/stock/risk-assessment', {
        stockCode,
      });
      return response.data;
    } catch (error) {
      throw error;
    }
  }

  // 获取热门股票列表
  async getPopularStocks() {
    try {
      const response = await this.client.get('/api/mobile/stocks/popular');
      return response.data;
    } catch (error) {
      throw error;
    }
  }

  // 健康检查
  async healthCheck() {
    try {
      const response = await this.client.get('/health');
      return response.data;
    } catch (error) {
      throw error;
    }
  }
}

// 导出单例实例
export default new ApiService();
