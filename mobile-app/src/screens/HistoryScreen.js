import React, { useState, useEffect } from 'react';
import {
  View,
  ScrollView,
  RefreshControl,
  Alert,
  TouchableOpacity,
  Text,
  StyleSheet,
  Modal,
} from 'react-native';
import {
  Card,
  Title,
  Paragraph,
  Button,
  ActivityIndicator,
  Chip,
  Divider,
  Surface,
  Menu,
  Provider,
} from 'react-native-paper';
import { theme, styles } from '../utils/theme';
import RecommendationService from '../services/RecommendationService';
import { Ionicons } from '@expo/vector-icons';

// 模态框头部样式
const modalHeaderStyles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: theme.colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.outline,
  },
  headerLeft: {
    flex: 1,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: theme.colors.text,
  },
  headerActions: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  actionButton: {
    padding: 8,
    marginLeft: 8,
  },
  content: {
    flex: 1,
    backgroundColor: theme.colors.background,
  },
});

export default function HistoryScreen({ navigation }) {
  const [availableDates, setAvailableDates] = useState([]);
  const [selectedDate, setSelectedDate] = useState(null);
  const [menuVisible, setMenuVisible] = useState(false);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [recommendationData, setRecommendationData] = useState(null);

  useEffect(() => {
    loadAvailableDates();
  }, []);

  const loadAvailableDates = async () => {
    try {
      setLoading(true);
      const response = await RecommendationService.getAvailableDates();
      console.log('可用日期响应:', response);
      
      // 检查响应结构
      let dates = [];
      if (response && response.success) {
        dates = response.data || [];
      } else if (Array.isArray(response)) {
        // 如果直接返回数组
        dates = response;
      } else if (response && Array.isArray(response.data)) {
        // 如果响应在 data 字段中
        dates = response.data;
      }
      
      console.log('解析后的日期列表:', dates);
      setAvailableDates(dates);
      
      if (dates.length > 0) {
        setSelectedDate(dates[0]);
        loadRecommendationByDate(dates[0]);
      }
    } catch (error) {
      console.error('加载可用日期失败:', error);
      Alert.alert('错误', '加载可用日期失败');
    } finally {
      setLoading(false);
    }
  };

  const loadRecommendationByDate = async (date) => {
    try {
      setLoading(true);
      const response = await RecommendationService.getRecommendationByDate(date);
      console.log('推荐数据响应:', response);
      
      // 检查响应结构
      let data = null;
      if (response && response.success) {
        data = response.data;
      } else if (response && response.data) {
        // 如果响应在 data 字段中
        data = response.data;
      } else if (response) {
        // 如果直接返回数据
        data = response;
      }
      
      console.log('解析后的推荐数据:', data);
      setRecommendationData(data);
      
      if (!data) {
        Alert.alert('提示', '未找到该日期的推荐数据');
      }
    } catch (error) {
      console.error('加载推荐数据失败:', error);
      Alert.alert('错误', '加载推荐数据失败');
      setRecommendationData(null);
    } finally {
      setLoading(false);
    }
  };

  const onRefresh = async () => {
    setRefreshing(true);
    await loadAvailableDates();
    setRefreshing(false);
  };

  const handleDateSelect = (date) => {
    setSelectedDate(date);
    setMenuVisible(false);
    loadRecommendationByDate(date);
  };

  const handleRecommendationDetail = (stock) => {
    navigation.navigate('RecommendationDetail', {
      stockCode: stock.stockCode,
      stockName: stock.stockName,
      recommendation: stock,
    });
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

  // 获取风险等级颜色
  const getRiskLevelColor = (riskLevel) => {
    switch (riskLevel) {
      case '低': return theme.colors.profit;
      case '中': return theme.colors.warning;
      case '高': return theme.colors.loss;
      default: return theme.colors.textSecondary;
    }
  };

  if (loading && !refreshing) {
    return (
      <View style={[styles.container, { justifyContent: 'center', alignItems: 'center' }]}>
        <ActivityIndicator size="large" color={theme.colors.primary} />
        <Paragraph style={{ marginTop: 16 }}>加载历史数据...</Paragraph>
      </View>
    );
  }

  return (
    <Modal
      visible={true}
      animationType="slide"
      presentationStyle="pageSheet"
    >
      <View style={styles.container}>
        {/* 模态框头部 */}
        <View style={modalHeaderStyles.header}>
          <View style={modalHeaderStyles.headerLeft}>
            <Text style={modalHeaderStyles.headerTitle}>历史推荐</Text>
          </View>
          
          <View style={modalHeaderStyles.headerActions}>
            <TouchableOpacity style={modalHeaderStyles.actionButton} onPress={() => navigation.goBack()}>
              <Ionicons name="close" size={20} color="#8E8E93" />
            </TouchableOpacity>
          </View>
        </View>

        {/* 模态框内容 */}
        <ScrollView 
          style={modalHeaderStyles.content} 
          showsVerticalScrollIndicator={true}
          bounces={true}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
          }
        >
          {/* 日期选择器 */}
          <Card style={styles.card}>
            <Card.Content>
              <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                <Title>选择日期</Title>
                <TouchableOpacity
                  onPress={() => setMenuVisible(true)}
                  style={dateSelectorStyles.selector}
                >
                  <Text style={dateSelectorStyles.selectedDate}>
                    {selectedDate || '请选择日期'}
                  </Text>
                  <Ionicons name="chevron-down" size={20} color={theme.colors.primary} />
                </TouchableOpacity>
              </View>
            </Card.Content>
          </Card>

          {/* 推荐数据 */}
          {recommendationData ? (
            <>
              {/* 推荐摘要 */}
              <Card style={styles.card}>
                <Card.Content>
                  <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                    <Title>🤖 AI推荐摘要</Title>
                    <Chip
                      compact
                      style={{ backgroundColor: theme.colors.primary, borderRadius: 10 }}
                      contentStyle={{ height: 16, paddingHorizontal: 6 }}
                      textStyle={{ color: 'white', fontWeight: '600', fontSize: 10, textAlignVertical: 'center' }}
                    >
                      共{recommendationData.totalCount}只
                    </Chip>
                  </View>
                  
                  <Paragraph style={styles.smallText}>
                    {recommendationData.date} | 基于AI分析的优质股票推荐
                  </Paragraph>

                  {/* 市场信息摘要 */}
                  {(recommendationData.marketOverview || recommendationData.policyHotspots || recommendationData.industryHotspots) && (
                    <View style={{ marginVertical: 12, padding: 12, backgroundColor: theme.colors.surface, borderRadius: 12, borderWidth: 1, borderColor: '#eee' }}>
                      {recommendationData.marketOverview && (
                        <View style={{ flexDirection: 'row', marginBottom: 6 }}>
                          <Text style={{ fontWeight: 'bold', width: 80 }}>市场概况:</Text>
                          <Text style={[styles.smallText]} numberOfLines={3}>{recommendationData.marketOverview}</Text>
                        </View>
                      )}
                      {recommendationData.policyHotspots && (
                        <View style={{ flexDirection: 'row', marginBottom: 6 }}>
                          <Text style={{ fontWeight: 'bold', width: 80 }}>政策热点:</Text>
                          <Text style={[styles.smallText]} numberOfLines={3}>{recommendationData.policyHotspots}</Text>
                        </View>
                      )}
                      {recommendationData.industryHotspots && (
                        <View style={{ flexDirection: 'row' }}>
                          <Text style={{ fontWeight: 'bold', width: 80 }}>行业热点:</Text>
                          <Text style={[styles.smallText]} numberOfLines={3}>{recommendationData.industryHotspots}</Text>
                        </View>
                      )}
                    </View>
                  )}

                  <Divider style={{ marginVertical: 8 }} />

                  {/* 推荐股票列表 */}
                  {recommendationData.topStocks?.map((stock, index) => (
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
                  {recommendationData.summary && (
                    <View style={{ marginTop: 16, padding: 12, backgroundColor: theme.colors.surface, borderRadius: 12, borderWidth: 1, borderColor: '#eee' }}>
                      <Text style={{ fontWeight: 'bold', marginBottom: 8 }}>推荐总结</Text>
                      <Text style={[styles.smallText, { fontStyle: 'italic' }]}>
                        {recommendationData.summary}
                      </Text>
                    </View>
                  )}
                  
                  {recommendationData.analystView && (
                    <View style={{ marginTop: 12, padding: 12, backgroundColor: theme.colors.surface, borderRadius: 12, borderWidth: 1, borderColor: '#eee' }}>
                      <Text style={{ fontWeight: 'bold', marginBottom: 8 }}>AI分析师观点</Text>
                      <Text style={[styles.smallText]}>
                        {recommendationData.analystView}
                      </Text>
                    </View>
                  )}
                </Card.Content>
              </Card>
            </>
          ) : (
            <Card style={styles.card}>
              <Card.Content>
                <View style={{ alignItems: 'center', paddingVertical: 20 }}>
                  <Paragraph style={styles.text}>
                    {selectedDate ? '该日期暂无推荐数据' : '请选择日期查看历史推荐'}
                  </Paragraph>
                </View>
              </Card.Content>
            </Card>
          )}
        </ScrollView>

        {/* 日期选择菜单 - 使用绝对定位 */}
        {menuVisible && (
          <>
            {/* 透明覆盖层，点击关闭菜单 */}
            <TouchableOpacity
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: 0,
                bottom: 0,
                backgroundColor: 'transparent',
                zIndex: 999,
              }}
              activeOpacity={1}
              onPress={() => setMenuVisible(false)}
            />
            <View style={{
              position: 'absolute',
              top: 100, // 在头部下方
              right: 20,
              backgroundColor: theme.colors.surface,
              borderRadius: 8,
              elevation: 8,
              shadowColor: '#000',
              shadowOffset: { width: 0, height: 2 },
              shadowOpacity: 0.25,
              shadowRadius: 3.84,
              minWidth: 150,
              zIndex: 1000,
            }}>
            {availableDates.length > 0 ? (
              availableDates.map((date) => (
                <TouchableOpacity
                  key={date}
                  onPress={() => handleDateSelect(date)}
                  style={{
                    paddingHorizontal: 16,
                    paddingVertical: 12,
                    borderBottomWidth: 1,
                    borderBottomColor: theme.colors.outline,
                    backgroundColor: date === selectedDate ? theme.colors.primary : 'transparent',
                  }}
                >
                  <Text style={{
                    color: date === selectedDate ? 'white' : theme.colors.text,
                    fontWeight: date === selectedDate ? 'bold' : 'normal',
                    fontSize: 16,
                  }}>
                    {date}
                  </Text>
                </TouchableOpacity>
              ))
            ) : (
              <View style={{
                paddingHorizontal: 16,
                paddingVertical: 12,
                alignItems: 'center',
              }}>
                <Text style={{
                  color: theme.colors.textSecondary,
                  fontStyle: 'italic',
                  fontSize: 16,
                }}>
                  暂无可用日期
                </Text>
              </View>
            )}
          </View>
        </>
      )}
    </View>
  </Modal>
 );
}

const dateSelectorStyles = StyleSheet.create({
  selector: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderWidth: 1,
    borderColor: theme.colors.outline,
    borderRadius: 8,
    backgroundColor: theme.colors.surface,
  },
  selectedDate: {
    fontSize: 16,
    color: theme.colors.text,
    marginRight: 8,
  },
});
