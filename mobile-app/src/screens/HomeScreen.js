import React, { useState, useEffect } from 'react';
import {
  View,
  ScrollView,
  RefreshControl,
  Alert,
  Dimensions,
  StyleSheet,
  TouchableOpacity,
} from 'react-native';
import {
  Card,
  Title,
  Paragraph,
  Button,
  ActivityIndicator,
  Chip,
  Divider,
  Text,
} from 'react-native-paper';
import { theme, styles } from '../utils/theme';
import ApiService from '../services/ApiService';
import RecommendationService from '../services/RecommendationService';

const { width: screenWidth } = Dimensions.get('window');

// 骨架屏组件
const SkeletonLoader = () => (
  <View style={skeletonStyles.container}>
    <View style={skeletonStyles.header} />
    <View style={skeletonStyles.content}>
      {[1, 2, 3].map((item) => (
        <View key={item} style={skeletonStyles.item}>
          <View style={skeletonStyles.textLine} />
          <View style={skeletonStyles.textLineShort} />
        </View>
      ))}
    </View>
  </View>
);

const skeletonStyles = StyleSheet.create({
  container: {
    padding: 16,
  },
  header: {
    height: 24,
    width: '40%',
    backgroundColor: '#e0e0e0',
    marginBottom: 16,
    borderRadius: 4,
  },
  content: {
    flexDirection: 'column',
  },
  item: {
    marginBottom: 16,
  },
  textLine: {
    height: 16,
    width: '100%',
    backgroundColor: '#e0e0e0',
    marginBottom: 8,
    borderRadius: 4,
  },
  textLineShort: {
    height: 16,
    width: '60%',
    backgroundColor: '#e0e0e0',
    borderRadius: 4,
  },
});

