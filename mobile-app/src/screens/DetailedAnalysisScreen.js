import React, { useState, useEffect } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  Alert,
  ActivityIndicator,
} from 'react-native';
import {
  Card,
  Title,
  Paragraph,
  Divider,
  Surface,
  Button,
  Chip,
} from 'react-native-paper';
import { Ionicons } from '@expo/vector-icons';
import ApiService from '../services/ApiService';

export default function DetailedAnalysisScreen({ route, navigation }) {
  const { stockCode, stockName } = route.params;

  const [loading, setLoading] = useState(true);
  const [analysisData, setAnalysisData] = useState(null);
  const [error, setError] = useState(null);
  const [analysisStatus, setAnalysisStatus] = useState('INIT');
  const [progress, setProgress] = useState(0);
  const [taskId, setTaskId] = useState(null);
  const [elapsedTime, setElapsedTime] = useState(0);

  useEffect(() => {
    loadDetailedAnalysis();
  }, []);

  const loadDetailedAnalysis = async () => {
    try {
      setLoading(true);
      setError(null);
      setAnalysisStatus('INIT');
      setProgress(0);
      setElapsedTime(0);

      // 启动异步AI分析
      const startResponse = await ApiService.startAIDetailedAnalysis(stockCode);
      
      if (startResponse.success) {
        setTaskId(startResponse.taskId);
        setAnalysisStatus('PROCESSING');
        setProgress(10);
        
        // 开始轮询状态
        startStatusPolling(startResponse.taskId);
      } else {
        setError(startResponse.message || '启动分析失败');
        setLoading(false);
      }
    } catch (err) {
      console.error('启动详细分析失败:', err);
      setError('网络错误，请稍后重试');
      setLoading(false);
    }
  };

  const startStatusPolling = async (taskId) => {
    const pollInterval = setInterval(async () => {
      try {
        const statusResponse = await ApiService.getAIAnalysisStatus(taskId);
        
        if (statusResponse.success) {
          const statusData = statusResponse.data;
          setProgress(statusData.progress || 0);
          setElapsedTime(statusData.elapsedTime || 0);
          
          if (statusData.status === 'COMPLETED') {
            setAnalysisData(statusData.result);
            setAnalysisStatus('COMPLETED');
            setLoading(false);
            clearInterval(pollInterval);
          } else if (statusData.status === 'FAILED') {
            setError(statusData.errorMessage || '分析失败');
            setAnalysisStatus('FAILED');
            setLoading(false);
            clearInterval(pollInterval);
          }
        }
      } catch (err) {
        console.error('查询分析状态失败:', err);
        // 继续轮询，不中断
      }
    }, 1000);

    // 设置超时，避免无限等待
    setTimeout(() => {
      clearInterval(pollInterval);
      if (analysisStatus === 'PROCESSING') {
        setError('分析超时，请稍后重试');
        setAnalysisStatus('FAILED');
        setLoading(false);
      }
    }, 300000);
  };

  if (loading) {
    return (
      <View style={[styles.container, styles.centered]}>
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color="#2196F3" />
          
          {analysisStatus === 'PROCESSING' && (
            <>
              <Title style={styles.loadingTitle}>AI正在分析中...</Title>
              <Paragraph style={styles.loadingSubtitle}>
                正在分析股票 {stockCode} 的多维度数据
              </Paragraph>
              
              {/* 进度条 */}
              <View style={styles.progressContainer}>
                <View style={styles.progressBar}>
                  <View 
                    style={[
                      styles.progressFill, 
                      { width: `${progress}%`, backgroundColor: '#2196F3' }
                    ]} 
                  />
                </View>
                <Paragraph style={styles.progressText}>{progress}%</Paragraph>
              </View>
              
              {/* 进度详情 */}
              <View style={styles.progressDetails}>
                <View style={styles.progressStep}>
                  <View style={[styles.stepDot, progress >= 10 && styles.stepDotActive]} />
                  <Paragraph style={styles.stepText}>数据收集</Paragraph>
                </View>
                <View style={styles.progressStep}>
                  <View style={[styles.stepDot, progress >= 40 && styles.stepDotActive]} />
                  <Paragraph style={styles.stepText}>AI分析</Paragraph>
                </View>
                <View style={styles.progressStep}>
                  <View style={[styles.stepDot, progress >= 80 && styles.stepDotActive]} />
                  <Paragraph style={styles.stepText}>结果生成</Paragraph>
                </View>
              </View>
              
              {/* 耗时信息 */}
              <Paragraph style={styles.elapsedTime}>
                已耗时: {Math.floor(elapsedTime / 60)}分{elapsedTime % 60}秒
              </Paragraph>
              
              {/* 提示信息 */}
              <Paragraph style={styles.loadingTip}>
                分析过程可能需要2-5分钟，请耐心等待
              </Paragraph>
            </>
          )}
          
          {analysisStatus === 'INIT' && (
            <>
              <Title style={styles.loadingTitle}>正在启动AI分析...</Title>
              <Paragraph style={styles.loadingSubtitle}>请稍候</Paragraph>
            </>
          )}
        </View>
      </View>
    );
  }

  if (error) {
    return (
      <View style={[styles.container, styles.centered]}>
        <Ionicons name="alert-circle" size={64} color="#F44336" />
        <Title style={{ marginTop: 16, color: '#F44336' }}>分析失败</Title>
        <Paragraph style={{ marginTop: 8, textAlign: 'center' }}>{error}</Paragraph>
        <Button mode="contained" onPress={loadDetailedAnalysis} style={{ marginTop: 16 }}>
          重新分析
        </Button>
      </View>
    );
  }

  if (!analysisData) {
    return (
      <View style={[styles.container, styles.centered]}>
        <Paragraph>未找到分析数据</Paragraph>
        <Button mode="contained" onPress={loadDetailedAnalysis} style={{ marginTop: 16 }}>
          重新加载
        </Button>
      </View>
    );
  }

  const { aiAnalysis, rawData, analysisTimestamp } = analysisData;

  return (
    <View style={styles.container}>
      {/* 头部信息 */}
      <Card style={styles.headerCard}>
        <Card.Content>
          <View style={styles.headerRow}>
            <View style={styles.stockInfo}>
              <Title style={styles.stockCode}>{stockCode}</Title>
              {stockName && (
                <Paragraph style={styles.stockName}>{stockName}</Paragraph>
              )}
            </View>
            <View style={styles.analysisInfo}>
              <Chip icon="robot" mode="outlined" style={styles.aiChip}>
                AI分析
              </Chip>
              <Paragraph style={styles.timestamp}>
                {new Date(analysisTimestamp).toLocaleString()}
              </Paragraph>
            </View>
          </View>
        </Card.Content>
      </Card>

      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        {/* AI分析总结 */}
        {aiAnalysis?.summary && (
          <Card style={styles.card}>
            <Card.Content>
              <Title style={styles.cardTitle}>🎯 AI分析总结</Title>
              <Divider style={{ marginVertical: 8 }} />
              <Surface style={styles.contentSurface}>
                <Paragraph style={styles.contentText}>
                  {aiAnalysis.summary}
                </Paragraph>
              </Surface>
            </Card.Content>
          </Card>
        )}

        {/* 技术面分析 */}
        {aiAnalysis?.technicalAnalysis && (
          <Card style={styles.card}>
            <Card.Content>
              <Title style={styles.cardTitle}>📊 技术面分析</Title>
              <Divider style={{ marginVertical: 8 }} />
              <Surface style={styles.contentSurface}>
                <Paragraph style={styles.contentText}>
                  {aiAnalysis.technicalAnalysis}
                </Paragraph>
              </Surface>
            </Card.Content>
          </Card>
        )}

        {/* 资金面分析 */}
        {aiAnalysis?.capitalAnalysis && (
          <Card style={styles.card}>
            <Card.Content>
              <Title style={styles.cardTitle}>💰 资金面分析</Title>
              <Divider style={{ marginVertical: 8 }} />
              <Surface style={styles.contentSurface}>
                <Paragraph style={styles.contentText}>
                  {aiAnalysis.capitalAnalysis}
                </Paragraph>
              </Surface>
            </Card.Content>
          </Card>
        )}

        {/* 基本面分析 */}
        {aiAnalysis?.fundamentalAnalysis && (
          <Card style={styles.card}>
            <Card.Content>
              <Title style={styles.cardTitle}>🏢 基本面分析</Title>
              <Divider style={{ marginVertical: 8 }} />
              <Surface style={styles.contentSurface}>
                <Paragraph style={styles.contentText}>
                  {aiAnalysis.fundamentalAnalysis}
                </Paragraph>
              </Surface>
            </Card.Content>
          </Card>
        )}

        {/* 风险评估 */}
        {aiAnalysis?.riskAssessment && (
          <Card style={[styles.card, { backgroundColor: '#FFF3E0' }]}>
            <Card.Content>
              <Title style={[styles.cardTitle, { color: '#E65100' }]}>⚠️ 风险评估</Title>
              <Divider style={{ marginVertical: 8, backgroundColor: '#E65100' }} />
              <Surface style={[styles.contentSurface, { backgroundColor: '#FFF8E1' }]}>
                <Paragraph style={[styles.contentText, { color: '#E65100' }]}>
                  {aiAnalysis.riskAssessment}
                </Paragraph>
              </Surface>
            </Card.Content>
          </Card>
        )}

        {/* 投资策略 */}
        {aiAnalysis?.investmentStrategy && (
          <Card style={[styles.card, { backgroundColor: '#E8F5E8' }]}>
            <Card.Content>
              <Title style={[styles.cardTitle, { color: '#2E7D32' }]}>💼 投资策略</Title>
              <Divider style={{ marginVertical: 8, backgroundColor: '#2E7D32' }} />
              <Surface style={[styles.contentSurface, { backgroundColor: '#F1F8E9' }]}>
                <Paragraph style={[styles.contentText, { color: '#2E7D32' }]}>
                  {aiAnalysis.investmentStrategy}
                </Paragraph>
              </Surface>
            </Card.Content>
          </Card>
        )}

        {/* 操作建议 */}
        {aiAnalysis?.operationAdvice && (
          <Card style={styles.card}>
            <Card.Content>
              <Title style={styles.cardTitle}>📋 操作建议</Title>
              <Divider style={{ marginVertical: 8 }} />
              <Surface style={styles.contentSurface}>
                <Paragraph style={styles.contentText}>
                  {aiAnalysis.operationAdvice}
                </Paragraph>
              </Surface>
            </Card.Content>
          </Card>
        )}

        {/* 原始数据概览 */}
        {rawData && (
          <Card style={styles.card}>
            <Card.Content>
              <Title style={styles.cardTitle}>📈 数据概览</Title>
              <Divider style={{ marginVertical: 8 }} />
              <View style={styles.dataOverview}>
                {rawData.stockBasic && (
                  <Chip icon="information" style={styles.dataChip}>
                    基础数据
                  </Chip>
                )}
                {rawData.technicalIndicators && (
                  <Chip icon="trending-up" style={styles.dataChip}>
                    技术指标
                  </Chip>
                )}
                {rawData.moneyFlowData && (
                  <Chip icon="cash" style={styles.dataChip}>
                    资金流向
                  </Chip>
                )}
                {rawData.marginTradingData && (
                  <Chip icon="swap-horizontal" style={styles.dataChip}>
                    融资融券
                  </Chip>
                )}
                {rawData.peerComparison && (
                  <Chip icon="people" style={styles.dataChip}>
                    同行比较
                  </Chip>
                )}
              </View>
            </Card.Content>
          </Card>
        )}

        {/* 底部间距 */}
        <View style={{ height: 50 }} />
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  centered: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  headerCard: {
    margin: 16,
    elevation: 4,
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  stockInfo: {
    flex: 1,
  },
  stockCode: {
    fontSize: 24,
    fontWeight: 'bold',
  },
  stockName: {
    fontSize: 16,
    color: '#666',
    marginTop: 4,
  },
  analysisInfo: {
    alignItems: 'flex-end',
  },
  aiChip: {
    marginBottom: 8,
  },
  timestamp: {
    fontSize: 12,
    color: '#999',
  },
  scrollView: {
    flex: 1,
  },
  card: {
    margin: 16,
    marginTop: 0,
    elevation: 2,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: 'bold',
  },
  contentSurface: {
    padding: 16,
    borderRadius: 8,
    backgroundColor: '#fafafa',
  },
  contentText: {
    fontSize: 14,
    lineHeight: 20,
  },
  dataOverview: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  dataChip: {
    marginRight: 8,
    marginBottom: 8,
  },
  // 加载状态样式
  loadingContainer: {
    alignItems: 'center',
    padding: 32,
  },
  loadingTitle: {
    marginTop: 24,
    marginBottom: 8,
    textAlign: 'center',
  },
  loadingSubtitle: {
    textAlign: 'center',
    color: '#666',
    marginBottom: 24,
  },
  progressContainer: {
    width: '100%',
    marginBottom: 24,
  },
  progressBar: {
    height: 8,
    backgroundColor: '#E0E0E0',
    borderRadius: 4,
    overflow: 'hidden',
    marginBottom: 8,
  },
  progressFill: {
    height: '100%',
    borderRadius: 4,
  },
  progressText: {
    textAlign: 'center',
    fontSize: 16,
    fontWeight: 'bold',
    color: '#2196F3',
  },
  progressDetails: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    width: '100%',
    marginBottom: 24,
  },
  progressStep: {
    alignItems: 'center',
  },
  stepDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: '#E0E0E0',
    marginBottom: 8,
  },
  stepDotActive: {
    backgroundColor: '#2196F3',
  },
  stepText: {
    fontSize: 12,
    color: '#666',
    textAlign: 'center',
  },
  elapsedTime: {
    fontSize: 14,
    color: '#666',
    marginBottom: 16,
  },
  loadingTip: {
    fontSize: 12,
    color: '#999',
    textAlign: 'center',
    fontStyle: 'italic',
  },
});
