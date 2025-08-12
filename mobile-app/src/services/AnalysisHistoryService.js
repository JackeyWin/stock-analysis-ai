import AsyncStorage from '@react-native-async-storage/async-storage';

const HISTORY_STORAGE_KEY = 'stock_analysis_history';
const MAX_HISTORY_DAYS = 7; // 保留最近一周的记录
const MAX_HISTORY_COUNT = 10; // 最多显示10条记录

class AnalysisHistoryService {
  /**
   * 添加分析记录
   * @param {Object} analysisRecord - 分析记录对象
   */
  static async addAnalysisRecord(analysisRecord) {
    try {
      // 尝试从不同位置获取股票名称
      let stockName = '';
      if (analysisRecord.stockBasic?.stockName) {
        stockName = analysisRecord.stockBasic.stockName;
      } else if (analysisRecord.stockName) {
        stockName = analysisRecord.stockName;
      } else if (analysisRecord.result?.stockBasic?.stockName) {
        stockName = analysisRecord.result.stockBasic.stockName;
      }
      
      const record = {
        ...analysisRecord,
        stockName: stockName, // 确保有股票名称
        id: Date.now().toString(), // 使用时间戳作为唯一ID
        timestamp: new Date().toISOString(),
        createdAt: Date.now()
      };

      // 获取现有历史记录
      const existingHistory = await this.getAnalysisHistory();
      
      // 检查是否已存在相同股票代码的记录
      const existingIndex = existingHistory.findIndex(
        item => item.stockCode === record.stockCode
      );

      if (existingIndex !== -1) {
        // 如果存在，更新现有记录
        existingHistory[existingIndex] = record;
      } else {
        // 如果不存在，添加新记录
        existingHistory.unshift(record);
      }

      // 清理过期记录并限制数量
      const cleanedHistory = this.cleanAndLimitHistory(existingHistory);
      
      // 保存到本地存储
      await AsyncStorage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(cleanedHistory));
      
      return true;
    } catch (error) {
      console.error('添加分析记录失败:', error);
      return false;
    }
  }

  /**
   * 获取分析历史记录
   * @returns {Array} 分析历史记录数组
   */
  static async getAnalysisHistory() {
    try {
      const historyJson = await AsyncStorage.getItem(HISTORY_STORAGE_KEY);
      if (historyJson) {
        const history = JSON.parse(historyJson);
        // 清理过期记录
        const cleanedHistory = this.cleanAndLimitHistory(history);
        // 如果清理后有变化，更新存储
        if (cleanedHistory.length !== history.length) {
          await AsyncStorage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(cleanedHistory));
        }
        return cleanedHistory;
      }
      return [];
    } catch (error) {
      console.error('获取分析历史失败:', error);
      return [];
    }
  }

  /**
   * 清理过期记录并限制数量
   * @param {Array} history - 原始历史记录数组
   * @returns {Array} 清理后的历史记录数组
   */
  static cleanAndLimitHistory(history) {
    if (!Array.isArray(history)) return [];

    const now = Date.now();
    const maxAge = MAX_HISTORY_DAYS * 24 * 60 * 60 * 1000; // 转换为毫秒

    // 过滤掉过期的记录
    const validHistory = history.filter(record => {
      return record.createdAt && (now - record.createdAt) < maxAge;
    });

    // 去重：保留每个股票代码的最新记录
    const uniqueHistory = [];
    const stockCodeMap = new Map();

    validHistory.forEach(record => {
      if (record.stockCode) {
        const existing = stockCodeMap.get(record.stockCode);
        if (!existing || record.createdAt > existing.createdAt) {
          stockCodeMap.set(record.stockCode, record);
        }
      }
    });

    // 转换为数组并按时间排序（最新的在前）
    const sortedHistory = Array.from(stockCodeMap.values())
      .sort((a, b) => b.createdAt - a.createdAt)
      .slice(0, MAX_HISTORY_COUNT);

    return sortedHistory;
  }

  /**
   * 删除指定的分析记录
   * @param {string} recordId - 记录ID
   */
  static async deleteAnalysisRecord(recordId) {
    try {
      const history = await this.getAnalysisHistory();
      const filteredHistory = history.filter(record => record.id !== recordId);
      await AsyncStorage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(filteredHistory));
      return true;
    } catch (error) {
      console.error('删除分析记录失败:', error);
      return false;
    }
  }

  /**
   * 清空所有分析历史
   */
  static async clearAllHistory() {
    try {
      await AsyncStorage.removeItem(HISTORY_STORAGE_KEY);
      return true;
    } catch (error) {
      console.error('清空分析历史失败:', error);
      return false;
    }
  }

  /**
   * 获取指定股票的最新分析记录
   * @param {string} stockCode - 股票代码
   * @returns {Object|null} 最新的分析记录或null
   */
  static async getLatestAnalysisByStock(stockCode) {
    try {
      const history = await this.getAnalysisHistory();
      const stockRecord = history.find(record => record.stockCode === stockCode);
      return stockRecord || null;
    } catch (error) {
      console.error('获取股票最新分析记录失败:', error);
      return null;
    }
  }

  /**
   * 格式化时间显示
   * @param {string} timestamp - ISO时间字符串
   * @returns {string} 格式化的时间字符串
   */
  static formatTimestamp(timestamp) {
    try {
      const date = new Date(timestamp);
      const now = new Date();
      const diffMs = now - date;
      const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
      const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
      const diffMinutes = Math.floor(diffMs / (1000 * 60));

      if (diffDays > 0) {
        return `${diffDays}天前`;
      } else if (diffHours > 0) {
        return `${diffHours}小时前`;
      } else if (diffMinutes > 0) {
        return `${diffMinutes}分钟前`;
      } else {
        return '刚刚';
      }
    } catch (error) {
      return '未知时间';
    }
  }
}

export default AnalysisHistoryService;
