import React, { useState, useRef, useEffect } from 'react';
import {
  View,
  ScrollView,
  Alert,
  Dimensions,
  Modal,
  TouchableOpacity,
  FlatList,
  Text,
} from 'react-native';
import {
  Card,
  Title,
  Paragraph,
  Button,
  TextInput,
  ActivityIndicator,
  Chip,
  SegmentedButtons,
  FAB,
  Divider,
} from 'react-native-paper';

import { theme, styles } from '../utils/theme';
import ApiService from '../services/ApiService';
import AnalysisTaskList from '../components/AnalysisTaskList';
import AnalysisResultView from '../components/AnalysisResultView';
import AnalysisHistoryService from '../services/AnalysisHistoryService';
import AnalysisHistoryList from '../components/AnalysisHistoryList';
import { Ionicons } from '@expo/vector-icons';

const { width: screenWidth } = Dimensions.get('window');

export default function AnalysisScreen({ navigation, route }) {
  const [stockCode, setStockCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [analysisResult, setAnalysisResult] = useState(null);
  
  // 新增状态管理
  const [selectedResult, setSelectedResult] = useState(null);
  const [showResultModal, setShowResultModal] = useState(false);
  // 盯盘
  const [showMonitorModal, setShowMonitorModal] = useState(false);
  const [monitorInterval, setMonitorInterval] = useState('10'); // '5' | '10' | '30' | '60'
  const [monitorJobId, setMonitorJobId] = useState(null);
  const [monitorLoading, setMonitorLoading] = useState(false);
  const [currentMonitoringStock, setCurrentMonitoringStock] = useState(null); // 当前盯盘的股票（用于结果弹窗）
  const [monitoringJobs, setMonitoringJobs] = useState([]); // 多股票盯盘：{stockCode, jobId, intervalMinutes}
  const [lastProcessedRecordId, setLastProcessedRecordId] = useState(null); // 单股模式兼容
  const [lastProcessedRecordIdByStock, setLastProcessedRecordIdByStock] = useState({}); // 多股去重：{ [stockCode]: recordId }
  const [showMonitoringIndicator, setShowMonitoringIndicator] = useState(false); // 显示监控指示器
  const recordsTimerRef = useRef(null);
  const taskListRef = useRef(null);
  const [stockCodeToAnalyze, setStockCodeToAnalyze] = useState(''); // New state
  const [recentStocks, setRecentStocks] = useState([]); // 最近分析的股票
  const [isRecentStocksCollapsed, setIsRecentStocksCollapsed] = useState(true); // 最近分析股票的折叠状态
  
  // 查看所有分析数据的状态
  const [showAllAnalyses, setShowAllAnalyses] = useState(false);
  const [allAnalyses, setAllAnalyses] = useState([]);
  const [loadingAllAnalyses, setLoadingAllAnalyses] = useState(false);
  const [allAnalysesPage, setAllAnalysesPage] = useState(0);
  const [hasMoreAnalyses, setHasMoreAnalyses] = useState(true);
  
  // 返回顶部按钮状态
  const [showScrollTop, setShowScrollTop] = useState(false);
  const scrollViewRef = useRef(null);

  // 处理滚动事件
  const handleScroll = (event) => {
    const offsetY = event.nativeEvent.contentOffset.y;
    // 当滚动超过200px时显示返回顶部按钮
    setShowScrollTop(offsetY > 200);
  };

  // 返回顶部
  const scrollToTop = () => {
    scrollViewRef.current?.scrollTo({ y: 0, animated: true });
  };

  // 移除不必要的变量

  const handleAnalysis = async () => {
    if (!stockCode.trim()) {
      Alert.alert('提示', '请输入股票代码');
      return;
    }

    const code = stockCode.trim();

    // 如果当前正在查看所有分析，则自动切换回我的分析
    if (showAllAnalyses) {
      setShowAllAnalyses(false);
    }

    // 直接调用子组件方法新增任务，保证立刻出现"分析中"卡片
    try {
      await taskListRef.current?.addTask(code);
    } catch (e) {
      console.warn('启动任务失败:', e?.message || e);
    }

    // 兼容旧逻辑：仍然传递给子组件作为兜底
    setStockCodeToAnalyze(code);
    setStockCode('');
  };

  // 处理从历史记录跳转过来的数据
  useEffect(() => {
    if (route.params?.historyData) {
      const { stockCode: historyStockCode, stockName: historyStockName } = route.params.historyData;
      setStockCode(historyStockCode);
      setAnalysisResult(route.params.historyData);
      setShowResultModal(true);
    }
  }, [route.params]);

  // 加载最近分析的股票
  useEffect(() => {
    loadRecentStocks();
  }, []);

  const loadRecentStocks = async () => {
    try {
      const history = await AnalysisHistoryService.getAnalysisHistory();
      // 提取最近分析的股票代码，去重
      const uniqueStocks = history.reduce((acc, item) => {
        if (!acc.find(stock => stock.code === item.stockCode)) {
          acc.push({
            code: item.stockCode,
            name: item.stockName
          });
        }
        return acc;
      }, []);
      setRecentStocks(uniqueStocks.slice(0, 5)); // 只显示最近5个
    } catch (error) {
      console.error('加载最近分析股票失败:', error);
    }
  };

  // 处理任务完成：自动写入分析历史
  const handleTaskComplete = async (task) => {
    try {
      console.log('任务完成，准备写入分析历史:', task?.stockCode);
      // 提取股票名称
      let stockName = '';
      if (task?.result?.stockBasic?.stockName) {
        stockName = task.result.stockBasic.stockName;
      } else if (task?.stockName) {
        stockName = task.stockName;
      } else if (task?.result?.stockName) {
        stockName = task.result.stockName;
      } else if (task?.result?.aiAnalysisResult?.stockName) {
        stockName = task.result.aiAnalysisResult.stockName;
      } else {
        stockName = `股票 ${task?.stockCode || ''}`.trim();
      }

      const historyRecord = {
        stockCode: task?.stockCode,
        stockName,
        summary: task?.result?.aiAnalysisResult?.summary || '分析完成',
        recommendation: task?.result?.aiAnalysisResult?.recommendation || null,
        result: task?.result,
        stockBasic: task?.result?.stockBasic,
        timestamp: new Date().toISOString(),
      };

      await AnalysisHistoryService.addAnalysisRecord(historyRecord);
      // 刷新最近分析 chips
      loadRecentStocks();
    } catch (error) {
      console.error('自动写入分析历史失败:', error);
    }
  };

  // 处理查看结果
  const handleViewResult = async (task) => {
    setSelectedResult(task); // 传递整个task对象而不是task.result
    setShowResultModal(true);
    
    // 尝试获取股票名称
    let stockName = '';
    if (task.result?.stockBasic?.stockName) {
      stockName = task.result.stockBasic.stockName;
    } else if (task.stockName) {
      stockName = task.stockName;
    } else if (task.result?.stockName) {
      stockName = task.result.stockName;
    } else if (task.result?.aiAnalysisResult?.stockName) {
      stockName = task.result.aiAnalysisResult.stockName;
    } else {
      stockName = `股票 ${task.stockCode}`;
    }
    
    // 保存分析结果到历史记录
    try {
      const historyRecord = {
        stockCode: task.stockCode,
        stockName: stockName,
        summary: task.result?.aiAnalysisResult?.summary || '分析完成',
        recommendation: task.result?.aiAnalysisResult?.recommendation || null,
        result: task.result,
        stockBasic: task.result?.stockBasic,
        timestamp: new Date().toISOString()
      };
      
      await AnalysisHistoryService.addAnalysisRecord(historyRecord);
      // 刷新最近分析的股票
      loadRecentStocks();
    } catch (error) {
      console.error('保存分析历史失败:', error);
    }
  };

  // 加载所有分析数据（包括他人的）
  const loadAllAnalyses = async (page = 0, append = false) => {
    if (loadingAllAnalyses) return;
    
    setLoadingAllAnalyses(true);
    try {
      // 默认每页加载10条数据
      const pageSize = 10;
      const response = await ApiService.getAllAnalysisTasks(page, pageSize);
      const newAnalyses = response.content || response;
      
      if (append) {
        setAllAnalyses(prev => [...prev, ...newAnalyses]);
      } else {
        setAllAnalyses(newAnalyses);
      }
      
      setAllAnalysesPage(page);
      // 如果返回的数据量等于页面大小，说明可能还有更多数据
      setHasMoreAnalyses(newAnalyses.length === pageSize);
    } catch (error) {
      console.error('加载所有分析数据失败:', error);
      Alert.alert('提示', '加载分析数据失败');
    } finally {
      setLoadingAllAnalyses(false);
    }
  };

  // 打开所有分析
  const openAllAnalyses = async () => {
    setShowAllAnalyses(true);
    // 清空之前的数据，从第一页开始加载
    setAllAnalyses([]);
    setAllAnalysesPage(0);
    setHasMoreAnalyses(true);
    // 每次点击都重新加载数据
      await loadAllAnalyses(0, false);
  };

  // 关闭所有分析，返回我的分析
  const closeAllAnalyses = () => {
    setShowAllAnalyses(false);
    // 重置分页状态，下次打开时从第一页开始
    setAllAnalysesPage(0);
    setHasMoreAnalyses(true);
  };

  // 加载更多分析数据
  const loadMoreAnalyses = async () => {
    if (!loadingAllAnalyses && hasMoreAnalyses) {
      await loadAllAnalyses(allAnalysesPage + 1, true);
    }
  };

  // 关闭结果模态框
  const handleCloseResult = () => {
    setShowResultModal(false);
    setSelectedResult(null);

    
    setLastProcessedRecordId(null); // 重置最后处理的记录ID
  };

  // 判断是否工作日且15:00之前
  const isWorkdayBefore15 = () => {
    try {
      const now = new Date();
      const day = now.getDay(); // 0:周日,6:周六
      const isWorkday = day !== 0 && day !== 6;
      const before15 = now.getHours() < 15 || (now.getHours() === 15 && now.getMinutes() === 0);
      return isWorkday && before15;
    } catch (e) {
      return false;
    }
  };

  // 检查当前股票是否正在盯盘
  const isCurrentStockMonitoring = () => {
    return currentMonitoringStock === selectedResult?.stockCode && monitorJobId !== null;
  };

  // 检查是否应该显示盯盘按钮
  const shouldShowMonitorButton = () => {
    if (!selectedResult?.stockCode) return false;
    
    // 只有在工作日15:00之前，且当前股票没有在盯盘时才显示按钮
    return isWorkdayBefore15() && !isCurrentStockMonitoring();
  };

  // 检查是否应该显示停止盯盘按钮
  const shouldShowStopMonitorButton = () => {
    return isCurrentStockMonitoring();
  };

  // 检查是否应该显示盯盘记录


  const handleOpenMonitor = () => {
    setShowMonitorModal(true);
  };

  const handleStartMonitoring = async () => {
    if (!selectedResult?.stockCode) {
      Alert.alert('提示', '未找到股票代码');
      return;
    }
    setMonitorLoading(true);
    try {
      const resp = await ApiService.startMonitoring(
        selectedResult.stockCode,
        parseInt(monitorInterval, 10),
        selectedResult?.result?.analysisId || null
      );
             if (resp?.success) {
         setMonitorJobId(resp.jobId);
         setCurrentMonitoringStock(selectedResult.stockCode); // 设置当前盯盘的股票
         setShowMonitoringIndicator(true); // 显示监控指示器
         Alert.alert('已开启盯盘', `任务ID: ${resp.jobId}`);
         setShowMonitorModal(false);
       } else {
        Alert.alert('开启失败', resp?.message || '请稍后重试');
      }
    } catch (e) {
      Alert.alert('网络错误', e?.message || '请稍后重试');
    } finally {
      setMonitorLoading(false);
    }
  };

  const handleStopMonitoring = async () => {
    if (!monitorJobId) {
      Alert.alert('提示', '当前没有运行中的盯盘任务');
      return;
    }
    setMonitorLoading(true);
    try {
      const resp = await ApiService.stopMonitoring(monitorJobId);
             if (resp?.success) {
         Alert.alert('已停止盯盘', '任务已停止');
         setMonitorJobId(null);
         setCurrentMonitoringStock(null); // 清除当前盯盘的股票

         setShowMonitoringIndicator(false); // 隐藏监控指示器
       } else {
        Alert.alert('停止失败', resp?.message || '请稍后重试');
      }
    } catch (e) {
      Alert.alert('网络错误', e?.message || '请稍后重试');
    } finally {
      setMonitorLoading(false);
    }
  };



  // 获取股票监控状态（页面加载时）
  const loadStockMonitoringStatus = async (stockCode) => {
    try {
      const resp = await ApiService.getStockMonitoringStatus(stockCode);
             if (resp?.success && resp?.data?.exists && resp?.data?.status === 'running') {
         setMonitorJobId(resp.data.jobId);
         setCurrentMonitoringStock(stockCode);
         setMonitorInterval(resp.data.intervalMinutes.toString());
         setShowMonitoringIndicator(true); // 显示监控指示器
       }
    } catch (error) {
      console.error('获取股票监控状态失败:', error);
    }
  };



  // 当查看结果时，检查该股票的监控状态
  useEffect(() => {
    if (showResultModal && selectedResult?.stockCode) {
      loadStockMonitoringStatus(selectedResult.stockCode);
    }
  }, [showResultModal, selectedResult?.stockCode]);

  // 快速选择最近分析的股票
  const handleQuickSelect = (stock) => {
    setStockCode(stock.code);
  };

  // 查看正在监控的股票的分析结果
  const handleViewMonitoringStock = async () => {
    if (!currentMonitoringStock) return;
    
    try {
      // 从历史记录中查找该股票的最新分析结果
      const history = await AnalysisHistoryService.getAnalysisHistory();
      const stockHistory = history.find(item => item.stockCode === currentMonitoringStock);
      
      if (stockHistory) {
        // 构造一个类似task的对象
        const mockTask = {
          stockCode: currentMonitoringStock,
          stockName: stockHistory.stockName,
          result: stockHistory.result,
          analysisId: stockHistory.result?.analysisId
        };
        
        setSelectedResult(mockTask);
        setShowResultModal(true);
      } else {
        // 如果没有历史记录，提示用户先进行分析
        Alert.alert(
          '提示',
          `没有找到 ${currentMonitoringStock} 的分析记录，请先进行综合分析。`,
          [
            { text: '知道了', style: 'default' },
            { 
              text: '立即分析', 
              onPress: () => {
                setStockCode(currentMonitoringStock);
                setShowResultModal(false);
              }
            }
          ]
        );
      }
    } catch (error) {
      console.error('获取监控股票分析结果失败:', error);
      Alert.alert('错误', '获取分析结果失败，请稍后重试');
    }
  };

  // 检测高置信度的交易建议并显示弹窗
  const checkHighConfidenceRecommendation = (records) => {
    if (!records || records.length === 0) return;
    
    // 获取最新的记录
    const latestRecord = records[records.length - 1];
    
    // 如果已经处理过这条记录，跳过
    if (lastProcessedRecordId === latestRecord.id) return;
    
    const content = latestRecord.content || '';
    
    // 检查是否包含置信度信息
    const confidenceMatch = content.match(/【置信度】\s*(高|中|低)/);
    const recommendationMatch = content.match(/【当前建议】\s*(买入|卖出|观望)/);
    const priceMatch = content.match(/【挂单价格】\s*买入：([\d.]+)，卖出：([\d.]+)/);
    
    if (confidenceMatch && recommendationMatch && priceMatch) {
      const confidence = confidenceMatch[1];
      const recommendation = recommendationMatch[1];
      const buyPrice = priceMatch[1];
      const sellPrice = priceMatch[2];
      
      // 如果是高置信度的买入或卖出建议，显示弹窗
      if (confidence === '高' && (recommendation === '买入' || recommendation === '卖出')) {
        const action = recommendation === '买入' ? '买入' : '卖出';
        const price = recommendation === '买入' ? buyPrice : sellPrice;
        
        Alert.alert(
          `🚨 高置信度${action}提醒`,
          `AI建议：${action} ${currentMonitoringStock}\n挂单价格：${price}\n置信度：高\n\n请及时关注交易机会！`,
          [
            { text: '知道了', style: 'default' },
            { 
              text: '查看详情', 
              onPress: () => {
                // 如果当前没有打开结果弹窗，先打开
                if (!showResultModal) {
                  // 需要先获取该股票的分析结果
                  handleViewMonitoringStock();
                } else {
                  // 如果已经打开，展开盯盘记录
          
                }
              }
            }
          ],
          { cancelable: false }
        );
        
        // 记录已处理的记录ID
        setLastProcessedRecordId(latestRecord.id);
      }
    }
  };

  // 移除图表相关函数，简化功能

  return (
    <View style={{ flex: 1 }}>
             {/* 全局监控指示器 */}
       {showMonitoringIndicator && currentMonitoringStock && (
         <View style={{
           backgroundColor: '#e3f2fd',
           padding: 12,
           marginHorizontal: 16,
           marginTop: 8,
           borderRadius: 8,
           borderLeftWidth: 4,
           borderLeftColor: '#2196F3',
           flexDirection: 'row',
           alignItems: 'center',
           justifyContent: 'space-between'
         }}>
           <View style={{ flex: 1 }}>
             <Paragraph style={{ 
               color: '#1976d2', 
               fontWeight: 'bold',
               fontSize: 14
             }}>
               🎯 正在监控: {currentMonitoringStock}
             </Paragraph>
             <Paragraph style={{ 
               color: '#1976d2', 
               fontSize: 12,
               marginTop: 2
             }}>
               间隔: {monitorInterval}分钟 | 自动检测高置信度建议
             </Paragraph>
           </View>
           <TouchableOpacity
             onPress={handleViewMonitoringStock}
             style={{
               backgroundColor: '#2196F3',
               paddingHorizontal: 12,
               paddingVertical: 6,
               borderRadius: 6
             }}
           >
             <Paragraph style={{ 
               color: '#ffffff', 
               fontSize: 12,
               fontWeight: 'bold'
             }}>
               查看
             </Paragraph>
           </TouchableOpacity>
         </View>
       )}

      <ScrollView 
        ref={scrollViewRef}
        style={styles.container}
        onScroll={handleScroll}
        scrollEventThrottle={16}
      >
        {/* 分析输入 */}
        <Card style={styles.card}>
          <Card.Content>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <Title>股票综合分析工具</Title>
              <Button
                mode="outlined"
                onPress={() => navigation.navigate('MonitoringList')}
                style={{ borderColor: '#1976d2' }}
                textColor="#1976d2"
                icon="eye-outline"
              >
                盯盘管理
              </Button>
            </View>
            
            <View style={{ flexDirection: 'row', alignItems: 'center', marginVertical: 8 }}>
              <TextInput
                label="股票代码"
                value={stockCode}
                onChangeText={setStockCode}
                placeholder="例如: 000001, 600519"
                mode="outlined"
                style={{ flex: 1 }}
                disabled={loading}
              />
              {stockCode && (
                <TouchableOpacity
                  style={{ marginLeft: 8, padding: 8 }}
                  onPress={() => setStockCode('')}
                >
                  <Ionicons name="close-circle" size={20} color="#999" />
                </TouchableOpacity>
              )}
            </View>

            <Paragraph style={[styles.smallText, { marginBottom: 8 }]}>
              综合分析
            </Paragraph>
            <Paragraph style={[styles.smallText, { marginBottom: 16, color: '#8E8E93' }]}>
              包含技术指标、资金流向、AI分析等全方位分析
            </Paragraph>

            {/* 最近分析的股票 */}
            {recentStocks.length > 0 && (
              <View style={{ marginBottom: 16 }}>
                <TouchableOpacity 
                  style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 8 }}
                  onPress={() => setIsRecentStocksCollapsed(!isRecentStocksCollapsed)}
                >
                  <Paragraph style={[styles.smallText, { flex: 1 }]}>最近分析:</Paragraph>
                  <Ionicons 
                    name={isRecentStocksCollapsed ? "chevron-down" : "chevron-up"} 
                    size={20} 
                    color="#999" 
                  />
                </TouchableOpacity>
                
                {!isRecentStocksCollapsed && (
                  <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                    {recentStocks.map((stock) => (
                      <Chip
                        key={stock.code}
                        mode="outlined"
                        onPress={() => handleQuickSelect(stock)}
                        style={{ marginBottom: 4 }}
                      >
                        {stock.code} - {stock.name}
                      </Chip>
                    ))}
                  </View>
                )}
              </View>
            )}

            <View style={{ flexDirection: 'row', gap: 8 }}>
              <Button
                mode="contained"
                onPress={handleAnalysis}
                loading={loading}
                disabled={loading || !stockCode.trim()}
                style={{ flex: 1 }}
              >
                开始综合分析
              </Button>

              <Button
                mode="outlined"
                onPress={showAllAnalyses ? closeAllAnalyses : openAllAnalyses}
                loading={loadingAllAnalyses}
                style={{ flex: 1 }}
                icon={showAllAnalyses ? "arrow-left" : "eye"}
              >
                {showAllAnalyses ? "返回我的分析" : "查看所有分析"}
              </Button>
            </View>
          </Card.Content>
        </Card>

        {/* 任务列表 */}
        <View style={{ marginTop: 12 }}>
          <Title style={{ marginHorizontal: 16 }}>
            {showAllAnalyses ? "所有分析数据" : "任务列表"}
          </Title>
          <AnalysisTaskList
            ref={taskListRef}
            stockCode={stockCodeToAnalyze}
            onTaskComplete={handleTaskComplete}
            onViewResult={handleViewResult}
            showAllAnalyses={showAllAnalyses}
            allAnalyses={allAnalyses}
            loadingAllAnalyses={loadingAllAnalyses}
            onLoadMoreAnalyses={loadMoreAnalyses}
            hasMoreAnalyses={hasMoreAnalyses}
          />
        </View>

        {/* 加载状态 */}
        {loading && (
          <Card style={styles.card}>
            <Card.Content>
              <View style={{ alignItems: 'center', padding: 20 }}>
                <ActivityIndicator size="large" color={theme.colors.primary} />
                <Paragraph style={{ marginTop: 16 }}>
                  正在分析 {stockCode}...
                </Paragraph>
                <Paragraph style={styles.smallText}>
                  正在进行综合分析，请稍候...
                </Paragraph>

                <Paragraph style={[styles.smallText, { marginTop: 16, textAlign: 'center' }]}>
                  💡 综合分析可能需要几分钟时间，请耐心等待
                </Paragraph>
              </View>
            </Card.Content>
          </Card>
        )}

        {/* 综合分析结果 */}
        {analysisResult && (
          <Card style={styles.card}>
            <Card.Content>
              <Title>综合分析结果</Title>
              <Paragraph style={styles.smallText}>
                股票代码: {analysisResult.stockCode}
              </Paragraph>
              <Divider style={{ marginVertical: 8 }} />
              
              {analysisResult.aiAnalysisResult && (
                <View style={{ marginVertical: 8 }}>
                  <Paragraph style={styles.subHeader}>AI分析摘要</Paragraph>
                  <Paragraph style={styles.text}>
                    {analysisResult.aiAnalysisResult.summary || '分析完成'}
                  </Paragraph>
                </View>
              )}

              <View style={{ flexDirection: 'row', flexWrap: 'wrap', marginTop: 8 }}>
                <Chip style={{ margin: 2 }} icon="trending-up">
                  技术分析
                </Chip>
                <Chip style={{ margin: 2 }} icon="chart-line">
                  趋势分析
                </Chip>
                <Chip style={{ margin: 2 }} icon="finance">
                  资金流向
                </Chip>
                <Chip style={{ margin: 2 }} icon="brain">
                  AI分析
                </Chip>
              </View>
            </Card.Content>
          </Card>
        )}

      </ScrollView>

      {/* 分析结果模态框 */}
      <Modal
        visible={showResultModal}
        animationType="slide"
        presentationStyle="pageSheet"
      >
        {selectedResult && (
          <View style={{ flex: 1 }}>
                  <AnalysisResultView
          result={selectedResult}
          stockCode={selectedResult?.stockCode || stockCode || '未知'}
          onClose={handleCloseResult}
        />

            {/* 盯盘控制区域 - 只在有盯盘相关内容时显示 */}
            {(shouldShowMonitorButton() || shouldShowStopMonitorButton()) && (
              <View style={{ 
                padding: 16, 
                borderTopWidth: 1, 
                borderTopColor: '#eee',
                backgroundColor: '#fafafa'
              }}>
                {/* 盯盘状态指示器 - 只在有盯盘任务时显示 */}
                {isCurrentStockMonitoring() && (
                 <View style={{ 
                   flexDirection: 'row', 
                   justifyContent: 'space-between', 
                   alignItems: 'center',
                   marginBottom: 16,
                   padding: 12,
                   backgroundColor: '#e3f2fd',
                   borderRadius: 8,
                   borderLeftWidth: 4,
                   borderLeftColor: '#2196F3'
                 }}>
                   <View style={{ flex: 1 }}>
                     <Paragraph style={{ 
                       color: '#1976d2', 
                       fontWeight: 'bold',
                       fontSize: 14
                     }}>
                       🎯 盯盘状态
                     </Paragraph>
                     <Paragraph style={{ 
                       color: '#1976d2', 
                       fontSize: 12,
                       marginTop: 2
                     }}>
                       {currentMonitoringStock} - 间隔: {monitorInterval}分钟
                     </Paragraph>
                   </View>
                   <View style={{
                     backgroundColor: '#4caf50',
                     paddingHorizontal: 8,
                     paddingVertical: 4,
                     borderRadius: 12
                   }}>
                     <Paragraph style={{ 
                       color: '#ffffff', 
                       fontSize: 10,
                       fontWeight: 'bold'
                     }}>
                       运行中
                     </Paragraph>
                   </View>
                 </View>
               )}

              {/* 盯盘按钮 */}
              {shouldShowMonitorButton() && (
                <Button
                  mode="contained"
                  onPress={handleOpenMonitor}
                  icon="bell"
                  loading={monitorLoading}
                  style={{ marginBottom: 16 }}
                >
                  帮我盯盘
                </Button>
              )}

              {/* 停止盯盘按钮 */}
              {shouldShowStopMonitorButton() && (
                <View style={{ alignItems: 'center', marginBottom: 16 }}>
                  <Button
                    mode="outlined"
                    onPress={handleStopMonitoring}
                    loading={monitorLoading}
                    icon="stop"
                    style={{ borderColor: '#f44336' }}
                    textColor="#f44336"
                  >
                    停止盯盘
                  </Button>
                </View>
              )}




            </View>
            )}
          </View>
        )}
      </Modal>

      {/* 盯盘设置弹窗 */}
      <Modal
        visible={showMonitorModal}
        transparent={true}
        animationType="fade"
        onRequestClose={() => setShowMonitorModal(false)}
      >
        <View style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.4)', justifyContent: 'center', alignItems: 'center' }}>
          <View style={{ backgroundColor: '#fff', width: '86%', borderRadius: 12, padding: 16 }}>
            <Title>盯盘频率</Title>
            <Paragraph style={{ marginBottom: 8 }}>请选择分析间隔</Paragraph>
            <SegmentedButtons
              value={monitorInterval}
              onValueChange={setMonitorInterval}
              buttons={[
                { value: '5', label: '5分钟' },
                { value: '10', label: '10分钟' },
                { value: '30', label: '半小时' },
                { value: '60', label: '1小时' },
              ]}
            />
            <View style={{ flexDirection: 'row', marginTop: 16, gap: 12 }}>
              <Button mode="text" onPress={() => setShowMonitorModal(false)} disabled={monitorLoading}>
                取消
              </Button>
              <Button mode="contained" onPress={handleStartMonitoring} loading={monitorLoading}>
                开始
              </Button>
            </View>
          </View>
        </View>
      </Modal>
      
      {/* 返回顶部按钮 */}
      {showScrollTop && (
        <FAB
          icon="arrow-up"
          style={{
            position: 'absolute',
            margin: 16,
            right: 0,
            bottom: 0,
          }}
          onPress={scrollToTop}
        />
      )}
    </View>
  );
}