export default function HomeScreen({ navigation }) {
  const [dailyRecommendations, setDailyRecommendations] = useState([]);
  const [recommendationSummary, setRecommendationSummary] = useState({
    available: false,
    date: '',
    marketOverview: '',
    policyHotspots: '',
    industryHotspots: '',
    hotspotsSummary: '',
    summary: '',
    analystView: '',
    totalCount: 0,
    topStocks: []
  });
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [serviceStatus, setServiceStatus] = useState('unknown');
  const [hotspotsExpanded, setHotspotsExpanded] = useState(false);
  const [summaryExpanded, setSummaryExpanded] = useState(false);
  const [analystExpanded, setAnalystExpanded] = useState(false);

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
      Alert.alert('错误', '加载数据失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  const loadDailyRecommendations = async () => {
    try {
      const response = await RecommendationService.getTodayRecommendations();
      console.log('推荐摘要API响应:', response);
      
      if (response && response.success && response.data && response.data.available) {
        const summaryData = response.data;
        setRecommendationSummary({
          ...summaryData,
          available: true
        });
        setDailyRecommendations(summaryData.topStocks || []);
        console.log('设置推荐摘要数据:', summaryData);
      } else {
        const message = response?.data?.message || response?.message || '今日推荐暂未生成';
        setRecommendationSummary({ 
          available: false, 
          message: message
        });
        setDailyRecommendations([]);
        console.log('推荐不可用:', message);
      }
    } catch (error) {
      console.error('加载每日推荐失败:', error);
      Alert.alert('错误', '加载每日推荐失败，请检查网络连接');
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

  const refreshRecommendations = async () => {
    try {
      setRefreshing(true);
      await RecommendationService.refreshRecommendations();
      await loadDailyRecommendations();
    } catch (error) {
      console.error('刷新推荐失败:', error);
      Alert.alert('刷新失败', '无法刷新推荐数据');
    } finally {
      setRefreshing(false);
    }
  };

  const onRefresh = async () => {
    await refreshRecommendations();
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

  // 获取风险等级颜色
  const getRiskLevelColor = (riskLevel) => {
    switch (riskLevel) {
      case '低': return theme.colors.profit;
      case '中': return theme.colors.warning;
      case '高': return theme.colors.loss;
      default: return theme.colors.textSecondary;
    }
  };

  // 获取推荐等级颜色
  const getRatingColor = (rating) => {
    if (rating?.includes('强烈')) {
      return theme.colors.profit;
    } else if (rating?.includes('推荐')) {
      return theme.colors.primary;
    } else if (rating?.includes('谨慎')) {
      return theme.colors.warning || '#FF9800';
    }
    return theme.colors.text;
  };

  if (loading && !refreshing) {
    return (
      <ScrollView
        style={styles.container}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }
      >
        <Card style={styles.card}>
          <Card.Content>
            <Title>服务状态</Title>
            <SkeletonLoader />
          </Card.Content>
        </Card>
        
        <Card style={styles.card}>
          <Card.Content>
            <Title>🤖 AI每日推荐</Title>
            <SkeletonLoader />
          </Card.Content>
        </Card>
        
        <Card style={styles.card}>
          <Card.Content>
            <Title>快捷功能</Title>
            <SkeletonLoader />
          </Card.Content>
        </Card>
      </ScrollView>
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
             <View style={{ flexDirection: 'row', alignItems: 'center' }}>
               <Title>🤖 AI今日推荐</Title>
               <TouchableOpacity onPress={() => navigation.navigate('History')}>
                 <Text style={{ fontSize: 12, color: theme.colors.primary, marginLeft: 8, textDecorationLine: 'underline' }}>
                   （往期数据）
                 </Text>
               </TouchableOpacity>
             </View>
            {recommendationSummary?.available && (
              <Chip
                 compact
                 style={{ backgroundColor: theme.colors.primary, borderRadius: 10 }}
                 contentStyle={{ height: 16, paddingHorizontal: 6 }}
                 textStyle={{ color: 'white', fontWeight: '600', fontSize: 10, textAlignVertical: 'center' }}
               >
                 共{recommendationSummary.totalCount}只
              </Chip>
            )}
          </View>
          
          {recommendationSummary?.available ? (
            <>
              <Paragraph style={styles.smallText}>
                {recommendationSummary.date} | 基于AI分析的优质股票推荐
              </Paragraph>
              
              {/* 市场信息摘要 */}
              {(recommendationSummary.marketOverview || recommendationSummary.policyHotspots || recommendationSummary.industryHotspots) && (
                <View style={{ marginVertical: 12, padding: 12, backgroundColor: theme.colors.surface, borderRadius: 12, borderWidth: 1, borderColor: '#eee' }}>
                  {recommendationSummary.marketOverview && (
                    <View style={{ flexDirection: 'row', marginBottom: 6 }}>
                      <Text style={{ fontWeight: 'bold', width: 80 }}>市场概况:</Text>
                      <Text style={[styles.smallText]} numberOfLines={3}>{recommendationSummary.marketOverview}</Text>
                    </View>
                  )}
                  {recommendationSummary.policyHotspots && (
                    <View style={{ flexDirection: 'row', marginBottom: 6 }}>
                      <Text style={{ fontWeight: 'bold', width: 80 }}>政策热点:</Text>
                      <Text style={[styles.smallText]} numberOfLines={3}>{recommendationSummary.policyHotspots}</Text>
                    </View>
                  )}
                  {recommendationSummary.industryHotspots && (
                    <View style={{ flexDirection: 'row' }}>
                      <Text style={{ fontWeight: 'bold', width: 80 }}>行业热点:</Text>
                      <Text style={[styles.smallText]} numberOfLines={3}>{recommendationSummary.industryHotspots}</Text>
                    </View>
                  )}
                </View>
              )}
              
              <Divider style={{ marginVertical: 8 }} />
              
              {/* 市场信息摘要 */}
              {recommendationSummary.hotspotsSummary && (
                <TouchableOpacity
                  activeOpacity={0.9}
                  onPress={() => setHotspotsExpanded(!hotspotsExpanded)}
                  style={{ marginVertical: 12 }}
                >
                  <View style={{ padding: 12, backgroundColor: theme.colors.surface, borderRadius: 12, borderWidth: 1, borderColor: '#eee' }}>
                    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                      <Text style={{ fontWeight: 'bold' }}>市场热点</Text>
                      <Chip mode="outlined" compact style={{ borderRadius: 10 }} contentStyle={{ height: 16, paddingHorizontal: 6 }} textStyle={{ fontSize: 10, textAlignVertical: 'center' }}>
                        {hotspotsExpanded ? '收起' : '展开'}
                      </Chip>
                    </View>
                    <Text style={[styles.smallText]} numberOfLines={hotspotsExpanded ? undefined : 4}>
                      {recommendationSummary.hotspotsSummary}
                    </Text>
                </View>
                </TouchableOpacity>
              )}
              
              <Divider style={{ marginVertical: 8 }} />
              
              {dailyRecommendations.map((stock, index) => (
                <TouchableOpacity key={stock.stockCode} activeOpacity={0.85} onPress={() => handleRecommendationDetail(stock)} style={{ marginVertical: 8 }}>
                  <View style={{ padding: 14, borderRadius: 14, borderWidth: 1, borderColor: '#eee', backgroundColor: '#fff', flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                    <View style={{ flex: 1, paddingRight: 12 }}>
                      <View style={{ flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', marginBottom: 8 }}>
                        <Text style={{ fontSize: 16, fontWeight: '700' }}>
                          {stock.stockName} ({stock.stockCode})
                        </Text>
                        <View style={{ flexDirection: 'row', alignItems: 'center', marginLeft: 'auto' }}>
                        {stock.isHot && (
                          <Chip
                            icon="fire"
                              compact
                              style={{ backgroundColor: theme.colors.loss, marginLeft: 6, borderRadius: 10 }}
                              contentStyle={{ height: 16, paddingHorizontal: 6 }}
                              textStyle={{ color: 'white', fontSize: 10, fontWeight: '600', textAlignVertical: 'center' }}
                          >
                            热门
                          </Chip>
                        )}
                        <Chip
                            compact
                            style={{ backgroundColor: getRatingColor(stock.rating), marginLeft: 6, borderRadius: 10 }}
                            contentStyle={{ height: 16, paddingHorizontal: 6 }}
                            textStyle={{ color: 'white', fontSize: 10, fontWeight: '600', textAlignVertical: 'center' }}
                          >
                            {stock.rating || '未评级'}
                          </Chip>
                          <Chip
                            compact
                            style={{ backgroundColor: getRiskLevelColor(stock.riskLevel), marginLeft: 6, borderRadius: 10 }}
                            contentStyle={{ height: 16, paddingHorizontal: 6 }}
                            textStyle={{ color: 'white', fontSize: 10, fontWeight: '600', textAlignVertical: 'center' }}
                          >
                            风险·{stock.riskLevel || '未评估'}
                        </Chip>
                      </View>
                      </View>
                      <View style={{ flexDirection: 'row', flexWrap: 'wrap', marginBottom: 6 }}>
                        <Text style={[styles.smallText, { color: theme.colors.textSecondary }]}> {stock.sector} </Text>
                        <Text style={[styles.smallText, { color: theme.colors.textSecondary }]}> | 评分 {stock.score?.toFixed(1)}/10 </Text>
                        <Text style={[styles.smallText, { color: theme.colors.textSecondary }]}> | {stock.rating} </Text>
                        {stock.targetPrice && (
                          <Text style={[styles.smallText, { color: theme.colors.textSecondary }]}> | 目标 {stock.targetPrice?.toFixed(2)} </Text>
                        )}
                        {stock.expectedReturn && (
                          <Text style={[styles.smallText, { color: stock.expectedReturn > 0 ? theme.colors.profit : theme.colors.loss }]}> | 预期 {stock.expectedReturn?.toFixed(2)}% </Text>
                        )}
                      </View>
                      <Text style={[styles.smallText, { color: theme.colors.primary }]} numberOfLines={3}>
                        {stock.recommendationReason}
                      </Text>
                    </View>
                  </View>
                </TouchableOpacity>
              ))}
              
              {/* 推荐总结和分析师观点 */}
                  {recommendationSummary.summary && (
                <TouchableOpacity
                  activeOpacity={0.9}
                  onPress={() => setSummaryExpanded(!summaryExpanded)}
                  style={{ marginTop: 16 }}
                >
                  <View style={{ padding: 12, backgroundColor: theme.colors.surface, borderRadius: 12, borderWidth: 1, borderColor: '#eee' }}>
                    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                      <Text style={{ fontWeight: 'bold' }}>推荐总结</Text>
                      <Chip mode="outlined" compact style={{ borderRadius: 10 }} contentStyle={{ height: 16, paddingHorizontal: 6 }} textStyle={{ fontSize: 10 }}>
                        {summaryExpanded ? '收起' : '展开'}
                      </Chip>
                    </View>
                    <Text style={[styles.smallText, { fontStyle: 'italic' }]} numberOfLines={summaryExpanded ? undefined : 4}>
                        {recommendationSummary.summary}
                    </Text>
                  </View>
                </TouchableOpacity>
                  )}
                  {recommendationSummary.analystView && (
                <TouchableOpacity
                  activeOpacity={0.9}
                  onPress={() => setAnalystExpanded(!analystExpanded)}
                  style={{ marginTop: 12 }}
                >
                  <View style={{ padding: 12, backgroundColor: theme.colors.surface, borderRadius: 12, borderWidth: 1, borderColor: '#eee' }}>
                    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                      <Text style={{ fontWeight: 'bold' }}>AI分析师观点</Text>
                      <Chip mode="outlined" compact style={{ borderRadius: 10 }} contentStyle={{ height: 16, paddingHorizontal: 6 }} textStyle={{ fontSize: 10 }}>
                        {analystExpanded ? '收起' : '展开'}
                      </Chip>
                    </View>
                    <Text style={[styles.smallText]} numberOfLines={analystExpanded ? undefined : 4}>
                        {recommendationSummary.analystView}
                    </Text>
                </View>
                </TouchableOpacity>
              )}
              
              
            </>
          ) : (
            <View style={{ alignItems: 'center', paddingVertical: 20 }}>
              <Paragraph style={styles.text}>
                {recommendationSummary?.message || '今日推荐暂未生成'}
              </Paragraph>
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
