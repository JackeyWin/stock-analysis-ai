import React, { useState } from 'react';
import {
  View,
  ScrollView,
  Alert,
  Linking,
} from 'react-native';
import {
  Card,
  Title,
  Paragraph,
  List,
  Switch,
  Button,
  Divider,
  Dialog,
  Portal,
} from 'react-native-paper';
import { theme, styles } from '../utils/theme';
import ApiService from '../services/ApiService';

export default function SettingsScreen() {
  const [notifications, setNotifications] = useState(true);
  const [darkMode, setDarkMode] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [aboutVisible, setAboutVisible] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleTestConnection = async () => {
    try {
      setLoading(true);
      const result = await ApiService.healthCheck();
      Alert.alert(
        '连接测试',
        `服务状态: ${result.status}\n时间: ${result.timestamp}`,
        [{ text: '确定' }]
      );
    } catch (error) {
      Alert.alert(
        '连接失败',
        `错误信息: ${error.message}\n请检查网络连接和服务器状态`,
        [{ text: '确定' }]
      );
    } finally {
      setLoading(false);
    }
  };

  const handleClearCache = () => {
    Alert.alert(
      '清除缓存',
      '确定要清除所有缓存数据吗？这将删除搜索历史和临时数据。',
      [
        { text: '取消', style: 'cancel' },
        {
          text: '确定',
          onPress: () => {
            // 这里可以实现清除缓存的逻辑
            Alert.alert('成功', '缓存已清除');
          },
        },
      ]
    );
  };

  const handleFeedback = () => {
    Alert.alert(
      '意见反馈',
      '请选择反馈方式',
      [
        { text: '取消', style: 'cancel' },
        {
          text: '邮件反馈',
          onPress: () => {
            Linking.openURL('mailto:feedback@stockanalysis.com?subject=股票分析助手反馈');
          },
        },
        {
          text: '在线反馈',
          onPress: () => {
            Alert.alert('提示', '在线反馈功能开发中...');
          },
        },
      ]
    );
  };

  const handleShare = () => {
    Alert.alert(
      '分享应用',
      '推荐给朋友使用股票分析助手',
      [
        { text: '取消', style: 'cancel' },
        {
          text: '分享',
          onPress: () => {
            // 这里可以实现分享功能
            Alert.alert('提示', '分享功能开发中...');
          },
        },
      ]
    );
  };

  return (
    <ScrollView style={styles.container}>
      {/* 通用设置 */}
      <Card style={styles.card}>
        <Card.Content>
          <Title>通用设置</Title>
          <Divider style={{ marginVertical: 8 }} />
          
          <List.Item
            title="推送通知"
            description="接收股票价格变动和分析结果通知"
            left={props => <List.Icon {...props} icon="bell" />}
            right={() => (
              <Switch
                value={notifications}
                onValueChange={setNotifications}
              />
            )}
          />
          
          <List.Item
            title="深色模式"
            description="使用深色主题界面"
            left={props => <List.Icon {...props} icon="theme-light-dark" />}
            right={() => (
              <Switch
                value={darkMode}
                onValueChange={setDarkMode}
                disabled={true} // 暂时禁用
              />
            )}
          />
          
          <List.Item
            title="自动刷新"
            description="自动刷新股票数据和分析结果"
            left={props => <List.Icon {...props} icon="refresh" />}
            right={() => (
              <Switch
                value={autoRefresh}
                onValueChange={setAutoRefresh}
              />
            )}
          />
        </Card.Content>
      </Card>

      {/* 数据与存储 */}
      <Card style={styles.card}>
        <Card.Content>
          <Title>数据与存储</Title>
          <Divider style={{ marginVertical: 8 }} />
          
          <List.Item
            title="清除缓存"
            description="清除搜索历史和临时数据"
            left={props => <List.Icon {...props} icon="delete" />}
            onPress={handleClearCache}
          />
          
          <List.Item
            title="数据使用"
            description="查看应用数据使用情况"
            left={props => <List.Icon {...props} icon="chart-pie" />}
            onPress={() => Alert.alert('提示', '数据使用统计功能开发中...')}
          />
        </Card.Content>
      </Card>

      {/* 服务设置 */}
      <Card style={styles.card}>
        <Card.Content>
          <Title>服务设置</Title>
          <Divider style={{ marginVertical: 8 }} />
          
          <List.Item
            title="连接测试"
            description="测试与分析服务器的连接"
            left={props => <List.Icon {...props} icon="server" />}
            onPress={handleTestConnection}
            disabled={loading}
          />
          
          <List.Item
            title="服务状态"
            description="查看各项服务的运行状态"
            left={props => <List.Icon {...props} icon="monitor" />}
            onPress={() => Alert.alert('提示', '服务状态监控功能开发中...')}
          />
        </Card.Content>
      </Card>

      {/* 帮助与支持 */}
      <Card style={styles.card}>
        <Card.Content>
          <Title>帮助与支持</Title>
          <Divider style={{ marginVertical: 8 }} />
          
          <List.Item
            title="使用帮助"
            description="查看应用使用指南"
            left={props => <List.Icon {...props} icon="help-circle" />}
            onPress={() => Alert.alert('提示', '使用帮助功能开发中...')}
          />
          
          <List.Item
            title="意见反馈"
            description="提交问题和建议"
            left={props => <List.Icon {...props} icon="message" />}
            onPress={handleFeedback}
          />
          
          <List.Item
            title="分享应用"
            description="推荐给朋友使用"
            left={props => <List.Icon {...props} icon="share" />}
            onPress={handleShare}
          />
          
          <List.Item
            title="关于应用"
            description="查看版本信息和开发团队"
            left={props => <List.Icon {...props} icon="information" />}
            onPress={() => setAboutVisible(true)}
          />
        </Card.Content>
      </Card>

      {/* 关于对话框 */}
      <Portal>
        <Dialog visible={aboutVisible} onDismiss={() => setAboutVisible(false)}>
          <Dialog.Title>关于股票分析助手</Dialog.Title>
          <Dialog.Content>
            <Paragraph style={styles.text}>
              版本: 1.0.0
            </Paragraph>
            <Paragraph style={styles.text}>
              构建: 2024.08.08
            </Paragraph>
            <Paragraph style={[styles.text, { marginTop: 16 }]}>
              股票分析助手是一款专业的股票分析工具，提供实时数据分析、技术指标计算、AI智能分析等功能。
            </Paragraph>
            <Paragraph style={[styles.text, { marginTop: 8 }]}>
              开发团队致力于为投资者提供准确、及时的股票分析服务。
            </Paragraph>
            <Paragraph style={[styles.smallText, { marginTop: 16 }]}>
              © 2024 Stock Analysis Team. All rights reserved.
            </Paragraph>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setAboutVisible(false)}>确定</Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>
    </ScrollView>
  );
}
