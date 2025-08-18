import React, { useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  Alert,
  Share,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';

const AnalysisResultView = ({ result, stockCode, onClose }) => {
  // 默认展开"公司基本面分析"、"行业趋势和政策导向"、"操作策略"、"盘面分析"
  const [expandedSections, setExpandedSections] = useState(new Set(['companyFundamentalAnalysis', 'industryPolicyOrientation', 'operationStrategy', 'intradayOperations']));

  // 获取股票名称
  const getStockName = () => {
    if (result?.stockBasic?.stockName) {
      return result.stockBasic.stockName;
    }
    if (result?.stockName) {
      return result.stockName;
    }
    if (result?.aiAnalysisResult?.stockName) {
      return result.aiAnalysisResult.stockName;
    }
    return '';
  };

  // 智能时间显示
  const getTimeDisplay = () => {
    if (!result?.timestamp) {
      return '刚刚';
    }
    
    const now = new Date();
    const analysisTime = new Date(result.timestamp);
    const diffMs = now - analysisTime;
    const diffMinutes = Math.floor(diffMs / (1000 * 60));
    
    if (diffMinutes < 60) {
      return '刚刚';
    } else {
      return analysisTime.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
      });
    }
  };

  const toggleSection = (sectionKey) => {
    const newExpanded = new Set(expandedSections);
    if (newExpanded.has(sectionKey)) {
      newExpanded.delete(sectionKey);
    } else {
      newExpanded.add(sectionKey);
    }
    setExpandedSections(newExpanded);
  };

  const shareResult = async () => {
    try {
      // 优先使用fullAnalysis完整内容，然后是aiAnalysisResult中的summary
      let shareContent = '';
      
      // 尝试获取完整的AI分析内容
      if (result?.aiAnalysisResult?.fullAnalysis) {
        shareContent = result.aiAnalysisResult.fullAnalysis;
      } else if (result?.aiAnalysis?.fullAnalysis) {
        shareContent = result.aiAnalysis.fullAnalysis;
      } else if (result?.fullAnalysis) {
        shareContent = result.fullAnalysis;
      } else {
        // 如果没有完整内容，使用摘要
        shareContent = result?.aiAnalysisResult?.summary || 
                      result?.aiAnalysis?.summary || 
                      result?.summary || 
                      '暂无分析结果';
      }
      
      const message = `${stockCode} 股票分析结果：\n\n${shareContent}`;
      
      await Share.share({
        message,
        title: `${stockCode} 股票分析`,
      });
    } catch (error) {
      Alert.alert('分享失败', error.message);
    }
  };

  const renderSection = (title, content, sectionKey, icon) => {
    const isExpanded = expandedSections.has(sectionKey);
    
    return (
      <View style={styles.section}>
        <TouchableOpacity 
          style={styles.sectionHeader}
          onPress={() => toggleSection(sectionKey)}
        >
          <View style={styles.sectionTitleContainer}>
            <Ionicons name={icon} size={20} color="#007AFF" />
            <Text style={styles.sectionTitle}>{title}</Text>
          </View>
          <Ionicons 
            name={isExpanded ? 'chevron-up' : 'chevron-down'} 
            size={20} 
            color="#8E8E93" 
          />
        </TouchableOpacity>
        
        {isExpanded && (
          <View style={styles.sectionContent}>
            {typeof content === 'string' ? (
              <Text style={styles.contentText}>{content}</Text>
            ) : (
              content
            )}
          </View>
        )}
      </View>
    );
  };

  const renderRecommendation = (recommendation) => {
    if (!recommendation) return null;

    const getRecommendationColor = (action) => {
      switch (action?.toLowerCase()) {
        case 'buy':
        case '买入':
          return '#34C759';
        case 'sell':
        case '卖出':
          return '#FF3B30';
        case 'hold':
        case '持有':
          return '#FF9500';
        default:
          return '#8E8E93';
      }
    };

    const getRecommendationIcon = (action) => {
      switch (action?.toLowerCase()) {
        case 'buy':
        case '买入':
          return 'trending-up';
        case 'sell':
        case '卖出':
          return 'trending-down';
        case 'hold':
        case '持有':
          return 'remove';
        default:
          return 'help-circle';
      }
    };

    return (
      <View style={styles.recommendationContainer}>
        <View style={styles.recommendationHeader}>
          <Ionicons 
            name={getRecommendationIcon(recommendation.action)} 
            size={24} 
            color={getRecommendationColor(recommendation.action)} 
          />
          <Text style={[
            styles.recommendationAction, 
            { color: getRecommendationColor(recommendation.action) }
          ]}>
            {recommendation.action || '建议'}
          </Text>
        </View>
        
        {recommendation.confidence && (
          <View style={styles.confidenceContainer}>
            <Text style={styles.confidenceLabel}>置信度:</Text>
            <View style={styles.confidenceBar}>
              <View 
                style={[
                  styles.confidenceFill, 
                  { 
                    width: `${recommendation.confidence}%`,
                    backgroundColor: getRecommendationColor(recommendation.action)
                  }
                ]} 
              />
            </View>
            <Text style={styles.confidenceText}>{recommendation.confidence}%</Text>
          </View>
        )}
        
        {recommendation.reason && (
          <Text style={styles.recommendationReason}>{recommendation.reason}</Text>
        )}
      </View>
    );
  };

  // 提取AI分析结果的关键部分 - 优先使用aiAnalysisResult字段，排除fullAnalysis
  const aiAnalysis = (() => {
    const analysis = result?.aiAnalysisResult || result?.aiAnalysis || result?.analysis || {};
    if (typeof analysis === 'object' && analysis !== null) {
      // 创建一个新对象，排除fullAnalysis字段
      const { fullAnalysis, ...cleanAnalysis } = analysis;
      return cleanAnalysis;
    }
    return analysis;
  })();
  

  


  // 通用内容格式化：处理多行内容，支持[H]/[S]标签；小标题( [H] )加粗且字号变大；子标题( [S] )加粗但内容不换行
  const renderFormattedContent = (text) => {
    if (!text) return null;
    
    // 按换行符分割内容
    const lines = text.split('\n')
      .map(line => line.trim())
      .filter(line => line.length > 0);
    
    return (
      <View>
        {lines.map((line, lineIndex) => {
          // 仅处理以"-"开头的结构化行
          if (line.startsWith('-')) {
            // [H] 小标题：- [H] 标题：内容(可选)
            if (/^\-\s*\[H\]\s*/.test(line)) {
              const rest = line.replace(/^\-\s*\[H\]\s*/, '');
              const colonIndex = rest.indexOf('：');
              const title = colonIndex !== -1 ? rest.substring(0, colonIndex) : rest;
              const content = colonIndex !== -1 ? rest.substring(colonIndex + 1).trim() : '';
              return (
                <View key={lineIndex} style={{ marginBottom: 8 }}>
                  <Text style={[styles.contentText, { fontWeight: 'bold', fontSize: 16, color: '#007AFF', marginBottom: 4 }]}>
                    {title}
                  </Text>
                  {content ? (
                    <Text style={[styles.contentText, { marginLeft: 16 }]}>
                      {content}
                    </Text>
                  ) : null}
                </View>
              );
            }
            
            // [S] 子标题：- [S] 子标题：内容(同一行)
            if (/^\-\s*\[S\]\s*/.test(line)) {
              const rest = line.replace(/^\-\s*\[S\]\s*/, '');
              let idx = rest.indexOf('：');
              if (idx === -1) idx = rest.indexOf(':');
              const label = idx !== -1 ? rest.substring(0, idx) : rest;
              const content = idx !== -1 ? rest.substring(idx + 1).trim() : '';
              return (
                <View key={lineIndex} style={{ marginBottom: 6, flexDirection: 'row', alignItems: 'flex-start', marginLeft: 16 }}>
                  <Text style={[styles.contentText, { fontWeight: 'bold', fontSize: 14, color: '#333', marginRight: 8 }]}>
                    {label}{idx !== -1 ? '：' : ''}
                  </Text>
                  {content ? (
                    <Text style={[styles.contentText, { flex: 1, fontSize: 14 }]}>
                      {content}
                    </Text>
                  ) : null}
                </View>
              );
            }

            // 向后兼容：**小标题** 和 **子标题** 标记
            if (line.includes('**')) {
              // 小标题：- **标题**：内容
              const hMatch = line.match(/^\-\s*\*\*(.*?)\*\*\s*：/);
              if (hMatch) {
                const title = hMatch[1];
                const content = line.replace(/^\-\s*\*\*(.*?)\*\*\s*：/, '').trim();
                return (
                  <View key={lineIndex} style={{ marginBottom: 8 }}>
                    <Text style={[styles.contentText, { fontWeight: 'bold', fontSize: 16, color: '#007AFF', marginBottom: 4 }]}>
                      {title}
                    </Text>
                    {content ? (
                      <Text style={[styles.contentText, { marginLeft: 16 }]}>
                        {content}
                      </Text>
                    ) : null}
                  </View>
                );
              }

              // 子标题：- **子标题**：内容（同一行）
              const sMatch = line.match(/^\-\s*\*\*(.*?)\*\*\s*：/);
              if (sMatch) {
                const subTitle = sMatch[1];
                const content = line.replace(/^\-\s*\*\*(.*?)\*\*\s*：/, '').trim();
                return (
                  <View key={lineIndex} style={{ marginBottom: 6, flexDirection: 'row', alignItems: 'flex-start', marginLeft: 16 }}>
                    <Text style={[styles.contentText, { fontWeight: 'bold', fontSize: 14, color: '#333', marginRight: 8 }]}>
                      {subTitle}：
                    </Text>
                    <Text style={[styles.contentText, { flex: 1, fontSize: 14 }]}>
                      {content}
                    </Text>
                  </View>
                );
              }
            }

            // 普通 "-" 行：按第一个冒号分两行显示（标题加粗，内容缩进）
            const colonIndex = line.indexOf('：');
            if (colonIndex !== -1) {
              const title = line.substring(0, colonIndex + 1);
              const content = line.substring(colonIndex + 1).trim();
              return (
                <View key={lineIndex} style={{ marginBottom: 4 }}>
                  <Text style={[styles.contentText, { fontWeight: 'bold' }]}>
                    {title}
                  </Text>
                  {content ? (
                    <Text style={[styles.contentText, { marginLeft: 8 }]}>
                      {content}
                    </Text>
                  ) : null}
                </View>
              );
            }

            // 没有冒号：整行加粗
            return (
              <Text key={lineIndex} style={[styles.contentText, { fontWeight: 'bold', marginBottom: 4 }]}>
                {line}
              </Text>
            );
          }

          // 非结构化行：普通文本
          return (
            <Text key={lineIndex} style={[styles.contentText, { marginBottom: 4 }]}>
              {line}
            </Text>
          );
        })}
      </View>
    );
  };

  // 操作策略内容格式化：直接显示内容，保持换行格式
  const renderOperationStrategy = (text) => {
    return renderFormattedContent(text);
  };

  // 智能提取AI分析内容
  const extractAiContent = () => {
    const sections = [];
    
    
    
    // 只处理对象类型的AI分析结果，忽略字符串类型的fullAnalysis
    if (typeof aiAnalysis === 'object' && aiAnalysis !== null) {
      // 定义显示顺序
      const displayOrder = [
        'companyFundamentalAnalysis',
        'industryPolicyOrientation', 
        'operationStrategy',
        'intradayOperations'
      ];
      
      // 按照指定顺序处理字段
      displayOrder.forEach(key => {
        const value = aiAnalysis[key];
        if (value && typeof value === 'string' && value.trim()) {
          
          
          let title = key;
          let sectionKey = key;
          
          // 映射字段名到中文标题 - 根据aiAnalysisResult字段名
          const titleMap = {
            'summary': 'AI分析摘要',
            'trendAnalysis': '趋势分析',
            'technicalPattern': '技术形态',
            'movingAverage': '移动平均线',
            'rsiAnalysis': 'RSI指标',
            'pricePredict': '价格预测',
            'tradingAdvice': '交易建议',
            'companyFundamentalAnalysis': '公司基本面分析',
            'industryPolicyOrientation': '行业趋势和政策导向',
            'operationStrategy': '操作策略',
            'intradayOperations': '盘面分析',
            'recommendation': '投资建议',
            'riskAnalysis': '风险分析',
            'marketSentiment': '市场情绪',
            'outlook': '未来展望'
          };
          
          title = titleMap[key] || key;
          // 统一key（用于默认展开控制）
          if (key === 'companyFundamentalAnalysis') sectionKey = 'companyFundamentalAnalysis';
          else if (key === 'industryPolicyOrientation') sectionKey = 'industryPolicyOrientation';
          else if (key === 'operationStrategy') sectionKey = 'operationStrategy';
          else if (key === 'intradayOperations') sectionKey = 'intradayOperations';
          else sectionKey = `sec_${key}`;

          // 所有分析部分都做结构化渲染，保持多行格式
          const contentNode = renderFormattedContent(value);
          sections.push({ title, content: contentNode, key: sectionKey });
        }
      });
      
      // 处理其他字段（不在指定顺序中的）
      Object.entries(aiAnalysis).forEach(([key, value]) => {
        if (!displayOrder.includes(key) && value && typeof value === 'string' && value.trim()) {
          
          
          let title = key;
          let sectionKey = key;
          
          // 映射字段名到中文标题
          const titleMap = {
            'summary': 'AI分析摘要',
            'trendAnalysis': '趋势分析',
            'technicalPattern': '技术形态',
            'movingAverage': '移动平均线',
            'rsiAnalysis': 'RSI指标',
            'pricePredict': '价格预测',
            'tradingAdvice': '交易建议',
            'companyFundamentalAnalysis': '公司基本面分析',
            'industryPolicyOrientation': '行业趋势和政策导向',
            'operationStrategy': '操作策略',
            'intradayOperations': '盘面分析',
            'recommendation': '投资建议',
            'riskAnalysis': '风险分析',
            'marketSentiment': '市场情绪',
            'outlook': '未来展望'
          };
          
          title = titleMap[key] || key;
          sectionKey = `sec_${key}`;

          // 所有分析部分都做结构化渲染，保持多行格式
          const contentNode = renderFormattedContent(value);
          sections.push({ title, content: contentNode, key: sectionKey });
        }
      });
    }
    
    return sections;
  };

  // 提取AI分析内容
  const aiSections = extractAiContent();

  // 根据标题映射图标
  const getSectionIcon = (title) => {
    switch (title) {
      case '趋势分析':
        return 'trending-up-outline';
      case '技术形态':
        return 'analytics-outline';
      case '移动平均线':
        return 'bar-chart-outline';
      case 'RSI指标':
        return 'speedometer-outline';
      case '价格预测':
        return 'calendar-outline';
      case '交易建议':
        return 'trending-up-outline';
      case '公司基本面分析':
        return 'business-outline';
      case '行业趋势和政策导向':
        return 'trending-up-outline';
      case '操作策略':
        return 'compass-outline';
      case '盘面分析':
        return 'time-outline';
      case '投资建议':
        return 'trending-up-outline';
      case '风险分析':
        return 'warning-outline';
      case '市场情绪':
        return 'people-outline';
      case '未来展望':
        return 'calendar-outline';
      default:
        return 'help-circle'; // 默认图标
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <View style={styles.stockInfo}>
            <Text style={styles.stockCode}>{stockCode}</Text>
            {getStockName() && (
              <Text style={styles.stockName}> - {getStockName()}</Text>
            )}
          </View>
          <Text style={styles.analysisTime}>
            {getTimeDisplay()}
          </Text>
        </View>
        
        <View style={styles.headerActions}>
          <TouchableOpacity style={styles.actionButton} onPress={shareResult}>
            <Ionicons name="share-outline" size={20} color="#007AFF" />
          </TouchableOpacity>
          <TouchableOpacity style={styles.actionButton} onPress={onClose}>
            <Ionicons name="close" size={20} color="#8E8E93" />
          </TouchableOpacity>
        </View>
      </View>

      <ScrollView style={styles.content} showsVerticalScrollIndicator={false}>
        {/* 使用智能提取的AI分析内容 */}
        {aiSections.length > 0 ? (
          aiSections.map((section, index) => (
            <View key={index}>
              {renderSection(
                section.title,
                section.content,
                section.key || `ai_section_${index}`,
                getSectionIcon(section.title)
              )}
            </View>
          ))
                 ) : (
           // 如果没有提取到AI分析内容，尝试显示各种可能的内容
           <>
             {/* 尝试显示AI分析摘要 */}
             {aiAnalysis.summary && renderSection(
               'AI分析摘要', 
               aiAnalysis.summary, 
               'summary', 
               'bulb-outline'
             )}
             
             {/* 尝试显示result中的其他可能字段 */}
             {result && typeof result === 'object' && (
               <>
                 {result.summary && renderSection(
                   '分析摘要', 
                   result.summary, 
                   'result_summary', 
                   'document-outline'
                 )}
                 
                 {result.recommendation && renderSection(
                   '投资建议', 
                   renderRecommendation(result.recommendation), 
                   'result_recommendation', 
                   'trending-up-outline'
                 )}
               </>
             )}
             
             {/* 如果什么都没有，显示提示信息 */}
             {!aiAnalysis.summary && !result?.summary && !result?.recommendation && (
               <View style={styles.section}>
                 <View style={styles.sectionHeader}>
                   <View style={styles.sectionTitleContainer}>
                     <Ionicons name="warning-outline" size={20} color="#FF9500" />
                     <Text style={styles.sectionTitle}>暂无AI分析结果</Text>
                   </View>
                 </View>
                 <View style={styles.sectionContent}>
                   <Text style={styles.contentText}>
                     未能获取到AI分析结果，请检查网络连接或稍后重试。
                   </Text>
                 </View>
               </View>
             )}
           </>
         )}

        {/* 兼容旧格式的字段 */}
        {aiAnalysis.recommendation && !aiSections.some(s => s.title === '投资建议') && renderSection(
          '投资建议', 
          renderRecommendation(aiAnalysis.recommendation), 
          'recommendation', 
          'trending-up-outline'
        )}


      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F2F2F7',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    backgroundColor: '#FFFFFF',
    borderBottomWidth: 1,
    borderBottomColor: '#E5E5EA',
  },
  headerLeft: {
    flex: 1,
  },
  stockInfo: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  stockCode: {
    fontSize: 24,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  stockName: {
    fontSize: 20,
    fontWeight: '500',
    color: '#666666',
  },
  analysisTime: {
    fontSize: 12,
    color: '#8E8E93',
    marginTop: 4,
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
    padding: 16,
  },
  section: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    marginBottom: 16,
    overflow: 'hidden',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    backgroundColor: '#F8F9FA',
  },
  sectionTitleContainer: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1C1C1E',
    marginLeft: 8,
  },
  sectionContent: {
    padding: 16,
  },
  contentText: {
    fontSize: 14,
    lineHeight: 20,
    color: '#3A3A3C',
  },
  recommendationContainer: {
    backgroundColor: '#F8F9FA',
    borderRadius: 8,
    padding: 16,
  },
  recommendationHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  recommendationAction: {
    fontSize: 18,
    fontWeight: '700',
    marginLeft: 8,
  },
  confidenceContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  confidenceLabel: {
    fontSize: 14,
    color: '#8E8E93',
    marginRight: 8,
  },
  confidenceBar: {
    flex: 1,
    height: 6,
    backgroundColor: '#E5E5EA',
    borderRadius: 3,
    marginRight: 8,
    overflow: 'hidden',
  },
  confidenceFill: {
    height: '100%',
    borderRadius: 3,
  },
  confidenceText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#8E8E93',
    minWidth: 35,
  },
  recommendationReason: {
    fontSize: 14,
    lineHeight: 20,
    color: '#3A3A3C',
  },
});

export default AnalysisResultView;
