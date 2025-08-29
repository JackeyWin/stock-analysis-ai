import React, { useState, useEffect } from 'react';
import {
  View,
  ScrollView,
  TouchableOpacity,
  Alert,
  RefreshControl,
  StyleSheet,
  Modal,
  Text,
} from 'react-native';
import {
  Card,
  Title,
  Paragraph,
  Button,
  Chip,
  ActivityIndicator,
  FAB,
  Divider,
} from 'react-native-paper';
import { Ionicons } from '@expo/vector-icons';
import ApiService from '../services/ApiService';

const MonitoringListScreen = ({ navigation }) => {
  const [monitoringJobs, setMonitoringJobs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [expandedStocks, setExpandedStocks] = useState(new Set());
  
  // 盯盘记录模态框状态
  const [showRecordsModal, setShowRecordsModal] = useState(false);
  const [selectedStockCode, setSelectedStockCode] = useState('');
  const [records, setRecords] = useState([]);
  const [recordsLoading, setRecordsLoading] = useState(false);
  const [expandedRecords, setExpandedRecords] = useState(new Set());
  const [recordsTimer, setRecordsTimer] = useState(null);
  
  // 停止盯盘确认对话框状态
  const [showStopConfirm, setShowStopConfirm] = useState(false);
  const [jobToStop, setJobToStop] = useState(null);
  
  // 清理所有盯盘任务状态
  const [cleanupLoading, setCleanupLoading] = useState(false);

  useEffect(() => {
    loadMonitoringJobs();
    
    // 组件卸载时清理定时器
    return () => {
      if (recordsTimer) {
        clearInterval(recordsTimer);
      }
    };
  }, []);

  const loadMonitoringJobs = async () => {
    try {
      setLoading(true);
      // 获取所有正在盯盘的股票
      const jobs = await ApiService.getAllMonitoringJobs();
      setMonitoringJobs(jobs || []);
    } catch (error) {
      console.error('加载盯盘任务失败:', error);
      Alert.alert('错误', '加载盯盘任务失败');
    } finally {
      setLoading(false);
    }
  };

  const onRefresh = async () => {
    setRefreshing(true);
    await loadMonitoringJobs();
    setRefreshing(false);
  };

  // 显示停止盯盘确认对话框
  const showStopConfirmDialog = (job) => {
    setJobToStop(job);
    setShowStopConfirm(true);
  };

  // 隐藏停止盯盘确认对话框
  const hideStopConfirmDialog = () => {
    setShowStopConfirm(false);
    setJobToStop(null);
  };

  // 确认停止盯盘
  const confirmStopMonitoring = async () => {
    if (jobToStop) {
      try {
        console.log('🛑 开始停止盯盘，任务ID:', jobToStop.jobId);
        await ApiService.stopMonitoring(jobToStop.jobId);
        console.log('✅ 停止盯盘成功');
        Alert.alert('成功', '已停止盯盘');
        loadMonitoringJobs(); // 重新加载列表
      } catch (error) {
        console.error('停止盯盘失败:', error);
        Alert.alert('错误', '停止盯盘失败');
      }
    }
    hideStopConfirmDialog();
  };

  // 清理所有盯盘任务
  const handleCleanupAllMonitoring = async () => {
    Alert.alert(
      '确认清理',
      '此操作将停止所有正在运行的盯盘任务，确定要继续吗？',
      [
        { text: '取消', style: 'cancel' },
        {
          text: '确定',
          style: 'destructive',
          onPress: async () => {
            setCleanupLoading(true);
            try {
              const resp = await ApiService.cleanupAllMonitoringJobs();
              if (resp?.success) {
                Alert.alert('清理完成', '所有盯盘任务已停止');
                loadMonitoringJobs(); // 重新加载列表
              } else {
                Alert.alert('清理失败', resp?.message || '请稍后重试');
              }
            } catch (e) {
              console.error('清理所有盯盘任务失败:', e);
              Alert.alert('网络错误', e?.message || '请稍后重试');
            } finally {
              setCleanupLoading(false);
            }
          }
        }
      ]
    );
  };

  const handleStopMonitoring = async (jobId) => {
    // 直接调用确认对话框
    const job = monitoringJobs.find(j => j.jobId === jobId);
    if (job) {
      showStopConfirmDialog(job);
    }
  };

  const handleViewRecords = async (stockCode) => {
    setSelectedStockCode(stockCode);
    setShowRecordsModal(true);
    await loadRecords(stockCode);
    
    // 启动轮询
    const timer = setInterval(async () => {
      await loadRecords(stockCode);
    }, 60000); // 每分钟刷新一次
    
    setRecordsTimer(timer);
  };

  const loadRecords = async (stockCode) => {
    try {
      setRecordsLoading(true);
      const response = await ApiService.getTodayMonitoringRecords(stockCode);
      // 后端返回的是 {success: true, data: [...]} 格式
      if (response && response.success && Array.isArray(response.data)) {
        setRecords(response.data);
      } else {
        console.warn('盯盘记录数据格式异常:', response);
        setRecords([]);
      }
    } catch (error) {
      console.error('加载盯盘记录失败:', error);
      setRecords([]);
    } finally {
      setRecordsLoading(false);
    }
  };

  const toggleRecordsExpanded = (recordId) => {
    const newExpanded = new Set(expandedRecords);
    if (newExpanded.has(recordId)) {
      newExpanded.delete(recordId);
    } else {
      newExpanded.add(recordId);
    }
    setExpandedRecords(newExpanded);
  };

  const isT0Advice = (content) => {
    return (
      content.includes('当前建议') ||
      content.includes('挂单价格') ||
      content.includes('操作理由') ||
      content.includes('风险控制') ||
      content.includes('置信度')
    );
  };

  const getConfidenceInfo = (content) => {
    const confidenceMatch = content.match(/【置信度】\s*(高|中|低)/);
    if (confidenceMatch) {
      const confidence = confidenceMatch[1];
      const confidenceColor = confidence === '高' ? '#d32f2f' : confidence === '中' ? '#f57c00' : '#388e3c';
      const confidenceBg = confidence === '高' ? '#ffebee' : confidence === '中' ? '#fff3e0' : '#e8f5e8';
      
      return { confidence, confidenceColor, confidenceBg };
    }
    return null;
  };

  const formatTime = (timestamp) => {
    const date = new Date(timestamp);
    return date.toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const toggleExpanded = (stockCode) => {
    const newExpanded = new Set(expandedStocks);
    if (newExpanded.has(stockCode)) {
      newExpanded.delete(stockCode);
    } else {
      newExpanded.add(stockCode);
    }
    setExpandedStocks(newExpanded);
  };

  const getStatusColor = (status) => {
    switch (status) {
      case 'running':
        return '#4caf50';
      case 'stopped':
        return '#f44336';
      case 'error':
        return '#ff9800';
      default:
        return '#9e9e9e';
    }
  };

  const getStatusText = (status) => {
    switch (status) {
      case 'running':
        return '运行中';
      case 'stopped':
        return '已停止';
      case 'error':
        return '错误';
      default:
        return '未知';
    }
  };

  const getIntervalText = (intervalMinutes) => {
    switch (intervalMinutes) {
      case 5:
        return '5分钟';
      case 10:
        return '10分钟';
      case 30:
        return '30分钟';
      case 60:
        return '1小时';
      default:
        return `${intervalMinutes}分钟`;
    }
  };

  if (loading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#1976d2" />
        <Paragraph style={styles.loadingText}>加载中...</Paragraph>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <ScrollView
        style={styles.scrollView}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }
      >
        <View style={styles.header}>
          <Title style={styles.headerTitle}>🎯 盯盘管理</Title>
          <Paragraph style={styles.headerSubtitle}>
            共 {monitoringJobs.length} 只股票正在盯盘
          </Paragraph>
          
          {/* 清理所有盯盘任务按钮 */}
          {monitoringJobs.length > 0 && (
            <View style={styles.cleanupButtonContainer}>
              <Button
                mode="outlined"
                onPress={handleCleanupAllMonitoring}
                loading={cleanupLoading}
                icon="delete-sweep"
                style={styles.cleanupButton}
                textColor="#f44336"
              >
                清理所有盯盘任务
              </Button>
              <Paragraph style={styles.cleanupButtonText}>
                此操作将停止所有正在运行的盯盘任务
              </Paragraph>
            </View>
          )}
        </View>

        {monitoringJobs.length === 0 ? (
          <Card style={styles.emptyCard}>
            <Card.Content style={styles.emptyContent}>
              <Ionicons name="eye-off-outline" size={48} color="#9e9e9e" />
              <Title style={styles.emptyTitle}>暂无盯盘任务</Title>
              <Paragraph style={styles.emptyText}>
                您还没有启动任何盯盘任务
              </Paragraph>
              <Button
                mode="contained"
                onPress={() => navigation.navigate('Analysis')}
                style={styles.startButton}
              >
                去启动盯盘
              </Button>
            </Card.Content>
          </Card>
        ) : (
          monitoringJobs.map((job, index) => (
            <Card key={job.jobId} style={styles.jobCard}>
              <Card.Content>
                <View style={styles.jobHeader}>
                  <View style={styles.stockInfo}>
                    <Title style={styles.stockCode}>{job.stockCode}</Title>
                    <Paragraph style={styles.stockName}>
                      {job.stockName || '未知股票'}
                    </Paragraph>
                  </View>
                  <View style={styles.statusContainer}>
                    <View
                      style={[
                        styles.statusIndicator,
                        { backgroundColor: getStatusColor(job.status) },
                      ]}
                    />
                    <Paragraph style={styles.statusText}>
                      {getStatusText(job.status)}
                    </Paragraph>
                  </View>
                </View>

                <View style={styles.jobDetails}>
                  <View style={styles.detailRow}>
                    <Chip
                      icon="clock-outline"
                      style={styles.intervalChip}
                      textStyle={styles.chipText}
                    >
                      间隔: {getIntervalText(job.intervalMinutes)}
                    </Chip>
                    <Chip
                      icon="calendar-outline"
                      style={styles.dateChip}
                      textStyle={styles.chipText}
                    >
                      {new Date(job.startTime).toLocaleDateString()}
                    </Chip>
                  </View>

                  {job.lastMessage && (
                    <View style={styles.lastMessageContainer}>
                      <Paragraph style={styles.lastMessageLabel}>
                        最后消息:
                      </Paragraph>
                      <Paragraph style={styles.lastMessageText}>
                        {job.lastMessage}
                      </Paragraph>
                    </View>
                  )}

                  <View style={styles.actionButtons}>
                    <Button
                      mode="outlined"
                      onPress={() => handleViewRecords(job.stockCode)}
                      style={styles.viewButton}
                      icon="eye-outline"
                    >
                      查看记录
                    </Button>
                    <Button
                      mode="outlined"
                      onPress={() => handleStopMonitoring(job.jobId)}
                      style={styles.stopButton}
                      icon="stop-outline"
                      disabled={job.status === 'stopped'}
                    >
                      停止盯盘
                    </Button>
                  </View>
                </View>
              </Card.Content>
            </Card>
          ))
        )}
      </ScrollView>

      <FAB
        style={styles.fab}
        icon="plus"
        onPress={() => navigation.navigate('Analysis')}
        label="启动盯盘"
      />

      {/* 盯盘记录模态框 */}
      <Modal
        visible={showRecordsModal}
        animationType="slide"
        presentationStyle="pageSheet"
        onRequestClose={() => {
          // 关闭模态框时停止轮询
          if (recordsTimer) {
            clearInterval(recordsTimer);
            setRecordsTimer(null);
          }
          setShowRecordsModal(false);
        }}
      >
        <View style={styles.modalContainer}>
          {/* 模态框头部 */}
          <View style={styles.modalHeader}>
            <TouchableOpacity
              onPress={() => {
                // 关闭模态框时停止轮询
                if (recordsTimer) {
                  clearInterval(recordsTimer);
                  setRecordsTimer(null);
                }
                setShowRecordsModal(false);
              }}
              style={styles.modalBackButton}
            >
              <Ionicons name="close" size={24} color="#333" />
            </TouchableOpacity>
            <Title style={styles.modalTitle}>{selectedStockCode} 盯盘记录</Title>
            <View style={styles.modalHeaderRight} />
          </View>

          {/* 记录内容 */}
          {recordsLoading ? (
            <View style={styles.modalLoadingContainer}>
              <ActivityIndicator size="large" color="#1976d2" />
              <Paragraph style={styles.modalLoadingText}>加载中...</Paragraph>
            </View>
          ) : (
            <ScrollView style={styles.modalScrollView}>
              {records.length === 0 ? (
                <Card style={styles.modalEmptyCard}>
                  <Card.Content style={styles.modalEmptyContent}>
                    <Ionicons name="document-outline" size={48} color="#9e9e9e" />
                    <Title style={styles.modalEmptyTitle}>暂无盯盘记录</Title>
                    <Paragraph style={styles.modalEmptyText}>
                      该股票今天还没有生成盯盘记录
                    </Paragraph>
                  </Card.Content>
                </Card>
              ) : (
                records.map((record, index) => (
                  <Card key={record.id} style={styles.modalRecordCard}>
                    <Card.Content>
                      <View style={styles.modalRecordHeader}>
                        <View style={styles.modalRecordInfo}>
                          <Paragraph style={styles.modalRecordTime}>
                            {formatTime(record.createdAt)}
                          </Paragraph>
                          <Chip
                            style={styles.modalRecordNumber}
                            textStyle={styles.modalChipText}
                          >
                            #{index + 1}
                          </Chip>
                        </View>
                        <TouchableOpacity
                          onPress={() => toggleRecordsExpanded(record.id)}
                          style={styles.modalExpandButton}
                        >
                          <Ionicons
                            name={expandedRecords.has(record.id) ? 'chevron-up' : 'chevron-down'}
                            size={20}
                            color="#666"
                          />
                        </TouchableOpacity>
                      </View>

                      <Paragraph
                        style={[
                          styles.modalRecordContent,
                          expandedRecords.has(record.id) && styles.modalExpandedContent,
                        ]}
                        numberOfLines={expandedRecords.has(record.id) ? undefined : 3}
                      >
                        {record.content}
                      </Paragraph>

                      {/* T+0交易建议标识 */}
                      {isT0Advice(record.content) && (
                        <View style={styles.modalT0Container}>
                          <View style={styles.modalT0Header}>
                            <Ionicons name="flash-outline" size={16} color="#ff9800" />
                            <Paragraph style={styles.modalT0Label}>T+0交易建议</Paragraph>
                          </View>
                          
                          {getConfidenceInfo(record.content) && (
                            <View style={styles.modalConfidenceContainer}>
                              <Paragraph style={styles.modalConfidenceLabel}>置信度:</Paragraph>
                              <Chip
                                style={[
                                  styles.modalConfidenceChip,
                                  {
                                    backgroundColor: getConfidenceInfo(record.content).confidenceBg,
                                  },
                                ]}
                                textStyle={[
                                  styles.modalConfidenceText,
                                  {
                                    color: getConfidenceInfo(record.content).confidenceColor,
                                  },
                                ]}
                              >
                                {getConfidenceInfo(record.content).confidence}
                              </Chip>
                            </View>
                          )}
                        </View>
                      )}

                      {!expandedRecords.has(record.id) && (
                        <TouchableOpacity
                          onPress={() => toggleRecordsExpanded(record.id)}
                          style={styles.modalExpandText}
                        >
                          <Paragraph style={styles.modalExpandTextContent}>
                            点击展开查看完整内容
                          </Paragraph>
                        </TouchableOpacity>
                      )}
                    </Card.Content>
                  </Card>
                ))
              )}
            </ScrollView>
          )}
        </View>
      </Modal>

      {/* 停止盯盘确认对话框 */}
      {showStopConfirm && (
        <View style={styles.stopConfirmOverlay}>
          <View style={styles.stopConfirmDialog}>
            <Text style={styles.stopConfirmTitle}>确认停止盯盘</Text>
            <Text style={styles.stopConfirmMessage}>
              确定要停止盯盘 {jobToStop?.stockCode} 吗？
            </Text>
            <View style={styles.stopConfirmButtons}>
              <TouchableOpacity
                style={[styles.stopConfirmButton, styles.stopCancelButton]}
                onPress={hideStopConfirmDialog}
              >
                <Text style={styles.stopCancelButtonText}>取消</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.stopConfirmButton, styles.stopConfirmButtonStyle]}
                onPress={confirmStopMonitoring}
              >
                <Text style={styles.stopConfirmButtonText}>确定</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 16,
    color: '#666',
  },
  scrollView: {
    flex: 1,
  },
  header: {
    padding: 20,
    backgroundColor: '#ffffff',
    marginBottom: 16,
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#1976d2',
    marginBottom: 8,
  },
  headerSubtitle: {
    fontSize: 16,
    color: '#666',
  },
  cleanupButtonContainer: {
    alignItems: 'center',
    marginTop: 16,
    paddingHorizontal: 16,
  },
  cleanupButton: {
    borderColor: '#f44336',
    borderWidth: 2,
    marginBottom: 8,
  },
  cleanupButtonText: {
    fontSize: 11,
    color: '#666',
    textAlign: 'center',
    lineHeight: 14,
  },
  emptyCard: {
    margin: 16,
    elevation: 2,
  },
  emptyContent: {
    alignItems: 'center',
    paddingVertical: 40,
  },
  emptyTitle: {
    fontSize: 18,
    color: '#666',
    marginTop: 16,
    marginBottom: 8,
  },
  emptyText: {
    fontSize: 14,
    color: '#999',
    textAlign: 'center',
    marginBottom: 24,
  },
  startButton: {
    backgroundColor: '#1976d2',
  },
  jobCard: {
    margin: 16,
    marginTop: 0,
    elevation: 2,
  },
  jobHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  stockInfo: {
    flex: 1,
  },
  stockCode: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#1976d2',
    marginBottom: 4,
  },
  stockName: {
    fontSize: 14,
    color: '#666',
  },
  statusContainer: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  statusIndicator: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: 6,
  },
  statusText: {
    fontSize: 12,
    color: '#666',
    fontWeight: '500',
  },
  jobDetails: {
    gap: 12,
  },
  detailRow: {
    flexDirection: 'row',
    gap: 8,
  },
  intervalChip: {
    backgroundColor: '#e3f2fd',
  },
  dateChip: {
    backgroundColor: '#f3e5f5',
  },
  chipText: {
    fontSize: 12,
    color: '#333',
  },
  lastMessageContainer: {
    backgroundColor: '#f5f5f5',
    padding: 12,
    borderRadius: 8,
  },
  lastMessageLabel: {
    fontSize: 12,
    color: '#666',
    marginBottom: 4,
    fontWeight: '500',
  },
  lastMessageText: {
    fontSize: 12,
    color: '#333',
    fontStyle: 'italic',
  },
  actionButtons: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 8,
  },
  viewButton: {
    flex: 1,
    borderColor: '#1976d2',
  },
  stopButton: {
    flex: 1,
    borderColor: '#f44336',
  },
  fab: {
    position: 'absolute',
    margin: 16,
    right: 0,
    bottom: 0,
    backgroundColor: '#1976d2',
  },
  
  // 模态框样式
  modalContainer: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  modalHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    backgroundColor: '#ffffff',
    borderBottomWidth: 1,
    borderBottomColor: '#e0e0e0',
  },
  modalBackButton: {
    padding: 8,
    marginRight: 8,
  },
  modalTitle: {
    flex: 1,
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
  },
  modalHeaderRight: {
    width: 40,
  },
  modalLoadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalLoadingText: {
    marginTop: 16,
    color: '#666',
  },
  modalScrollView: {
    flex: 1,
  },
  modalEmptyCard: {
    margin: 16,
    elevation: 2,
  },
  modalEmptyContent: {
    alignItems: 'center',
    paddingVertical: 40,
  },
  modalEmptyTitle: {
    fontSize: 18,
    color: '#666',
    marginTop: 16,
    marginBottom: 8,
  },
  modalEmptyText: {
    fontSize: 14,
    color: '#999',
    textAlign: 'center',
  },
  modalRecordCard: {
    margin: 16,
    marginTop: 0,
    elevation: 2,
  },
  modalRecordHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  modalRecordInfo: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  modalRecordTime: {
    fontSize: 12,
    color: '#666',
    fontWeight: '500',
  },
  modalRecordNumber: {
    backgroundColor: '#e3f2fd',
    height: 24,
  },
  modalChipText: {
    fontSize: 10,
    color: '#1976d2',
    fontWeight: 'bold',
  },
  modalExpandButton: {
    padding: 4,
  },
  modalRecordContent: {
    fontSize: 14,
    lineHeight: 20,
    color: '#333',
    marginBottom: 12,
  },
  modalExpandedContent: {
    marginBottom: 8,
  },
  modalT0Container: {
    backgroundColor: '#fff3e0',
    padding: 12,
    borderRadius: 8,
    borderLeftWidth: 3,
    borderLeftColor: '#ff9800',
    marginBottom: 12,
  },
  modalT0Header: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  modalT0Label: {
    fontSize: 12,
    color: '#e65100',
    fontWeight: 'bold',
    marginLeft: 4,
  },
  modalConfidenceContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  modalConfidenceLabel: {
    fontSize: 11,
    color: '#e65100',
  },
  modalConfidenceChip: {
    height: 20,
  },
  modalConfidenceText: {
    fontSize: 10,
    fontWeight: 'bold',
  },
  modalExpandText: {
    alignItems: 'center',
    paddingVertical: 8,
  },
  modalExpandTextContent: {
    fontSize: 12,
    color: '#1976d2',
    fontStyle: 'italic',
  },
  
  // 停止盯盘确认对话框样式
  stopConfirmOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'flex-start',
    alignItems: 'center',
    zIndex: 1000,
  },
  stopConfirmDialog: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 24,
    marginTop: 100,
    marginHorizontal: 20,
    minWidth: 280,
    maxWidth: 320,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 8,
    elevation: 5,
  },
  stopConfirmTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1C1C1E',
    textAlign: 'center',
    marginBottom: 12,
  },
  stopConfirmMessage: {
    fontSize: 16,
    color: '#666666',
    textAlign: 'center',
    marginBottom: 24,
    lineHeight: 22,
  },
  stopConfirmButtons: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 12,
  },
  stopConfirmButton: {
    flex: 1,
    paddingVertical: 12,
    paddingHorizontal: 20,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stopCancelButton: {
    backgroundColor: '#F2F2F7',
    borderWidth: 1,
    borderColor: '#E5E5EA',
  },
  stopCancelButtonText: {
    fontSize: 16,
    fontWeight: '500',
    color: '#666666',
  },
  stopConfirmButtonStyle: {
    backgroundColor: '#f44336',
    borderWidth: 1,
    borderColor: '#f44336',
  },
  stopConfirmButtonText: {
    fontSize: 16,
    fontWeight: '500',
    color: '#FFFFFF',
  },
});

export default MonitoringListScreen;
