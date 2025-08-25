import React, { useState, useEffect } from 'react';
import {
  View,
  ScrollView,
  RefreshControl,
  Alert,
  Modal,
  TouchableOpacity,
  Text,
  StyleSheet,
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
  stockInfo: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 4,
  },
  stockCode: {
    fontSize: 18,
    fontWeight: 'bold',
    color: theme.colors.primary,
  },
  stockName: {
    fontSize: 16,
    color: theme.colors.text,
  },
  analysisTime: {
    fontSize: 14,
    color: theme.colors.placeholder,
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

export default function RecommendationDetailScreen({ route, navigation }) {
  const { stockCode, stockName, recommendation: initialRecommendation } = route.params;
  
  const [recommendation, setRecommendation] = useState(initialRecommendation);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);


  useEffect(() => {
    if (!recommendation) {
      loadRecommendationDetail();
    }
  }, []);

  const loadRecommendationDetail = async () => {
    try {
      setLoading(true);
      const response = await RecommendationService.getRecommendationDetail(stockCode);
      if (response.success) {
        setRecommendation(response.data);
      } else {
        Alert.alert('提示', response.message || '未找到推荐信息');
      }
    } catch (error) {
      console.error('加载推荐详情失败:', error);
      Alert.alert('错误', '加载推荐详情失败');
    } finally {
      setLoading(false);
    }
  };

  const onRefresh = async () => {
    setRefreshing(true);
    await loadRecommendationDetail();
    setRefreshing(false);
  };

  const handleAnalyzeStock = () => {
    navigation.navigate('StockDetail', {
      stockCode,
      stockName,
    });
  };

  const getRiskLevelColor = (riskLevel) => {
    switch (riskLevel) {
      case '低':
        return theme.colors.profit;
      case '中':
        return theme.colors.warning || '#FF9800';
      case '高':
        return theme.colors.loss;
      default:
        return theme.colors.text;
    }
  };

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
      <View style={[styles.container, { justifyContent: 'center', alignItems: 'center' }]}>
        <ActivityIndicator size="large" color={theme.colors.primary} />
        <Paragraph style={{ marginTop: 16 }}>加载推荐详情...</Paragraph>
      </View>
    );
  }

  if (!recommendation) {
    return (
      <View style={[styles.container, { justifyContent: 'center', alignItems: 'center' }]}>
        <Paragraph>未找到推荐信息</Paragraph>
        <Button mode="outlined" onPress={loadRecommendationDetail} style={{ marginTop: 16 }}>
          重新加载
        </Button>
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
            <View style={modalHeaderStyles.stockInfo}>
              <Text style={modalHeaderStyles.stockCode}>{stockCode}</Text>
              {recommendation.stockName && (
                <Text style={modalHeaderStyles.stockName}> - {recommendation.stockName}</Text>
              )}
            </View>
            <Text style={modalHeaderStyles.analysisTime}>
              推荐详情
            </Text>
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
          scrollEnabled={true}
        >

          {/* 股票基本信息 */}
          <Card style={styles.card}>
            <Card.Content>
              <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                <View style={{ flex: 1 }}>
                  <Title>{recommendation.stockName}</Title>
                  <Paragraph style={styles.smallText}>{recommendation.stockCode}</Paragraph>
                </View>
                {recommendation.isHot && (
                  <Chip
                    icon="fire"
                    style={{ backgroundColor: theme.colors.loss }}
                    textStyle={{ color: 'white' }}
                   >
                    热门推荐
                   </Chip>
                )}
              </View>
              
              <Divider style={{ marginVertical: 12 }} />
              
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 8 }}>
                <View style={{ alignItems: 'center' }}>
                  <Paragraph style={styles.smallText}>AI评分</Paragraph>
                  <Title style={{ color: theme.colors.primary }}>
                    {recommendation.score?.toFixed(1)}/10
                  </Title>
                </View>
                <View style={{ alignItems: 'center' }}>
                  <Paragraph style={styles.smallText}>推荐等级</Paragraph>
                  <Chip
                    style={{ backgroundColor: getRatingColor(recommendation.rating) }}
                    textStyle={{ color: 'white' }}
                  >
                    {recommendation.rating}
                  </Chip>
                </View>
                <View style={{ alignItems: 'center' }}>
                  <Paragraph style={styles.smallText}>风险等级</Paragraph>
                  <Chip
                    style={{ backgroundColor: getRiskLevelColor(recommendation.riskLevel) }}
                    textStyle={{ color: 'white' }}
                  >
                    {recommendation.riskLevel}风险
                  </Chip>
                </View>
              </View>
            </Card.Content>
          </Card>

          {/* 推荐信息 */}
          {recommendation.sector && (
            <Card style={styles.card}>
              <Card.Content>
                <Title>📊 推荐信息</Title>
                <Divider style={{ marginVertical: 8 }} />
                
                <View style={{ marginBottom: 12 }}>
                  <Paragraph style={[styles.text, { fontWeight: 'bold' }]}>所属领域</Paragraph>
                  <Chip
                    style={{ alignSelf: 'flex-start', marginTop: 4 }}
                    textStyle={{ color: theme.colors.primary }}
                  >
                    {recommendation.sector}
                  </Chip>
                </View>

                <View style={{ marginBottom: 12 }}>
                  <Paragraph style={[styles.text, { fontWeight: 'bold' }]}>投资时间建议</Paragraph>
                  <Paragraph style={styles.text}>{recommendation.investmentPeriod}</Paragraph>
                </View>

                {recommendation.targetPrice && (
                  <View style={{ marginBottom: 12 }}>
                    <Paragraph style={[styles.text, { fontWeight: 'bold' }]}>目标价格</Paragraph>
                    <Paragraph style={[styles.text, { color: theme.colors.profit }]}>
                      ¥{recommendation.targetPrice.toFixed(2)}
                    </Paragraph>
                  </View>
                )}

                {recommendation.expectedReturn && (
                  <View style={{ marginBottom: 12 }}>
                    <Paragraph style={[styles.text, { fontWeight: 'bold' }]}>预期涨幅</Paragraph>
                    <Paragraph style={[styles.text, { color: theme.colors.profit }]}>
                      +{recommendation.expectedReturn.toFixed(1)}%
                    </Paragraph>
                  </View>
                )}
              </Card.Content>
            </Card>
          )}

          {/* 推荐理由 */}
          <Card style={styles.card}>
            <Card.Content>
              <Title>💡 推荐理由</Title>
              <Divider style={{ marginVertical: 8 }} />
              <Surface style={{ padding: 12, borderRadius: 8 }}>
                <Paragraph style={styles.text}>
                  {recommendation.recommendationReason || '暂无详细推荐理由'}
                </Paragraph>
              </Surface>
            </Card.Content>
          </Card>

          {/* 操作按钮 */}
          <Card style={styles.card}>
            <Card.Content>
              <Title>🔧 操作</Title>
              <Divider style={{ marginVertical: 8 }} />
              
              <View style={{ flexDirection: 'row', justifyContent: 'space-around' }}>
                <Button
                  mode="contained"
                  icon="chart-line"
                  onPress={handleAnalyzeStock}
                  style={{ flex: 1, marginHorizontal: 4 }}
                >
                  详细分析
                </Button>
                <Button
                  mode="outlined"
                  icon="refresh"
                  onPress={onRefresh}
                  style={{ flex: 1, marginHorizontal: 4 }}
                >
                  刷新
                </Button>
              </View>
            </Card.Content>
          </Card>

          {/* 风险提示 */}
          <Card style={[styles.card, { backgroundColor: '#FFF3E0' }]}>
            <Card.Content>
              <Title style={{ color: '#E65100' }}>⚠️ 风险提示</Title>
              <Divider style={{ marginVertical: 8, backgroundColor: '#E65100' }} />
              <Paragraph style={[styles.smallText, { color: '#E65100' }]}>
                • 本推荐基于AI分析生成，仅供参考，不构成投资建议{'\n'}
                • 股市有风险，投资需谨慎，请根据自身情况做出投资决策{'\n'}
                • 建议设置止损位，控制投资风险{'\n'}
                • 市场环境变化较快，请及时关注相关信息更新
              </Paragraph>
            </Card.Content>
          </Card>

          {/* 投资建议 */}
          <Card style={styles.card}>
            <Card.Content>
              <Title>💼 投资建议</Title>
              <Divider style={{ marginVertical: 8 }} />
              <Surface style={{ padding: 12, borderRadius: 8 }}>
                <Paragraph style={styles.text}>
                  基于当前市场环境和AI分析结果，建议投资者：{'\n\n'}
                  • 关注市场整体趋势，把握投资时机{'\n'}
                  • 合理配置资产，控制单只股票仓位{'\n'}
                  • 设置止损位，严格执行风险控制{'\n'}
                  • 定期关注公司公告和行业动态{'\n'}
                  • 保持理性投资心态，避免追涨杀跌
                </Paragraph>
              </Surface>
            </Card.Content>
          </Card>

          {/* 底部间距 */}
          <View style={{ height: 50 }} />
        </ScrollView>
      </View>
    </Modal>
  );
}
