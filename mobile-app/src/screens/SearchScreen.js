import React, { useState, useEffect } from 'react';
import {
  View,
  ScrollView,
  Alert,
  FlatList,
} from 'react-native';
import {
  Searchbar,
  Card,
  Title,
  Paragraph,
  Button,
  ActivityIndicator,
  Chip,
  Divider,
  List,
} from 'react-native-paper';
import { theme, styles } from '../utils/theme';
import ApiService from '../services/ApiService';

export default function SearchScreen({ navigation }) {
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [recentSearches, setRecentSearches] = useState([]);
  const [popularStocks, setPopularStocks] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    loadInitialData();
  }, []);

  const loadInitialData = async () => {
    try {
      setLoading(true);
      const response = await ApiService.getPopularStocks();
      if (response.success) {
        setPopularStocks(response.data);
      }
      
      // 加载最近搜索记录（这里使用模拟数据）
      setRecentSearches([
        { code: '000001', name: '平安银行', timestamp: Date.now() - 3600000 },
        { code: '600519', name: '贵州茅台', timestamp: Date.now() - 7200000 },
      ]);
    } catch (error) {
      console.error('加载数据失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = async (query) => {
    if (!query.trim()) {
      setSearchResults([]);
      return;
    }

    try {
      setSearching(true);
      
      // 从热门股票中筛选匹配的结果
      const filtered = popularStocks.filter(stock => 
        stock.code.includes(query.toUpperCase()) || 
        stock.name.includes(query)
      );
      
      setSearchResults(filtered);
      
      // 如果没有找到结果，提示用户
      if (filtered.length === 0) {
        Alert.alert('提示', '未找到匹配的股票，请检查股票代码或名称');
      }
    } catch (error) {
      console.error('搜索失败:', error);
      Alert.alert('错误', '搜索失败，请重试');
    } finally {
      setSearching(false);
    }
  };

  const handleStockSelect = async (stock) => {
    // 添加到最近搜索
    const newSearch = {
      ...stock,
      timestamp: Date.now(),
    };
    
    setRecentSearches(prev => {
      const filtered = prev.filter(item => item.code !== stock.code);
      return [newSearch, ...filtered].slice(0, 10); // 保留最近10个
    });

    // 导航到股票详情
    navigation.navigate('StockDetail', {
      stockCode: stock.code,
      stockName: stock.name,
    });
  };

  const handleQuickAnalysis = async (stockCode) => {
    try {
      setLoading(true);
      const result = await ApiService.quickAnalyze(stockCode);
      
      if (result.success) {
        Alert.alert(
          '快速分析结果',
          `股票代码: ${stockCode}\n技术指标: ${JSON.stringify(result.technicalIndicators, null, 2)}`,
          [
            { text: '查看详情', onPress: () => handleStockSelect({ code: stockCode }) },
            { text: '确定', style: 'cancel' },
          ]
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

  const clearRecentSearches = () => {
    Alert.alert(
      '确认',
      '确定要清空最近搜索记录吗？',
      [
        { text: '取消', style: 'cancel' },
        { text: '确定', onPress: () => setRecentSearches([]) },
      ]
    );
  };

  const renderStockItem = ({ item, showTimestamp = false }) => (
    <List.Item
      title={`${item.name} (${item.code})`}
      description={`${item.market === 'SH' ? '上海' : '深圳'}${showTimestamp ? ` • ${new Date(item.timestamp).toLocaleTimeString()}` : ''}`}
      left={props => <List.Icon {...props} icon="trending-up" />}
      right={props => (
        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
          <Button
            mode="outlined"
            compact
            onPress={() => handleQuickAnalysis(item.code)}
            disabled={loading}
            style={{ marginRight: 8 }}
          >
            分析
          </Button>
          <List.Icon {...props} icon="chevron-right" />
        </View>
      )}
      onPress={() => handleStockSelect(item)}
      style={{ paddingVertical: 4 }}
    />
  );

  return (
    <View style={styles.container}>
      {/* 搜索栏 */}
      <View style={{ padding: 16 }}>
        <Searchbar
          placeholder="输入股票代码或名称"
          onChangeText={setSearchQuery}
          value={searchQuery}
          onSubmitEditing={() => handleSearch(searchQuery)}
          loading={searching}
          icon="magnify"
          clearIcon="close"
        />
      </View>

      <ScrollView style={{ flex: 1 }}>
        {/* 搜索结果 */}
        {searchQuery.trim() && (
          <Card style={styles.card}>
            <Card.Content>
              <Title>搜索结果</Title>
              {searching ? (
                <View style={{ alignItems: 'center', padding: 20 }}>
                  <ActivityIndicator size="small" color={theme.colors.primary} />
                  <Paragraph style={{ marginTop: 8 }}>搜索中...</Paragraph>
                </View>
              ) : searchResults.length > 0 ? (
                <FlatList
                  data={searchResults}
                  renderItem={renderStockItem}
                  keyExtractor={item => item.code}
                  ItemSeparatorComponent={() => <Divider />}
                  scrollEnabled={false}
                />
              ) : (
                <Paragraph style={styles.smallText}>未找到匹配的股票</Paragraph>
              )}
            </Card.Content>
          </Card>
        )}

        {/* 最近搜索 */}
        {!searchQuery.trim() && recentSearches.length > 0 && (
          <Card style={styles.card}>
            <Card.Content>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                <Title>最近搜索</Title>
                <Button mode="text" onPress={clearRecentSearches}>
                  清空
                </Button>
              </View>
              <FlatList
                data={recentSearches}
                renderItem={({ item }) => renderStockItem({ item, showTimestamp: true })}
                keyExtractor={item => `recent_${item.code}`}
                ItemSeparatorComponent={() => <Divider />}
                scrollEnabled={false}
              />
            </Card.Content>
          </Card>
        )}

        {/* 热门股票 */}
        {!searchQuery.trim() && (
          <Card style={styles.card}>
            <Card.Content>
              <Title>热门股票</Title>
              <Paragraph style={styles.smallText}>点击查看详情或进行分析</Paragraph>
              <Divider style={{ marginVertical: 8 }} />
              
              {loading ? (
                <View style={{ alignItems: 'center', padding: 20 }}>
                  <ActivityIndicator size="small" color={theme.colors.primary} />
                  <Paragraph style={{ marginTop: 8 }}>加载中...</Paragraph>
                </View>
              ) : (
                <FlatList
                  data={popularStocks}
                  renderItem={renderStockItem}
                  keyExtractor={item => item.code}
                  ItemSeparatorComponent={() => <Divider />}
                  scrollEnabled={false}
                />
              )}
            </Card.Content>
          </Card>
        )}

        {/* 搜索提示 */}
        {!searchQuery.trim() && recentSearches.length === 0 && (
          <Card style={styles.card}>
            <Card.Content>
              <Title>搜索提示</Title>
              <View style={{ marginTop: 8 }}>
                <Chip style={{ margin: 2 }} onPress={() => setSearchQuery('000001')}>
                  000001
                </Chip>
                <Chip style={{ margin: 2 }} onPress={() => setSearchQuery('600519')}>
                  600519
                </Chip>
                <Chip style={{ margin: 2 }} onPress={() => setSearchQuery('平安银行')}>
                  平安银行
                </Chip>
                <Chip style={{ margin: 2 }} onPress={() => setSearchQuery('贵州茅台')}>
                  贵州茅台
                </Chip>
              </View>
              <Paragraph style={[styles.smallText, { marginTop: 8 }]}>
                支持股票代码和股票名称搜索
              </Paragraph>
            </Card.Content>
          </Card>
        )}
      </ScrollView>
    </View>
  );
}
