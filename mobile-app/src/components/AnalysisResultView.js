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
    // 优先从stockBasic中获取
    if (result?.stockBasic?.stockName) {
      return result.stockBasic.stockName;
    }
    
    // 从result的根级别获取
    if (result?.stockName) {
      return result.stockName;
    }
    
    // 从AI分析结果中获取
    if (result?.aiAnalysisResult?.stockName) {
      return result.aiAnalysisResult.stockName;
    }
    
    // 从AI分析结果中获取
    if (result?.aiAnalysis?.stockName) {
      return result.aiAnalysis.stockName;
    }
    
    // 如果都没有，返回空字符串
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
  

  


  // 获取风险等级颜色
  const getRiskColor = (text) => {
    if (text.includes('❗') || text.includes('⚠️')) {
      return '#FF3B30'; // 高风险 - 红色
    } else if (text.includes('🔺') || text.includes('⚡')) {
      return '#34C759'; // 利好 - 绿色
    } else if (text.includes('◼️')) {
      return '#007AFF'; // 中性 - 蓝色
    } else if (text.includes('🔻') || text.includes('❄️')) {
      return '#FF9500'; // 利空 - 橙色
    }
    return '#3A3A3C'; // 默认颜色
  };

  // 从单行文本渲染表格
  const renderTableFromLine = (line, idx) => {
    const columns = line.split('|').map(col => col.trim());
    
    // 检查是否是决策树表格（包含\u2192、？等符号）
    const isDecisionTree = line.includes('\u2192') || line.includes('？') || line.includes('\u2192');
    
    // 检查是否包含百分比或成功率
    const hasPercentage = line.includes('%');
    
    return (
      <View key={`table_${idx}`} style={[styles.table, { marginBottom: 12 }]}>
        <View style={styles.tableRow}>
          {columns.map((col, colIdx) => {
            // 为决策树表格添加特殊样式
            let cellStyle = [styles.tableCell, { flex: 1 }];
            let textStyle = [styles.tableCellText, { 
              fontWeight: colIdx === 0 ? 'bold' : 'normal',
              color: colIdx === 0 ? '#1C1C1E' : '#3A3A3C',
              fontSize: colIdx === 0 ? 13 : 12
            }];
            
            // 最后一个单元格不显示右边框
            if (colIdx === columns.length - 1) {
              cellStyle.push({ borderRightWidth: 0 });
            }
            
            // 成功率列特殊处理
            if (hasPercentage && col.includes('%')) {
              cellStyle.push({ backgroundColor: '#F3E5F5' });
              textStyle.push({ color: '#7B1FA2', fontWeight: '700' });
            }
            
            return (
              <View key={`col_${colIdx}`} style={cellStyle}>
                <Text style={textStyle}>
                  {col}
                </Text>
              </View>
            );
          })}
        </View>
      </View>
    );
  };

  // 解析 Mermaid 决策树为树状文本显示
  const renderMermaidText = (code, keyIdx) => {
  
    // 简化的树状渲染逻辑
    return (
      <View key={`mertext_${keyIdx}`} style={{ marginBottom: 12 }}>
        <Text style={styles.contentText}>
          {code}
        </Text>
      </View>
    );
  };

  // 解析买入/卖出策略中的表格，同时保留其他策略内容
  const extractStrategyTables = (text) => {
    if (!text) return [];
    
    console.log('=== extractStrategyTables Debug ===');
    console.log('Input text:', text.substring(0, 200) + '...');
    
    const lines = text.split('\n').map(l => l.trim());
    console.log('Total lines:', lines.length);
    console.log('First few lines:', lines.slice(0, 5));
    
    const sections = [];
    let current = null;
    let currentContent = [];
    
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      
      // 优先识别 [H] 主标题
      const isMainHeader = /^-\s*\[H\]\s*/.test(line);
      
      // 然后识别 [S] 子标题
      const isSubHeader = /^-\s*\[S\]\s*/.test(line);
      
      if (isMainHeader || isSubHeader) {
        console.log(`Line ${i}: Found header - Main: ${isMainHeader}, Sub: ${isSubHeader}, Content: "${line}"`);
        
        // 如果之前有收集内容，先保存
        if (current) {
          current.content = currentContent;
          sections.push(current);
        }
        
        // 开启新小节
        let title = '';
        if (isMainHeader) {
          // 主标题处理
          title = line.replace(/^-\s*\[H\]\s*/, '');
        } else {
          // 子标题处理
          title = line.replace(/^-\s*\[S\]\s*/, '');
        }
        
        console.log(`Extracted title: "${title}"`);
        
        current = { 
          title: title, 
          tableLines: [],
          content: [],
          isMainHeader: isMainHeader
        };
        currentContent = [];
        continue;
      }
      
      if (current) {
        if (line.includes('|')) {
          current.tableLines.push(line);
          continue;
        }
        
        // 收集非表格内容
        if (line.length > 0) {
          currentContent.push(line);
        }
      }
    }
    
    // 保存最后一个section
    if (current) {
      current.content = currentContent;
      sections.push(current);
    }
    
    console.log('Sections found:', sections.length);
    sections.forEach((sec, idx) => {
      console.log(`Section ${idx}: "${sec.title}", isMainHeader: ${sec.isMainHeader}, hasTable: ${sec.tableLines.length >= 2}, contentLines: ${sec.content.length}`);
    });
    
    // 如果没有识别到任何section，尝试回退到简单解析
    if (sections.length === 0) {
      console.log('No sections found, trying fallback parsing...');
      // 回退：按行解析，识别策略标题
      for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        if (line.startsWith('-') && (line.includes('策略') || line.includes('买入') || line.includes('卖出'))) {
          const title = line.replace(/^-\s*/, '');
          console.log(`Fallback found: "${title}"`);
          sections.push({
            title: title,
            tableLines: [],
            content: [],
            isMainHeader: line.includes('策略') && !line.includes('买入') && !line.includes('卖出'),
            hasTable: false
          });
        }
      }
    }
    
    // 解析每个section的表格 - 移除过滤条件，显示所有section
    const parsed = sections.map(sec => {
      if (sec.tableLines.length >= 2) {
        const rows = sec.tableLines
          .map(l => l.split('|').map(c => c.trim()).filter(c => c.length > 0));
        const headers = rows.shift();
        return { 
          title: sec.title,
          headers,
          rows,
          hasTable: true,
          content: sec.content,
          isMainHeader: sec.isMainHeader
        };
      } else {
        return {
          title: sec.title,
          hasTable: false,
          content: sec.content,
          isMainHeader: sec.isMainHeader
        };
      }
    });
    
    console.log('Final parsed sections:', parsed.length);
    console.log('=== End Debug ===');
    
    return parsed;
  };

  // 渲染策略内容（非表格部分）
  const renderStrategyContent = (content) => {
    if (!content || content.length === 0) return null;
    
    return (
      <View>
        {content.map((line, idx) => {
          // 处理投资判断等非表格内容
          if (line.startsWith('-')) {
            const colonIndex = line.indexOf('：');
            if (colonIndex !== -1) {
              const title = line.substring(0, colonIndex + 1);
              const content = line.substring(colonIndex + 1).trim();
              return (
                <View key={`content_${idx}`} style={{ marginBottom: 8 }}>
                  <Text style={[styles.contentText, { fontWeight: 'bold', fontSize: 14, color: '#1C1C1E' }]}>
                    {title}
                  </Text>
                  {content && (
                    <Text style={[styles.contentText, { marginLeft: 16, marginTop: 4, fontWeight: 'normal' }]}>
                      {content}
                    </Text>
                  )}
                </View>
              );
            }
          }
          
          // 处理盘面分析的特殊内容
          if (line.includes('|')) {
            return renderTableFromLine(line, idx);
          }
          
          // 处理风险标识和特殊符号
          if (line.includes('❗') || line.includes('⚠️') || line.includes('🔺') || line.includes('⚡') || line.includes('◼️') || line.includes('🔻') || line.includes('❄️')) {
            const riskColor = getRiskColor(line);
            return (
              <View key={`content_${idx}`} style={{ 
                marginBottom: 8, 
                flexDirection: 'row', 
                alignItems: 'center',
                backgroundColor: riskColor === '#FF3B30' ? '#FFE5E5' : 
                               riskColor === '#34C759' ? '#E5F7E5' : 
                               riskColor === '#007AFF' ? '#E5F0FF' : 
                               riskColor === '#FF9500' ? '#FFF2E5' : 'transparent',
                padding: 8,
                borderRadius: 6
              }}>
                <Text style={[styles.contentText, { fontSize: 16, marginRight: 8 }]}>
                  {line.match(/[❗⚠️🔺⚡◼️🔻❄️]/)[0]}
                </Text>
                <Text style={[styles.contentText, { flex: 1, fontSize: 14, color: riskColor }]}>
                  {line.replace(/[❗⚠️🔺⚡◼️🔻❄️]/, '').trim()}
                </Text>
              </View>
            );
          }
          
          return (
            <Text key={`content_${idx}`} style={[styles.contentText, { marginBottom: 4 }]}>
              {line}
            </Text>
          );
        })}
      </View>
    );
  };

  // 表格渲染
  const renderTable = (table) => {
    const colCount = table.headers.length;
    return (
      <View>
        {/* 表格 */}
        <View style={styles.table}>
          {/* 表头 */}
          <View style={[styles.tableRow, styles.tableHeaderRow]}>
            {table.headers.map((h, idx) => (
              <View key={`h_${idx}`} style={[styles.tableCell, styles.tableHeaderCell, { flex: 1 }] }>
                <Text style={styles.tableHeaderText}>{h}</Text>
              </View>
            ))}
          </View>
          {/* 行 */}
          {table.rows.map((r, rIdx) => (
            <View key={`r_${rIdx}`} style={styles.tableRow}>
              {Array.from({ length: colCount }).map((_, cIdx) => (
                <View key={`c_${rIdx}_${cIdx}`} style={[styles.tableCell, { flex: 1 }]}>
                  <Text style={styles.tableCellText}>
                    {(r[cIdx] ?? '').replace(/\\n/g, ' ').trim()}
                  </Text>
                </View>
              ))}
            </View>
          ))}
        </View>
        
        {/* 显示其他内容 */}
        {table.content && table.content.length > 0 && (
          <View style={{ marginTop: 12 }}>
            {table.content.map((line, idx) => {
              // 处理投资判断等非表格内容
              if (line.startsWith('-')) {
                const colonIndex = line.indexOf('：');
                if (colonIndex !== -1) {
                  const title = line.substring(0, colonIndex + 1);
                  const content = line.substring(colonIndex + 1).trim();
                  return (
                    <View key={`content_${idx}`} style={{ marginBottom: 8 }}>
                      <Text style={[styles.contentText, { fontWeight: 'bold', fontSize: 14, color: '#1C1C1E' }]}>
                        {title}
                      </Text>
                      {content && (
                        <Text style={[styles.contentText, { marginLeft: 16, marginTop: 4, fontWeight: 'normal' }]}>
                          {content}
                        </Text>
                      )}
                    </View>
                  );
                }
              }
              return (
                <Text key={`content_${idx}`} style={[styles.contentText, { marginBottom: 4 }]}>
                  {line}
                </Text>
              );
            })}
          </View>
        )}
      </View>
    );
  };

  // 渲染盘面分析内容
  const renderIntradayAnalysis = (text) => {
    if (!text) return null;
    
    const rawLines = text.split('\n');
    const lines = rawLines.map(l => l.trim());
    const elements = [];
    
    for (let lineIdx = 0; lineIdx < lines.length; lineIdx++) {
      const line = lines[lineIdx];
      if (!line) continue;
          // 处理盘面分析主标题（[H]开头）
          if (/^-\s*\[H\]/.test(line)) {
            const title = line.replace(/^-\s*\[H\]\s*/, '');
            elements.push((
              <View key={`header_${lineIdx}`} style={{ 
                marginBottom: 12, 
                marginTop: 8,
                padding: 12, 
                backgroundColor: '#E3F2FD', 
                borderRadius: 8,
                borderLeftWidth: 4,
                borderLeftColor: '#2196F3'
              }}>
                <Text style={[styles.contentText, { fontSize: 16, color: '#1565C0', fontWeight: '700' }]}>
                  {title}
                </Text>
              </View>
            ));
            continue;
          }
          
          // 处理盘面分析子标题（[S]开头）
          if (/^-\s*\[S\]/.test(line)) {
            const title = line.replace(/^-\s*\[S\]\s*/, '');
            elements.push((
              <View key={`subheader_${lineIdx}`} style={{ 
                marginBottom: 8, 
                marginTop: 6,
                padding: 8, 
                backgroundColor: '#F3E5F5', 
                borderRadius: 6,
                borderLeftWidth: 3,
                borderLeftColor: '#9C27B0'
              }}>
                <Text style={[styles.contentText, { fontSize: 14, color: '#7B1FA2', fontWeight: '600' }]}>
                  {title}
                </Text>
              </View>
            ));
            continue;
          }

          // 严格解析 Mermaid 代码块为纯文本（不再渲染图形）
          if (line.startsWith('```mermaid')) {
            let j = lineIdx + 1;
            const merLines = [];
            while (j < lines.length && !lines[j].startsWith('```')) {
              merLines.push(lines[j]);
              j++;
            }
            const mermaidCode = merLines.join('\n').trim();
            elements.push(renderMermaidText(mermaidCode, lineIdx));
            lineIdx = j; // 跳过到结尾标记行
            continue;
          }
          
          // 处理表格行（包含|符号）
          if (line.includes('|')) {
            elements.push(renderTableFromLine(line, lineIdx));
            continue;
          }
          
          // 处理风险标识行
          if (line.includes('❗') || line.includes('⚠️') || line.includes('🔺') || line.includes('⚡') || line.includes('◼️') || line.includes('🔻') || line.includes('❄️')) {
            const riskColor = getRiskColor(line);
            elements.push((
              <View key={`risk_${lineIdx}`} style={{ 
                marginBottom: 8, 
                flexDirection: 'row', 
                alignItems: 'center',
                backgroundColor: riskColor === '#FF3B30' ? '#FFE5E5' : 
                               riskColor === '#34C759' ? '#E5F7E5' : 
                               riskColor === '#007AFF' ? '#E5F0FF' : 
                               riskColor === '#FF9500' ? '#FFF2E5' : 'transparent',
                padding: 8,
                borderRadius: 6,
                borderWidth: 1,
                borderColor: riskColor
              }}>
                <Text style={[styles.contentText, { fontSize: 16, marginRight: 8 }]}>
                  {line.match(/[❗⚠️🔺⚡◼️🔻❄️]/)[0]}
                </Text>
                <Text style={[styles.contentText, { flex: 1, fontSize: 14, color: riskColor, fontWeight: '600' }]}>
                  {line.replace(/[❗⚠️🔺⚡◼️🔻❄️]/, '').trim()}
                </Text>
              </View>
            ));
            continue;
          }
          
          // 处理操作指令行（包含▶符号）
          if (line.includes('▶')) {
            elements.push((
              <View key={`action_${lineIdx}`} style={{ 
                marginBottom: 8, 
                padding: 8, 
                backgroundColor: '#E8F5E8', 
                borderRadius: 6,
                borderLeftWidth: 3,
                borderLeftColor: '#4CAF50'
              }}>
                <Text style={[styles.contentText, { fontSize: 14, color: '#2E7D32', fontWeight: '600' }]}>
                  {line}
                </Text>
              </View>
            ));
            continue;
          }
          
          // 处理Mermaid格式决策树
          if (text.includes('```mermaid')) {
            const mermaidLines = text.split('\n').filter(l => l.trim().match(/^[A-Z]\[.*\].*-->\|?.*\|?[A-Z]\[.*\]/));
            const treeElements = renderMermaidTree(mermaidLines);
            elements.push(treeElements);
            continue;
          }

          // 新增的Mermaid树渲染函数
          const renderMermaidTree = (edges) => {
            const nodeMap = new Map();
            const rootNodeIds = new Set();
            
            // 解析边并构建节点映射
            edges.forEach(edge => {
              const [fromPart, toPart] = edge.split('-->');
              const fromMatch = fromPart.match(/([A-Z])\[(.*?)\]/);
              const toMatch = toPart.match(/([A-Z])\[(.*?)\]/);
              
              if (fromMatch && toMatch) {
                const fromId = fromMatch[1];
                const fromLabel = fromMatch[2];
                const toId = toMatch[1];
                const toLabel = toMatch[2];
                const condition = edge.match(/\|(.*?)\|/)?.[1] || '';

                // 添加或更新起始节点
                if (!nodeMap.has(fromId)) {
                  nodeMap.set(fromId, { id: fromId, label: fromLabel, children: [] });
                  rootNodeIds.add(fromId);
                }
                
                // 添加或更新目标节点
                if (!nodeMap.has(toId)) {
                  nodeMap.set(toId, { id: toId, label: toLabel, children: [] });
                }
                
                // 建立父子关系
                nodeMap.get(fromId).children.push({ 
                  id: toId, 
                  condition,
                  node: nodeMap.get(toId)
                });
                
                // 目标节点不再是根节点
                rootNodeIds.delete(toId);
              }
            });

            // 递归渲染树节点
            const renderNode = (node, level = 0, isLast = false, isRoot = true) => {
              const marginLeft = level * 16 + (level > 0 ? 12 : 0);
              
              // 渲染当前节点
              const nodeElement = (
                <View key={`node_${node.id}`} style={{ marginBottom: 2 }}>
                  <Text style={[styles.contentText, { 
                    color: node.label.includes('问题') ? '#1A4E8A' : 
                           node.label.includes('判断') ? '#1A73E8' :
                           node.label.includes('建议') ? '#34A853' : '#EA4335', 
                    marginLeft: marginLeft,
                    fontWeight: node.label.includes('风险') ? '700' : '600'
                  }]}>,
                    {isRoot ? '' : (isLast ? '└── ' : '├── ')}{node.label},
                  </Text>
                </View>
              );

              // 渲染子节点
              const childElements = node.children.map((child, idx) => {
                const isLastChild = idx === node.children.length - 1;
                
                // 渲染条件
                const conditionElement = child.condition ? (
                  <View key={`condition_${node.id}_${child.id}`} style={{ marginBottom: 2 }}>
                    <Text style={[styles.contentText, { 
                      color: child.condition.includes('条件') ? '#1A73E8' :
                             child.condition.includes('建议') ? '#34A853' :
                             child.condition.includes('风险') ? '#EA4335' : '#666',
                      marginLeft: (level + 1) * 16 + 12,
                      fontStyle: child.condition.includes('条件') ? 'italic' : 'normal'
                    }]}>\n                      \u2192 {child.condition}
                    </Text>
                  </View>
                ) : null;
                
                // 递归渲染子节点
                const childNodeElement = renderNode(child.node, level + 1, isLastChild, false);
                
                return (
                  <View key={`child_group_${node.id}_${child.id}`}>
                    {conditionElement}
                    {childNodeElement}
                  </View>
                );
              });

              return (
                <View key={`tree_node_${node.id}`}>
                  {nodeElement}
                  {childElements}
                </View>
              );
            };

            // 渲染所有根节点
            const treeElements = Array.from(rootNodeIds).map((rootId, idx) => {
              const rootNode = nodeMap.get(rootId);
              const isLast = idx === rootNodeIds.size - 1;
              return renderNode(rootNode, 0, isLast, true);
            });

            return <View>{treeElements}</View>;
          };

          // 处理时间标识行（XX:XX格式）
          if (line.includes('：') && /^\d{2}:\d{2}/.test(line)) {
            elements.push((
              <View key={`time_${lineIdx}`} style={{ 
                marginBottom: 6, 
                padding: 6, 
                backgroundColor: '#FFF3CD', 
                borderRadius: 4,
                borderLeftWidth: 2,
                borderLeftColor: '#FFC107'
              }}>
                <Text style={[styles.contentText, { fontSize: 13, color: '#856404', fontWeight: '600' }]}>
                  {line}
                </Text>
              </View>
            ));
            continue;
          }
          
          // 处理百分比和数字标识行
          if (line.includes('%') || line.includes('￥') || /\d+\.\d+/.test(line)) {
            elements.push((
              <View key={`number_${lineIdx}`} style={{ 
                marginBottom: 6, 
                padding: 6, 
                backgroundColor: '#E8F5E8', 
                borderRadius: 4,
                borderLeftWidth: 2,
                borderLeftColor: '#28A745'
              }}>
                <Text style={[styles.contentText, { fontSize: 13, color: '#155724', fontWeight: '600' }]}>
                  {line}
                </Text>
              </View>
            ));
            continue;
          }
          
          // 处理数字列表（如1. 2. 3.开头的行）
          if (/^\d+\.\s*\*\*/.test(line)) {
            elements.push((
              <View key={`list_${lineIdx}`} style={{ 
                marginBottom: 8, 
                padding: 8, 
                backgroundColor: '#FFF8E1', 
                borderRadius: 6,
                borderLeftWidth: 3,
                borderLeftColor: '#FF9800'
              }}>
                <Text style={[styles.contentText, { fontSize: 14, color: '#E65100', fontWeight: '600' }]}>
                  {line}
                </Text>
              </View>
            ));
            continue;
          }
          
          // 处理仓位公式等特殊格式
          if (line.includes('仓位') && line.includes('=')) {
            elements.push((
              <View key={`formula_${lineIdx}`} style={{ 
                marginBottom: 8, 
                padding: 8, 
                backgroundColor: '#E1F5FE', 
                borderRadius: 6,
                borderLeftWidth: 3,
                borderLeftColor: '#00BCD4'
              }}>
                <Text style={[styles.contentText, { fontSize: 14, color: '#006064', fontWeight: '600' }]}>
                  {line}
                </Text>
              </View>
            ));
            continue;
          }
          
          // 处理成功率标识行（包含%的成功率）
          if (line.includes('成功率') && line.includes('%')) {
            elements.push((
              <View key={`success_${lineIdx}`} style={{ 
                marginBottom: 6, 
                padding: 6, 
                backgroundColor: '#F3E5F5', 
                borderRadius: 4,
                borderLeftWidth: 2,
                borderLeftColor: '#9C27B0'
              }}>
                <Text style={[styles.contentText, { fontSize: 13, color: '#7B1FA2', fontWeight: '600' }]}>
                  {line}
                </Text>
              </View>
            ));
            continue;
          }
          
          // 其他普通文本
          elements.push(
            <Text key={`content_${lineIdx}`} style={[styles.contentText, { marginBottom: 4 }]}>
              {line}
            </Text>
          );
    }
    
    return (<View>{elements}</View>);
  };

  // 通用内容格式化：处理多行内容，支持[H]/[S]标签；小标题( [H] )加粗且字号变大；子标题( [S] )加粗但内容不换行
  const renderFormattedContent = (text) => {
    if (!text) return null;
    
    // 检查是否是盘面分析内容 - 更新检测条件
    if (text.includes('盘前作战指南') || text.includes('盘中动态操作') || text.includes('盘后总结') || 
        text.includes('多路径决策树') || text.includes('```mermaid')) {
      return renderIntradayAnalysis(text);
    }
    
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
                    <Text style={[styles.contentText, { flex: 1, fontSize: 14, fontWeight: 'normal' }]}>
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
                    <Text style={[styles.contentText, { flex: 1, fontSize: 14, fontWeight: 'normal' }]}>
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
                    <Text style={[styles.contentText, { marginLeft: 8, fontWeight: 'normal' }]}>
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
    // 尝试将表格样式的买入/卖出策略渲染为表格
    const tableSections = extractStrategyTables(text);
    if (tableSections.length > 0) {
      return (
        <View>
          {tableSections.map((sec, idx) => (
            <View key={`tbl_${idx}`} style={{ marginBottom: 16 }}>
              {(() => {
                const rawTitle = sec.title || '';
                const idxCn = rawTitle.indexOf('：');
                const idxEn = rawTitle.indexOf(':');
                const hasColon = idxCn !== -1 || idxEn !== -1;
                const cut = idxCn !== -1 ? idxCn : idxEn;
                const label = hasColon ? rawTitle.substring(0, cut) : rawTitle;
                const after = hasColon ? rawTitle.substring(cut + 1).trim() : '';

                if (sec.isMainHeader) {
                  return (
                    <View style={{ marginBottom: 8 }}>
                      <Text style={[styles.contentText, { fontWeight: 'bold', fontSize: 16, color: '#007AFF' }]}>
                        {label}
                      </Text>
                      {after ? (
                        <Text style={[styles.contentText, { marginLeft: 16, marginTop: 4, fontWeight: 'normal' }]}>
                          {after}
                        </Text>
                      ) : null}
                    </View>
                  );
                }

                // 子标题：同一行显示，冒号后内容不加粗
                if (hasColon) {
                  return (
                    <View style={{ marginBottom: 8, flexDirection: 'row', alignItems: 'flex-start' }}>
                      <Text style={[styles.contentText, { fontWeight: 'bold', fontSize: 14, color: '#1C1C1E', marginRight: 8 }]}>
                        {label}：
                      </Text>
                      <Text style={[styles.contentText, { flex: 1, fontSize: 14, fontWeight: 'normal' }]}>
                        {after}
                      </Text>
                    </View>
                  );
                }

                // 无冒号：整段作为子标题加粗
                return (
                  <Text style={[styles.contentText, { fontWeight: 'bold', fontSize: 14, color: '#1C1C1E', marginBottom: 8 }]}>
                    {rawTitle}
                  </Text>
                );
              })()}
              {sec.hasTable ? renderTable(sec) : renderStrategyContent(sec.content)}
            </View>
          ))}
        </View>
      );
    }
    
    // 如果没有识别到策略结构，回退到原有渲染
    console.log('No strategy sections found, falling back to formatted content');
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

          // operationStrategy 使用表格优先渲染
          const contentNode = key === 'operationStrategy' 
            ? renderOperationStrategy(value)
            : renderFormattedContent(value);
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

          const contentNode = key === 'operationStrategy' 
            ? renderOperationStrategy(value)
            : renderFormattedContent(value);
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
      // 盘面分析特殊图标
      case '盘前作战指南':
        return 'sunny-outline';
      case '盘中动态操作手册':
        return 'play-outline';
      case '动态风控引擎':
        return 'shield-outline';
      case '盘后总结与明日计划':
        return 'moon-outline';
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
          {/* 显示分析时间，优先使用analysis_time字段 */}
          <Text style={styles.analysisTime}>
            {result?.analysis_time ? new Date(result.analysis_time).toLocaleString('zh-CN') : getTimeDisplay()}
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
  table: {
    borderWidth: 1,
    borderColor: '#E5E5EA',
    borderRadius: 8,
    overflow: 'hidden',
    marginBottom: 8,
  },
  tableRow: {
    flexDirection: 'row',
    borderBottomWidth: 1,
    borderBottomColor: '#E5E5EA',
    minHeight: 40,
  },
  tableHeaderRow: {
    backgroundColor: '#F8F9FA',
    borderBottomWidth: 2,
    borderBottomColor: '#007AFF',
  },
  tableCell: {
    paddingVertical: 8,
    paddingHorizontal: 6,
    justifyContent: 'center',
    alignItems: 'center',
    borderRightWidth: 1,
    borderRightColor: '#E5E5EA',
  },
  tableHeaderCell: {
    borderRightWidth: 1,
    borderRightColor: '#E5E5EA',
    backgroundColor: '#F8F9FA',
  },
  tableHeaderText: {
    fontSize: 13,
    fontWeight: '700',
    color: '#1C1C1E',
    textAlign: 'center',
  },
  tableCellText: {
    fontSize: 12,
    color: '#3A3A3C',
    textAlign: 'center',
    lineHeight: 16,
  },
});

export default AnalysisResultView;
