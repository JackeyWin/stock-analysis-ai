import React, { useState, useEffect } from 'react';
import {
  View,
  ScrollView,
  RefreshControl,
  Alert,
  Dimensions,
} from 'react-native';
import {
  Card,
  Title,
  Paragraph,
  Button,
  ActivityIndicator,
  Chip,
  Divider,
} from 'react-native-paper';
import { theme, styles } from '../utils/theme';
import ApiService from '../services/ApiService';
import AnalysisHistoryList from '../components/AnalysisHistoryList';

const { width: screenWidth } = Dimensions.get('window');

export default function HomeScreen({ navigation }) {
  const [popularStocks, setPopularStocks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [serviceStatus, setServiceStatus] = useState('unknown');
  const [showHistory, setShowHistory] = useState(false);

  useEffect(() => {
    loadInitialData();
  }, []);

  const loadInitialData = async () => {
    try {
      setLoading(true);
      await Promise.all([
        loadPopularStocks(),
        checkServiceHealth(),
      ]);
    } catch (error) {
      console.error('加载初始数据失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadPopularStocks = async () => {
    try {
      const response = await ApiService.getPopularStocks();
      if (response.success) {
        setPopularStocks(response.data);
      }
    } catch (error) {
      console.error('加载热门股票失败:', error);
      Alert.alert('错误', '加载热门股票失败');
    }
  };

  const checkServiceHealth = async () => {
    try {
      await ApiService.healthCheck();
      setServiceStatus('healthy');
    } catch (error) {
      setServiceStatus('error');
    }
  };

  const onRefresh = async () => {
    setRefreshing(true);
    await loadInitialData();
    setRefreshing(false);
  };

  const handleStockPress = (stock) => {
    navigation.navigate('StockDetail', { 
      stockCode: stock.code,
      stockName: stock.name,
    });
  };



  const handleHistorySelect = (historyItem) => {
    // 跳转到分析结果页面，显示历史记录
    navigation.navigate('Analysis', { 
      stockCode: historyItem.stockCode,
      stockName: historyItem.stockName,
      historyData: historyItem
    });
  };

  const toggleHistory = () => {
    setShowHistory(!showHistory);
  };

  // 移除图表相关代码，简化功能

  if (loading && !refreshing) {
    return (
      <View style={[styles.container, { justifyContent: 'center', alignItems: 'center' }]}>
        <ActivityIndicator size="large" color={theme.colors.primary} />
        <Paragraph style={{ marginTop: 16 }}>加载中...</Paragraph>
      </View>
    );
  }

  return (
    <ScrollView
      style={styles.container}
      refreshControl={
        <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
      }
    >
      {/* 服务状态 */}
      <Card style={styles.card}>
        <Card.Content>
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
            <Title>服务状态</Title>
            <Chip
              icon={serviceStatus === 'healthy' ? 'check-circle' : 'alert-circle'}
              style={{
                backgroundColor: serviceStatus === 'healthy' ? theme.colors.profit : theme.colors.loss,
              }}
              textStyle={{ color: 'white' }}
            >
              {serviceStatus === 'healthy' ? '正常' : '异常'}
            </Chip>
          </View>
        </Card.Content>
      </Card>

      {/* 分析历史 */}
      <Card style={styles.card}>
        <Card.Content>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
            <Title>分析历史</Title>
            <Button
              mode="outlined"
              compact
              onPress={toggleHistory}
            >
              {showHistory ? '隐藏' : '查看'}
            </Button>
          </View>
          <Paragraph style={styles.smallText}>
            最近一周的分析记录，最多显示10条
          </Paragraph>
          
          {showHistory && (
            <View style={{ marginTop: 16, height: 300 }}>
              <AnalysisHistoryList
                onSelectHistory={handleHistorySelect}
                onRefresh={onRefresh}
              />
            </View>
          )}
        </Card.Content>
      </Card>

      {/* 热门股票 */}
      <Card style={styles.card}>
        <Card.Content>
          <Title>热门股票</Title>
          <Paragraph style={styles.smallText}>点击查看详情或快速分析</Paragraph>
          <Divider style={{ marginVertical: 8 }} />
          
          {popularStocks.map((stock, index) => (
            <View key={stock.code} style={{ marginVertical: 4 }}>
              <View style={{ 
                flexDirection: 'row', 
                justifyContent: 'space-between', 
                alignItems: 'center',
                paddingVertical: 8,
              }}>
                <View style={{ flex: 1 }}>
                  <Paragraph style={styles.text}>
                    {stock.name} ({stock.code})
                  </Paragraph>
                  <Paragraph style={styles.smallText}>
                    {stock.market === 'SH' ? '上海' : '深圳'}
                  </Paragraph>
                </View>
                
                <View style={{ flexDirection: 'row', gap: 8 }}>
                  <Button
                    mode="contained"
                    compact
                    onPress={() => handleStockPress(stock)}
                  >
                    详情
                  </Button>
                </View>
              </View>
              {index < popularStocks.length - 1 && <Divider />}
            </View>
          ))}
        </Card.Content>
      </Card>

      {/* 快捷功能 */}
      <Card style={styles.card}>
        <Card.Content>
          <Title>快捷功能</Title>
          <View style={{ 
            flexDirection: 'row', 
            justifyContent: 'space-around', 
            marginTop: 16 
          }}>
            <Button
              mode="contained"
              icon="search"
              onPress={() => navigation.navigate('Search')}
              style={{ flex: 1, marginHorizontal: 4 }}
            >
              股票搜索
            </Button>
            <Button
              mode="contained"
              icon="analytics"
              onPress={() => navigation.navigate('Analysis')}
              style={{ flex: 1, marginHorizontal: 4 }}
            >
              综合分析
            </Button>
          </View>
        </Card.Content>
      </Card>
    </ScrollView>
  );
}
