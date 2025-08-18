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

const { width: screenWidth } = Dimensions.get('window');

export default function HomeScreen({ navigation }) {
  const [dailyRecommendations, setDailyRecommendations] = useState([]);
  const [recommendationSummary, setRecommendationSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [serviceStatus, setServiceStatus] = useState('unknown');

  useEffect(() => {
    loadInitialData();
  }, []);

  const loadInitialData = async () => {
    try {
      setLoading(true);
      await Promise.all([
        loadDailyRecommendations(),
        checkServiceHealth(),
      ]);
    } catch (error) {
      console.error('加载初始数据失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadDailyRecommendations = async () => {
    try {
      // 获取推荐摘要
      const summaryResponse = await ApiService.getDailyRecommendationSummary();
      if (summaryResponse.success && summaryResponse.data.available) {
        setRecommendationSummary(summaryResponse.data);
        setDailyRecommendations(summaryResponse.data.topStocks || []);
      } else {
        // 如果没有推荐，显示提示信息
        setRecommendationSummary({ available: false, message: '今日推荐暂未生成' });
        setDailyRecommendations([]);
      }
    } catch (error) {
      console.error('加载每日推荐失败:', error);
      Alert.alert('错误', '加载每日推荐失败');
      setRecommendationSummary({ available: false, message: '加载失败' });
      setDailyRecommendations([]);
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
      stockCode: stock.stockCode,
      stockName: stock.stockName,
    });
  };

  const handleRecommendationDetail = (stock) => {
    navigation.navigate('RecommendationDetail', {
      stockCode: stock.stockCode,
      stockName: stock.stockName,
      recommendation: stock,
    });
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



      {/* AI每日推荐 */}
      <Card style={styles.card}>
        <Card.Content>
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
            <Title>🤖 AI每日推荐</Title>
            {recommendationSummary?.available && (
              <Chip
                icon="star"
                style={{ backgroundColor: theme.colors.primary }}
                textStyle={{ color: 'white' }}
              >
                {recommendationSummary.totalCount}只
              </Chip>
            )}
          </View>
          
          {recommendationSummary?.available ? (
            <>
              <Paragraph style={styles.smallText}>
                {recommendationSummary.date} | 基于AI分析的优质股票推荐
              </Paragraph>
              <Divider style={{ marginVertical: 8 }} />
              
              {dailyRecommendations.map((stock, index) => (
                <View key={stock.stockCode} style={{ marginVertical: 4 }}>
                  <View style={{ 
                    flexDirection: 'row', 
                    justifyContent: 'space-between', 
                    alignItems: 'center',
                    paddingVertical: 8,
                  }}>
                    <View style={{ flex: 1 }}>
                      <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                        <Paragraph style={styles.text}>
                          {stock.stockName} ({stock.stockCode})
                        </Paragraph>
                        {stock.isHot && (
                          <Chip
                            icon="fire"
                            style={{ 
                              backgroundColor: theme.colors.loss, 
                              marginLeft: 8,
                              height: 24,
                            }}
                            textStyle={{ color: 'white', fontSize: 10 }}
                          >
                            热门
                          </Chip>
                        )}
                      </View>
                      <Paragraph style={styles.smallText}>
                        {stock.sector} | 评分: {stock.score?.toFixed(1)}/10 | {stock.rating}
                      </Paragraph>
                      <Paragraph style={[styles.smallText, { color: theme.colors.primary }]}>
                        {stock.recommendationReason?.substring(0, 50)}...
                      </Paragraph>
                    </View>
                    
                    <View style={{ flexDirection: 'row', gap: 8 }}>
                      <Button
                        mode="outlined"
                        compact
                        onPress={() => handleStockPress(stock)}
                      >
                        分析
                      </Button>
                      <Button
                        mode="contained"
                        compact
                        onPress={() => handleRecommendationDetail(stock)}
                      >
                        推荐详情
                      </Button>
                    </View>
                  </View>
                  {index < dailyRecommendations.length - 1 && <Divider />}
                </View>
              ))}
              
              {recommendationSummary.summary && (
                <View style={{ marginTop: 12, padding: 12, backgroundColor: theme.colors.surface, borderRadius: 8 }}>
                  <Paragraph style={[styles.smallText, { fontStyle: 'italic' }]}>
                    {recommendationSummary.summary.substring(0, 100)}...
                  </Paragraph>
                </View>
              )}
            </>
          ) : (
            <View style={{ alignItems: 'center', paddingVertical: 20 }}>
              <Paragraph style={styles.text}>
                {recommendationSummary?.message || '今日推荐暂未生成'}
              </Paragraph>
              <Button
                mode="outlined"
                onPress={loadDailyRecommendations}
                style={{ marginTop: 8 }}
              >
                刷新
              </Button>
            </View>
          )}
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
