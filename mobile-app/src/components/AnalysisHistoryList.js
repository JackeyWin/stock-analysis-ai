import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Alert,
  RefreshControl,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import AnalysisHistoryService from '../services/AnalysisHistoryService';

const AnalysisHistoryList = ({ onSelectHistory, onRefresh }) => {
  const [historyList, setHistoryList] = useState([]);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    loadHistory();
  }, []);

  const loadHistory = async () => {
    try {
      const history = await AnalysisHistoryService.getAnalysisHistory();
      setHistoryList(history);
    } catch (error) {
      console.error('加载分析历史失败:', error);
    }
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    await loadHistory();
    setRefreshing(false);
    if (onRefresh) {
      onRefresh();
    }
  };

  const handleDeleteRecord = async (recordId) => {
    Alert.alert(
      '确认删除',
      '确定要删除这条分析记录吗？',
      [
        { text: '取消', style: 'cancel' },
        {
          text: '删除',
          style: 'destructive',
          onPress: async () => {
            try {
              const success = await AnalysisHistoryService.deleteAnalysisRecord(recordId);
              if (success) {
                await loadHistory();
              }
            } catch (error) {
              console.error('删除记录失败:', error);
            }
          },
        },
      ]
    );
  };

  const handleClearAllHistory = () => {
    Alert.alert(
      '确认清空',
      '确定要清空所有分析历史吗？此操作不可恢复。',
      [
        { text: '取消', style: 'cancel' },
        {
          text: '清空',
          style: 'destructive',
          onPress: async () => {
            try {
              const success = await AnalysisHistoryService.clearAllHistory();
              if (success) {
                setHistoryList([]);
              }
            } catch (error) {
              console.error('清空历史失败:', error);
            }
          },
        },
      ]
    );
  };

  const renderHistoryItem = ({ item, index }) => (
    <TouchableOpacity
      style={styles.historyItem}
      onPress={() => onSelectHistory && onSelectHistory(item)}
    >
      <View style={styles.historyItemHeader}>
        <View style={styles.stockInfo}>
          <Text style={styles.stockCode}>{item.stockCode}</Text>
          <Text style={styles.stockName}>{item.stockName || '未知股票'}</Text>
        </View>
        <View style={styles.timeInfo}>
          <Text style={styles.timestamp}>
            {AnalysisHistoryService.formatTimestamp(item.timestamp)}
          </Text>
          <TouchableOpacity
            style={styles.deleteButton}
            onPress={() => handleDeleteRecord(item.id)}
          >
            <Ionicons name="trash-outline" size={16} color="#FF3B30" />
          </TouchableOpacity>
        </View>
      </View>
      
      {item.summary && (
        <Text style={styles.summary} numberOfLines={2}>
          {item.summary}
        </Text>
      )}
      
      {item.recommendation && (
        <View style={styles.recommendationContainer}>
          <Ionicons 
            name="trending-up-outline" 
            size={16} 
            color="#007AFF" 
          />
          <Text style={styles.recommendation}>
            {item.recommendation.action || '建议'} - {item.recommendation.reason || '无详细说明'}
          </Text>
        </View>
      )}
    </TouchableOpacity>
  );

  const renderEmptyState = () => (
    <View style={styles.emptyState}>
      <Ionicons name="document-outline" size={48} color="#C7C7CC" />
      <Text style={styles.emptyStateTitle}>暂无分析历史</Text>
      <Text style={styles.emptyStateSubtitle}>
        开始分析股票后，记录将显示在这里
      </Text>
    </View>
  );

  const renderHeader = () => (
    <View style={styles.header}>
      <View style={styles.headerLeft}>
        <Text style={styles.headerTitle}>分析历史</Text>
        <Text style={styles.headerSubtitle}>
          最近一周 · 最多10条记录
        </Text>
      </View>
      {historyList.length > 0 && (
        <TouchableOpacity
          style={styles.clearButton}
          onPress={handleClearAllHistory}
        >
          <Text style={styles.clearButtonText}>清空</Text>
        </TouchableOpacity>
      )}
    </View>
  );

  return (
    <View style={styles.container}>
      {renderHeader()}
      
      <FlatList
        data={historyList}
        renderItem={renderHistoryItem}
        keyExtractor={(item) => item.id}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={handleRefresh}
            colors={['#007AFF']}
            tintColor="#007AFF"
          />
        }
        ListEmptyComponent={renderEmptyState}
        contentContainerStyle={styles.listContent}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F2F2F7',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    backgroundColor: '#FFFFFF',
    borderBottomWidth: 1,
    borderBottomColor: '#E5E5EA',
  },
  headerLeft: {
    flex: 1,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  headerSubtitle: {
    fontSize: 12,
    color: '#8E8E93',
    marginTop: 2,
  },
  clearButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    backgroundColor: '#FF3B30',
    borderRadius: 6,
  },
  clearButtonText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '600',
  },
  listContent: {
    flexGrow: 1,
    padding: 16,
  },
  historyItem: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  historyItemHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 8,
  },
  stockInfo: {
    flex: 1,
  },
  stockCode: {
    fontSize: 18,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  stockName: {
    fontSize: 14,
    color: '#8E8E93',
    marginTop: 2,
  },
  timeInfo: {
    alignItems: 'flex-end',
  },
  timestamp: {
    fontSize: 12,
    color: '#8E8E93',
    marginBottom: 4,
  },
  deleteButton: {
    padding: 4,
  },
  summary: {
    fontSize: 14,
    color: '#3A3A3C',
    lineHeight: 20,
    marginBottom: 8,
  },
  recommendationContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F0F8FF',
    padding: 8,
    borderRadius: 6,
  },
  recommendation: {
    fontSize: 12,
    color: '#007AFF',
    marginLeft: 6,
    flex: 1,
  },
  emptyState: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 60,
  },
  emptyStateTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#8E8E93',
    marginTop: 16,
    marginBottom: 8,
  },
  emptyStateSubtitle: {
    fontSize: 14,
    color: '#C7C7CC',
    textAlign: 'center',
    lineHeight: 20,
  },
});

export default AnalysisHistoryList;
