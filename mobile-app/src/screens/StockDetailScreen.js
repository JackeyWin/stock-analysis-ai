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
  SegmentedButtons,
  FAB,
} from 'react-native-paper';
import { LineChart, BarChart } from 'react-native-chart-kit';
import { theme, styles } from '../utils/theme';
import ApiService from '../services/ApiService';

const { width: screenWidth } = Dimensions.get('window');

export default function StockDetailScreen({ route, navigation }) {
  const { stockCode, stockName } = route.params;
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [analysisData, setAnalysisData] = useState(null);
  const [viewMode, setViewMode] = useState('overview');
  const [fabVisible, setFabVisible] = useState(true);

  const viewModes = [
    { value: 'overview', label: '概览' },
    { value: 'technical', label: '技术' },
    { value: 'risk', label: '风险' },
  ];

  useEffect(() => {
    navigation.setOptions({
      title: `${stockName} (${stockCode})`,
    });
    loadStockData();
  }, [stockCode, stockName, navigation]);

  const loadStockData = async () => {
    try {
      setLoading(true);
      const result = await ApiService.analyzeStock(stockCode);
      
      if (result.success) {
        setAnalysisData(result);
      } else {
        Alert.alert('错误', result.message || '加载股票数据失败');
      }
    } catch (error) {
      console.error('加载股票数据失败:', error);
      Alert.alert('错误', error.message || '加载股票数据失败');
    } finally {
      setLoading(false);
    }
  };

  const onRefresh = async () => {
    setRefreshing(true);
    await loadStockData();
    setRefreshing(false);
  };

  const handleQuickAnalysis = async () => {
    try {
      setLoading(true);
      const result = await ApiService.quickAnalyze(stockCode);
      
      if (result.success) {
        Alert.alert(
          '快速分析结果',
          `技术指标分析完成\n建议: ${result.aiAnalysisResult?.summary || '请查看详细数据'}`,
          [{ text: '确定' }]
        );
      } else {
        Alert.alert('错误', result.message || '快速分析失败');
      }
    } catch (error) {
      Alert.alert('错误', error.message || '快速分析失败');
    } finally {
      setLoading(false);
    }
  };

  const handleRiskAssessment = async () => {
    try {
      setLoading(true);
      const result = await ApiService.assessRisk(stockCode);
      
      if (result.success) {
        Alert.alert(
          '风险评估结果',
          result.riskAssessment || '风险评估完成，请查看详细报告',
          [{ text: '确定' }]
        );
      } else {
        Alert.alert('错误', result.message || '风险评估失败');
      }
    } catch (error) {
      Alert.alert('错误', error.message || '风险评估失败');
    } finally {
      setLoading(false);
    }
  };

  // 生成模拟价格走势图
  const getPriceChart = () => {
    const data = {
      labels: ['9:30', '10:00', '10:30', '11:00', '11:30', '14:00', '14:30', '15:00'],
      datasets: [
        {
          data: [12.5, 12.8, 12.3, 12.9, 13.2, 12.7, 13.1, 13.0],
          color: (opacity = 1) => `rgba(33, 150, 243, ${opacity})`,
          strokeWidth: 2,
        },
      ],
    };

    return (
      <LineChart
        data={data}
        width={screenWidth - 64}
        height={220}
        chartConfig={{
          backgroundColor: theme.colors.surface,
          backgroundGradientFrom: theme.colors.surface,
          backgroundGradientTo: theme.colors.surface,
          decimalPlaces: 2,
          color: (opacity = 1) => `rgba(33, 150, 243, ${opacity})`,
          labelColor: (opacity = 1) => `rgba(0, 0, 0, ${opacity})`,
          style: {
            borderRadius: 16,
          },
          propsForDots: {
            r: '4',
            strokeWidth: '2',
            stroke: theme.colors.primary,
          },
        }}
        bezier
        style={{
          marginVertical: 8,
          borderRadius: 16,
        }}
      />
    );
  };

  // 生成技术指标图表
  const getTechnicalChart = () => {
    const data = {
      labels: ['RSI', 'MACD', 'KDJ', 'BOLL', 'MA5', 'MA20'],
      datasets: [
        {
          data: [65, 45, 78, 56, 89, 72],
        },
      ],
    };

    return (
      <BarChart
        data={data}
        width={screenWidth - 64}
        height={220}
        chartConfig={{
          backgroundColor: theme.colors.surface,
          backgroundGradientFrom: theme.colors.surface,
          backgroundGradientTo: theme.colors.surface,
          decimalPlaces: 1,
          color: (opacity = 1) => `rgba(76, 175, 80, ${opacity})`,
          labelColor: (opacity = 1) => `rgba(0, 0, 0, ${opacity})`,
          style: {
            borderRadius: 16,
          },
        }}
        style={{
          marginVertical: 8,
          borderRadius: 16,
        }}
      />
    );
  };

  if (loading && !refreshing) {
    return (
      <View style={[styles.container, { justifyContent: 'center', alignItems: 'center' }]}>
        <ActivityIndicator size="large" color={theme.colors.primary} />
        <Paragraph style={{ marginTop: 16 }}>加载股票数据中...</Paragraph>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <ScrollView
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }
      >
        {/* 视图模式选择 */}
        <View style={{ padding: 16 }}>
          <SegmentedButtons
            value={viewMode}
            onValueChange={setViewMode}
            buttons={viewModes}
          />
        </View>

        {/* 概览视图 */}
        {viewMode === 'overview' && (
          <>
            {/* 基本信息 */}
            <Card style={styles.card}>
              <Card.Content>
                <Title>{stockName}</Title>
                <Paragraph style={styles.smallText}>代码: {stockCode}</Paragraph>
                <Divider style={{ marginVertical: 8 }} />
                
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginVertical: 8 }}>
                  <View>
                    <Paragraph style={styles.smallText}>当前价格</Paragraph>
                    <Paragraph style={[styles.text, { fontSize: 24, fontWeight: 'bold' }]}>
                      ¥13.05
                    </Paragraph>
                  </View>
                  <View style={{ alignItems: 'flex-end' }}>
                    <Paragraph style={styles.smallText}>涨跌幅</Paragraph>
                    <Paragraph style={[styles.profitText, { fontSize: 18, fontWeight: 'bold' }]}>
                      +2.35%
                    </Paragraph>
                  </View>
                </View>

                <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 8 }}>
                  <Chip icon="trending-up" style={{ backgroundColor: theme.colors.profit }}>
                    <Paragraph style={{ color: 'white' }}>上涨</Paragraph>
                  </Chip>
                  <Chip icon="volume-high">
                    成交量: 1.2M
                  </Chip>
                </View>
              </Card.Content>
            </Card>

            {/* 价格走势图 */}
            <Card style={styles.card}>
              <Card.Content>
                <Title>价格走势</Title>
                <Paragraph style={styles.smallText}>今日分时图</Paragraph>
                {getPriceChart()}
              </Card.Content>
            </Card>

            {/* AI分析结果 */}
            {analysisData?.aiAnalysisResult && (
              <Card style={styles.card}>
                <Card.Content>
                  <Title>AI分析摘要</Title>
                  <Divider style={{ marginVertical: 8 }} />
                  <Paragraph style={styles.text}>
                    {analysisData.aiAnalysisResult.summary || '分析完成，建议关注技术指标变化'}
                  </Paragraph>
                  
                  <View style={{ flexDirection: 'row', flexWrap: 'wrap', marginTop: 8 }}>
                    <Chip style={{ margin: 2 }} icon="brain">
                      AI分析
                    </Chip>
                    <Chip style={{ margin: 2 }} icon="chart-line">
                      趋势预测
                    </Chip>
                  </View>
                </Card.Content>
              </Card>
            )}
          </>
        )}

        {/* 技术分析视图 */}
        {viewMode === 'technical' && (
          <>
            <Card style={styles.card}>
              <Card.Content>
                <Title>技术指标</Title>
                <Paragraph style={styles.smallText}>主要技术指标分析</Paragraph>
                {getTechnicalChart()}
                
                <Divider style={{ marginVertical: 8 }} />
                
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginVertical: 4 }}>
                  <Paragraph style={styles.text}>RSI (14)</Paragraph>
                  <Paragraph style={styles.text}>65.2</Paragraph>
                </View>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginVertical: 4 }}>
                  <Paragraph style={styles.text}>MACD</Paragraph>
                  <Paragraph style={styles.profitText}>0.15</Paragraph>
                </View>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginVertical: 4 }}>
                  <Paragraph style={styles.text}>KDJ</Paragraph>
                  <Paragraph style={styles.text}>78.5</Paragraph>
                </View>
              </Card.Content>
            </Card>

            <Card style={styles.card}>
              <Card.Content>
                <Title>移动平均线</Title>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginVertical: 4 }}>
                  <Paragraph style={styles.text}>MA5</Paragraph>
                  <Paragraph style={styles.text}>¥12.85</Paragraph>
                </View>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginVertical: 4 }}>
                  <Paragraph style={styles.text}>MA20</Paragraph>
                  <Paragraph style={styles.text}>¥12.45</Paragraph>
                </View>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginVertical: 4 }}>
                  <Paragraph style={styles.text}>MA60</Paragraph>
                  <Paragraph style={styles.text}>¥11.95</Paragraph>
                </View>
              </Card.Content>
            </Card>
          </>
        )}

        {/* 风险分析视图 */}
        {viewMode === 'risk' && (
          <>
            <Card style={styles.card}>
              <Card.Content>
                <Title>风险评估</Title>
                <Paragraph style={styles.smallText}>基于历史数据和技术指标的风险分析</Paragraph>
                <Divider style={{ marginVertical: 8 }} />
                
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginVertical: 8 }}>
                  <Paragraph style={styles.text}>整体风险等级</Paragraph>
                  <Chip style={{ backgroundColor: theme.colors.warning }}>
                    <Paragraph style={{ color: 'white' }}>中等</Paragraph>
                  </Chip>
                </View>
                
                <View style={{ marginVertical: 8 }}>
                  <Paragraph style={styles.subHeader}>风险因素</Paragraph>
                  <Paragraph style={styles.text}>• 市场波动性较高</Paragraph>
                  <Paragraph style={styles.text}>• 技术指标显示超买状态</Paragraph>
                  <Paragraph style={styles.text}>• 成交量放大需要关注</Paragraph>
                </View>
                
                <View style={{ marginVertical: 8 }}>
                  <Paragraph style={styles.subHeader}>投资建议</Paragraph>
                  <Paragraph style={styles.text}>建议谨慎操作，关注支撑位和阻力位</Paragraph>
                </View>
              </Card.Content>
            </Card>
          </>
        )}

        {/* 操作按钮 */}
        <Card style={styles.card}>
          <Card.Content>
            <Title>分析工具</Title>
            <View style={{ flexDirection: 'row', gap: 8, marginTop: 8 }}>
              <Button
                mode="contained"
                icon="flash"
                onPress={handleQuickAnalysis}
                disabled={loading}
                style={{ flex: 1 }}
              >
                快速分析
              </Button>
              <Button
                mode="outlined"
                icon="shield-check"
                onPress={handleRiskAssessment}
                disabled={loading}
                style={{ flex: 1 }}
              >
                风险评估
              </Button>
            </View>
          </Card.Content>
        </Card>
      </ScrollView>

      {/* 浮动操作按钮 */}
      <FAB
        icon="refresh"
        style={{
          position: 'absolute',
          margin: 16,
          right: 0,
          bottom: 0,
          backgroundColor: theme.colors.primary,
        }}
        onPress={onRefresh}
        visible={fabVisible}
        loading={refreshing}
      />
    </View>
  );
}
