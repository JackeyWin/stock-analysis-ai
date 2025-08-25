import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import DeviceFingerprint from '../utils/deviceFingerprint';

/**
 * 设备指纹演示组件
 */
const DeviceFingerprintDemo = () => {
  const [fingerprint, setFingerprint] = useState('');
  const [deviceInfo, setDeviceInfo] = useState({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadDeviceFingerprint();
  }, []);

  const loadDeviceFingerprint = async () => {
    try {
      setLoading(true);
      
      // 生成设备指纹
      const fp = await DeviceFingerprint.generateFingerprint();
      setFingerprint(fp);
      
      // 收集设备信息
      const info = await DeviceFingerprint.collectDeviceFeatures();
      setDeviceInfo(info);
      
    } catch (error) {
      console.error('加载设备指纹失败:', error);
      setFingerprint('生成失败');
    } finally {
      setLoading(false);
    }
  };

  const formatDeviceInfo = (info) => {
    return Object.entries(info)
      .map(([key, value]) => {
        if (typeof value === 'object' && value !== null) {
          return `${key}: ${JSON.stringify(value, null, 2)}`;
        }
        return `${key}: ${value}`;
      })
      .join('\n');
  };

  if (loading) {
    return (
      <View style={styles.container}>
        <Text style={styles.loadingText}>正在生成设备指纹...</Text>
      </View>
    );
  }

  return (
    <ScrollView style={styles.container}>
      <View style={styles.section}>
        <Text style={styles.title}>设备指纹</Text>
        <Text style={styles.fingerprint}>{fingerprint}</Text>
        <Text style={styles.subtitle}>此标识基于您的设备特征生成，可用于用户识别和统计分析</Text>
      </View>

      <View style={styles.section}>
        <Text style={styles.title}>设备信息</Text>
        <Text style={styles.deviceInfo}>
          {formatDeviceInfo(deviceInfo)}
        </Text>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
    padding: 16,
  },
  section: {
    backgroundColor: 'white',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  title: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 12,
  },
  fingerprint: {
    fontSize: 14,
    fontFamily: 'monospace',
    backgroundColor: '#f0f0f0',
    padding: 12,
    borderRadius: 8,
    marginBottom: 8,
    color: '#666',
  },
  subtitle: {
    fontSize: 12,
    color: '#666',
    fontStyle: 'italic',
  },
  deviceInfo: {
    fontSize: 12,
    fontFamily: 'monospace',
    backgroundColor: '#f8f8f8',
    padding: 12,
    borderRadius: 8,
    color: '#444',
    lineHeight: 18,
  },
  instruction: {
    fontSize: 14,
    color: '#555',
    lineHeight: 20,
  },
  loadingText: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    marginTop: 20,
  },
});

export default DeviceFingerprintDemo;