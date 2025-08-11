import React, { useState, useEffect, useRef, useImperativeHandle } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Alert,
  RefreshControl,
  ActivityIndicator,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import AsyncStorage from '@react-native-async-storage/async-storage';
import ApiService from '../services/ApiService';

const AnalysisTaskList = React.forwardRef(({ stockCode, onTaskComplete, onViewResult }, ref) => {
  const [tasks, setTasks] = useState([]);
  const [refreshing, setRefreshing] = useState(false);
  const [loading, setLoading] = useState(true);
  const pollingRefs = useRef(new Map());
  const apiService = useRef(ApiService);

  // 暴露方法给父组件
  useImperativeHandle(ref, () => ({
    addTask,
    removeTask,
    refreshTasks,
    loadTasks,
  }));

  // 保存任务到本地存储
  const saveTasksToStorage = async (taskList) => {
    try {
      console.log('💾 开始保存任务到本地存储，任务数:', taskList.length);
      if (taskList.length > 0) {
        console.log('📋 保存的任务详情:', taskList.map(t => ({ 
          stockCode: t.stockCode, 
          taskId: t.taskId, 
          status: t.status 
        })));
      }
      
      await AsyncStorage.setItem('analysis_tasks', JSON.stringify(taskList));
      console.log('✅ 任务已成功保存到本地存储');
    } catch (error) {
      console.error('❌ 保存任务到本地存储失败:', error);
    }
  };

  // 从本地存储加载任务
  const loadTasksFromStorage = async () => {
    try {
      const storedTasks = await AsyncStorage.getItem('analysis_tasks');
      if (storedTasks) {
        const parsedTasks = JSON.parse(storedTasks);
        console.log('📱 从本地存储加载任务:', parsedTasks.length, '个');
        return parsedTasks;
      }
    } catch (error) {
      console.error('从本地存储加载任务失败:', error);
    }
    return [];
  };

  // 从后端获取任务状态
  const fetchTasksFromBackend = async () => {
    try {
      console.log('🔄 尝试从后端获取任务状态...');
      // 这里可以调用后端API获取所有任务状态
      // 暂时返回空数组，后续可以实现
      return [];
    } catch (error) {
      console.error('从后端获取任务失败:', error);
      return [];
    }
  };

  // 加载任务（从本地存储和后端）
  const loadTasks = async () => {
    setLoading(true);
    try {
      console.log('🔄 开始加载任务...');
      
      // 首先从本地存储加载
      const localTasks = await loadTasksFromStorage();
      console.log('📱 从本地存储加载到任务:', localTasks.length, '个');
      if (localTasks.length > 0) {
        console.log('📋 本地任务详情:', localTasks.map(t => ({ 
          stockCode: t.stockCode, 
          taskId: t.taskId, 
          status: t.status 
        })));
      }
      
      // 然后尝试从后端获取最新状态
      const backendTasks = await fetchTasksFromBackend();
      console.log('🌐 从后端获取到任务:', backendTasks.length, '个');
      
      // 合并任务，优先使用后端数据
      let finalTasks = localTasks;
      if (backendTasks.length > 0) {
        // 如果后端有数据，使用后端数据更新本地数据
        finalTasks = backendTasks;
        console.log('🔄 使用后端数据更新本地任务');
      }
      
      console.log('📊 最终任务列表:', finalTasks.length, '个任务');
      if (finalTasks.length > 0) {
        console.log('📋 最终任务详情:', finalTasks.map(t => ({ 
          stockCode: t.stockCode, 
          taskId: t.taskId, 
          status: t.status 
        })));
      }
      
      setTasks(finalTasks);
      
      // 重新开始进行中任务的轮询
      const processingTasks = finalTasks.filter(task => 
        task.status === 'pending' || task.status === 'processing'
      );
      
      console.log('🔄 重新开始轮询的任务数:', processingTasks.length);
      processingTasks.forEach(task => {
        console.log('🔄 开始轮询任务:', task.stockCode, task.taskId);
        startPolling(task.taskId);
      });
      
    } catch (error) {
      console.error('❌ 加载任务失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 组件挂载时加载任务
  useEffect(() => {
    loadTasks();
  }, []);

  // 当传入stockCode时，自动添加任务（避免重复创建）
  const lastAutoAddedRef = useRef(null);
  useEffect(() => {
    if (stockCode && stockCode.trim()) {
      const code = stockCode.trim();
      // 如果与上次相同且已处理过，跳过
      if (lastAutoAddedRef.current === code) {
        return;
      }
      // 若已存在进行中/等待中的同股票任务，则不再自动创建
      const hasActive = tasks.some(t => t.stockCode === code && (t.status === 'pending' || t.status === 'processing' || t.status === 'running'));
      if (hasActive) {
        console.log('⚠️ 已存在进行中的任务，跳过自动创建:', code);
        lastAutoAddedRef.current = code;
        return;
      }
      console.log('📱 检测到新的股票代码，自动添加任务:', code);
      lastAutoAddedRef.current = code;
      addTask(code);
    }
  }, [stockCode, tasks]);

  // 添加新任务
  const addTask = async (stockCode) => {
    try {
      console.log('🔄 开始添加任务:', stockCode);
      const taskInfo = await apiService.current.startAnalysisTask(stockCode);
      console.log('✅ 获取到任务信息:', taskInfo);
      
      const newTask = {
        ...taskInfo,
        id: taskInfo.taskId,
        startTime: new Date(),
        createdAt: new Date(),
      };
      
      console.log('📝 创建新任务对象:', newTask);
      
      // 使用函数式更新确保获取到最新的tasks状态
      setTasks(prevTasks => {
        const updatedTasks = [newTask, ...prevTasks];
        console.log('📊 更新任务列表，当前任务数:', updatedTasks.length);
        console.log('📋 任务列表:', updatedTasks.map(t => ({ stockCode: t.stockCode, taskId: t.taskId })));
        
        // 异步保存到本地存储
        saveTasksToStorage(updatedTasks);
        
        return updatedTasks;
      });
      
      console.log('🔄 开始轮询任务:', newTask.taskId);
      startPolling(newTask.taskId);
      
      return newTask;
    } catch (error) {
      console.error('❌ 添加任务失败:', error);
      Alert.alert('错误', `启动分析失败: ${error.message}`);
      throw error;
    }
  };

  // 开始轮询任务状态
  const startPolling = (taskId) => {
    if (pollingRefs.current.has(taskId)) {
      return; // 已经在轮询中
    }

    const pollInterval = setInterval(async () => {
      try {
        const status = await apiService.current.getAnalysisStatus(taskId);
        
        setTasks(prev => {
          const updatedTasks = prev.map(task => {
            if (task.taskId !== taskId) return task;
            const merged = { ...task, ...status };
            if ((status.status === 'completed' || status.status === 'failed') && !merged.endTime) {
              merged.endTime = new Date();
            }
            return merged;
          });
          
          // 保存更新后的任务到本地存储
          saveTasksToStorage(updatedTasks);
          
          return updatedTasks;
        });

        // 如果任务完成或失败，停止轮询
        if (status.status === 'completed' || status.status === 'failed') {
          clearInterval(pollInterval);
          pollingRefs.current.delete(taskId);
          
          if (status.status === 'completed' && onTaskComplete) {
            onTaskComplete(status);
          }
        }
      } catch (error) {
        console.error(`轮询任务 ${taskId} 状态失败:`, error);
        // 错误时也停止轮询
        clearInterval(pollInterval);
        pollingRefs.current.delete(taskId);
      }
    }, 3000); // 每3秒轮询一次

    pollingRefs.current.set(taskId, pollInterval);
  };

  // 停止轮询
  const stopPolling = (taskId) => {
    const interval = pollingRefs.current.get(taskId);
    if (interval) {
      clearInterval(interval);
      pollingRefs.current.delete(taskId);
    }
  };

  // 删除任务
  const removeTask = async (taskId) => {
    stopPolling(taskId);
    
    // 使用函数式更新确保获取到最新的tasks状态
    setTasks(prevTasks => {
      const updatedTasks = prevTasks.filter(task => task.taskId !== taskId);
      console.log('🗑️ 删除任务后，剩余任务数:', updatedTasks.length);
      
      // 异步保存到本地存储
      saveTasksToStorage(updatedTasks);
      
      return updatedTasks;
    });
  };

  // 刷新任务状态
  const refreshTasks = async () => {
    setRefreshing(true);
    try {
      await loadTasks();
    } catch (error) {
      console.error('刷新任务失败:', error);
    } finally {
      setRefreshing(false);
    }
  };

  // 清理轮询定时器
  useEffect(() => {
    return () => {
      pollingRefs.current.forEach((interval) => {
        clearInterval(interval);
      });
      pollingRefs.current.clear();
    };
  }, []);

  // 渲染任务项
  const renderTaskItem = ({ item }) => {
    const getStatusColor = (status) => {
      switch (status) {
        case 'pending': return '#FFA500';
        case 'processing': return '#007AFF';
        case 'completed': return '#34C759';
        case 'failed': return '#FF3B30';
        default: return '#8E8E93';
      }
    };

    const getStatusIcon = (status) => {
      switch (status) {
        case 'pending': return 'time-outline';
        case 'processing': return 'sync-outline';
        case 'completed': return 'checkmark-circle-outline';
        case 'failed': return 'close-circle-outline';
        default: return 'help-circle-outline';
      }
    };

    const formatDuration = (task) => {
      if (!task?.startTime) return '';
      const startTs = new Date(task.startTime).getTime();
      const endTs = task.endTime ? new Date(task.endTime).getTime() : Date.now();
      const duration = Math.max(0, endTs - startTs);
      const minutes = Math.floor(duration / 60000);
      const seconds = Math.floor((duration % 60000) / 1000);
      const hours = Math.floor(minutes / 60);
      const remMinutes = minutes % 60;
      if (hours > 0) {
        return `${hours}时${remMinutes}分${seconds}秒`;
      }
      return `${minutes}分${seconds}秒`;
    };

    return (
      <View style={styles.taskItem}>
        <View style={styles.taskHeader}>
          <View style={styles.stockInfo}>
            <Text style={styles.stockCode}>{item.stockCode}</Text>
            <Text style={styles.taskId}>#{item.taskId.slice(-8)}</Text>
          </View>
          
          <View style={styles.statusContainer}>
            <Ionicons 
              name={getStatusIcon(item.status)} 
              size={20} 
              color={getStatusColor(item.status)} 
            />
            <Text style={[styles.statusText, { color: getStatusColor(item.status) }]}>
              {item.status === 'pending' && '等待中'}
              {item.status === 'processing' && '分析中'}
              {item.status === 'completed' && '已完成'}
              {item.status === 'failed' && '失败'}
            </Text>
          </View>
        </View>

        <View style={styles.progressContainer}>
          <View style={styles.progressBar}>
            <View 
              style={[
                styles.progressFill, 
                { 
                  width: `${item.progress || 0}%`,
                  backgroundColor: getStatusColor(item.status)
                }
              ]} 
            />
          </View>
          <Text style={styles.progressText}>{item.progress || 0}%</Text>
        </View>

        <Text style={styles.messageText}>{item.message || '准备中...'}</Text>

        <View style={styles.taskFooter}>
          <Text style={styles.durationText}>
            {item.status === 'completed' ? '总耗时 ' : '已用时 '}{formatDuration(item)}
          </Text>
          
          <View style={styles.actionButtons}>
            {item.status === 'completed' && (
              <TouchableOpacity 
                style={styles.viewButton}
                onPress={() => onViewResult && onViewResult(item)}
              >
                <Ionicons name="eye-outline" size={16} color="#007AFF" />
                <Text style={styles.viewButtonText}>查看结果</Text>
              </TouchableOpacity>
            )}
            
            <TouchableOpacity 
              style={styles.deleteButton}
              onPress={() => {
                Alert.alert(
                  '确认删除',
                  '确定要删除这个分析任务吗？',
                  [
                    { text: '取消', style: 'cancel' },
                    { text: '删除', style: 'destructive', onPress: () => removeTask(item.taskId) }
                  ]
                );
              }}
            >
              <Ionicons name="trash-outline" size={16} color="#FF3B30" />
            </TouchableOpacity>
          </View>
        </View>
      </View>
    );
  };

  // 显示加载状态
  if (loading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#007AFF" />
        <Text style={styles.loadingText}>加载任务中...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <FlatList
        data={tasks}
        renderItem={renderTaskItem}
        keyExtractor={(item) => item.taskId}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={refreshTasks}
            colors={['#007AFF']}
          />
        }
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Ionicons name="analytics-outline" size={48} color="#8E8E93" />
            <Text style={styles.emptyText}>暂无分析任务</Text>
            <Text style={styles.emptySubtext}>点击"开始分析"添加新的股票分析任务</Text>
          </View>
        }
        showsVerticalScrollIndicator={false}
      />
    </View>
  );
});

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F2F2F7',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F2F2F7',
  },
  loadingText: {
    marginTop: 16,
    fontSize: 16,
    color: '#8E8E93',
  },
  taskItem: {
    backgroundColor: '#FFFFFF',
    marginHorizontal: 16,
    marginVertical: 8,
    borderRadius: 12,
    padding: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  taskHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  stockInfo: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  stockCode: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1C1C1E',
    marginRight: 8,
  },
  taskId: {
    fontSize: 12,
    color: '#8E8E93',
    fontFamily: 'monospace',
  },
  statusContainer: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  statusText: {
    fontSize: 14,
    fontWeight: '500',
    marginLeft: 4,
  },
  progressContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  progressBar: {
    flex: 1,
    height: 6,
    backgroundColor: '#E5E5EA',
    borderRadius: 3,
    marginRight: 12,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    borderRadius: 3,
  },
  progressText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#8E8E93',
    minWidth: 35,
  },
  messageText: {
    fontSize: 14,
    color: '#3A3A3C',
    marginBottom: 12,
  },
  taskFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  durationText: {
    fontSize: 12,
    color: '#8E8E93',
  },
  actionButtons: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  viewButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F2F2F7',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    marginRight: 8,
  },
  viewButtonText: {
    fontSize: 12,
    color: '#007AFF',
    fontWeight: '500',
    marginLeft: 4,
  },
  deleteButton: {
    padding: 8,
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 60,
  },
  emptyText: {
    fontSize: 18,
    fontWeight: '600',
    color: '#8E8E93',
    marginTop: 16,
  },
  emptySubtext: {
    fontSize: 14,
    color: '#8E8E93',
    textAlign: 'center',
    marginTop: 8,
    paddingHorizontal: 32,
  },
});

export default AnalysisTaskList;
