import React, { useState, useRef, useEffect } from 'react';
import {
  View,
  ScrollView,
  Alert,
  Dimensions,
  Modal,
  TouchableOpacity,
  FlatList,
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

    // 直接调用子组件方法新增任务，保证立刻出现“分析中”卡片
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
    setSelectedResult(task.result);
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
      const response = await ApiService.getAllAnalysisTasks(page, 10);
      const newAnalyses = response.content || response;
      
      if (append) {
        setAllAnalyses(prev => [...prev, ...newAnalyses]);
      } else {
        setAllAnalyses(newAnalyses);
      }
      
      setAllAnalysesPage(page);
      setHasMoreAnalyses(newAnalyses.length === 10); // 如果返回10条，认为还有更多
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
    if (allAnalyses.length === 0) {
      await loadAllAnalyses(0, false);
    }
  };

  // 关闭所有分析，返回我的分析
  const closeAllAnalyses = () => {
    setShowAllAnalyses(false);
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
  };



  // 快速选择最近分析的股票
  const handleQuickSelect = (stock) => {
    setStockCode(stock.code);
  };



  // 移除图表相关函数，简化功能

  return (
    <View style={{ flex: 1 }}>
      <ScrollView 
        ref={scrollViewRef}
        style={styles.container}
        onScroll={handleScroll}
        scrollEventThrottle={16}
      >
        {/* 分析输入 */}
        <Card style={styles.card}>
          <Card.Content>
            <Title>股票综合分析工具</Title>
            
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
                visible={false}
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
                  <AnalysisResultView
          result={selectedResult}
          stockCode={selectedResult?.stockCode || stockCode || '未知'}
          onClose={handleCloseResult}
        />
        )}
      </Modal>

      {/* 所有分析数据模态框 */}
      <Modal
        visible={showAllAnalyses}
        animationType="slide"
        presentationStyle="pageSheet"
        onRequestClose={closeAllAnalyses}
      >
        <View style={{ flex: 1, padding: 16 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 16 }}>
            <Title style={{ flex: 1 }}>所有分析数据</Title>
            <Button mode="text" onPress={closeAllAnalyses} icon="close">
              关闭
            </Button>
          </View>
          
          <FlatList
            data={allAnalyses}
            keyExtractor={(item, index) => `${item.taskId || item.id || index}-${item.stockCode}`}
            renderItem={({ item }) => (
              <Card style={{ margin: 8 }}>
                <Card.Content>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                    <View>
                      <Text style={{ fontWeight: 'bold', fontSize: 16 }}>
                        {item.stockCode}
                      </Text>
                      {item.stockName && (
                        <Text style={{ color: '#666', fontSize: 14 }}>
                          {item.stockName}
                        </Text>
                      )}
                    </View>
                    <View style={{ alignItems: 'flex-end' }}>
                      <Text style={{ color: '#888', fontSize: 12 }}>
                        {item.analysis_time ? new Date(item.analysis_time).toLocaleString('zh-CN') : 
                         item.timestamp ? new Date(item.timestamp).toLocaleString('zh-CN') : '未知时间'}
                      </Text>
                      <Text style={{ 
                        color: item.status === 'completed' ? '#34C759' : 
                               item.status === 'failed' ? '#FF3B30' : '#FF9500',
                        fontSize: 12 
                      }}>
                        {item.status === 'completed' ? '已完成' : 
                         item.status === 'failed' ? '失败' : '进行中'}
                      </Text>
                    </View>
                  </View>
                  
                  {item.result && (
                    <Button
                      mode="outlined"
                      onPress={() => {
                        handleViewResult(item);
                        closeAllAnalyses();
                      }}
                      style={{ marginTop: 8 }}
                    >
                      查看详情
                    </Button>
                  )}
                </Card.Content>
              </Card>
            )}
            onEndReached={loadMoreAnalyses}
            onEndReachedThreshold={0.5}
            ListFooterComponent={() => (
              loadingAllAnalyses ? (
                <View style={{ padding: 20, alignItems: 'center' }}>
                  <ActivityIndicator size="small" color={theme.colors.primary} />
                  <Text style={{ marginTop: 8, color: '#888' }}>加载更多...</Text>
                </View>
              ) : hasMoreAnalyses ? (
                <Button
                  mode="outlined"
                  onPress={loadMoreAnalyses}
                  style={{ margin: 16 }}
                >
                  加载更多
                </Button>
              ) : allAnalyses.length > 0 ? (
                <Text style={{ textAlign: 'center', padding: 16, color: '#888' }}>
                  没有更多数据了
                </Text>
              ) : null
            )}
            ListEmptyComponent={() => (
              !loadingAllAnalyses && (
                <Text style={{ textAlign: 'center', padding: 20, color: '#888' }}>
                  暂无分析数据
                </Text>
              )
            )}
          />
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
