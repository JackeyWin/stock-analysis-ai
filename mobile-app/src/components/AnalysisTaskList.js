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
  
  // 每只股票的轮询状态管理
  const stockPollingStates = useRef(new Map());

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
      
      console.log('📊 最终任务列表(去重前):', finalTasks.length, '个任务');
      if (finalTasks.length > 0) {
        console.log('📋 去重前任务详情:', finalTasks.map(t => ({ 
          stockCode: t.stockCode, 
          taskId: t.taskId, 
          status: t.status 
        })));
      }

      // 根据股票代码去重，保留最新的一条（假设列表是按新→旧排序）
      const seenCodes = new Set();
      const dedupedTasks = [];
      for (const t of finalTasks) {
        if (!seenCodes.has(t.stockCode)) {
          seenCodes.add(t.stockCode);
          dedupedTasks.push(t);
        }
      }

      console.log('📊 最终任务列表(去重后):', dedupedTasks.length, '个任务');
      setTasks(dedupedTasks);
      // 同步更新本地存储
      saveTasksToStorage(dedupedTasks);
      
      // 重新开始进行中任务的轮询
      const processingTasks = finalTasks.filter(task => 
        task.status === 'pending' || task.status === 'processing'
      );
      
             console.log('🔄 重新开始轮询的任务数:', processingTasks.length);
       processingTasks.forEach(task => {
         console.log('🔄 开始轮询任务:', task.stockCode, task.taskId);
         startPolling(task.taskId, task.stockCode);
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
      
      // 若同股票已有旧任务，先停止其轮询并在列表中移除（保持只保留最新的一条）
      try {
        const sameCodeTasks = tasks.filter(t => t.stockCode === stockCode);
        sameCodeTasks.forEach(t => stopPolling(t.taskId));
      } catch (e) {
        console.warn('停止旧任务轮询时出现问题:', e?.message || e);
      }

      // 使用函数式更新确保获取到最新的tasks状态，同时移除相同股票的旧记录
      setTasks(prevTasks => {
        const filtered = prevTasks.filter(t => t.stockCode !== stockCode);
        const updatedTasks = [newTask, ...filtered];
        console.log('📊 更新任务列表，当前任务数:', updatedTasks.length);
        console.log('📋 任务列表:', updatedTasks.map(t => ({ stockCode: t.stockCode, taskId: t.taskId })));
        
        // 异步保存到本地存储
        saveTasksToStorage(updatedTasks);
        
        return updatedTasks;
      });
       
       // 等待状态更新后再开始轮询
       setTimeout(() => {
         console.log('🔄 开始轮询任务:', newTask.taskId);
         startPolling(newTask.taskId, newTask.stockCode);
       }, 100);
      
      return newTask;
    } catch (error) {
      console.error('❌ 添加任务失败:', error);
      Alert.alert('错误', `启动分析失败: ${error.message}`);
      throw error;
    }
  };

  // 开始轮询任务状态
  const startPolling = (taskId, stockCodeParam = null) => {
    if (pollingRefs.current.has(taskId)) {
      return; // 已经在轮询中
    }

    // 获取任务信息以确定股票代码
    let taskStockCode = stockCodeParam;
    if (!taskStockCode) {
      const task = tasks.find(t => t.taskId === taskId);
      if (!task) {
        console.error(`❌ 找不到任务 ${taskId}，当前任务列表:`, tasks.map(t => ({ taskId: t.taskId, stockCode: t.stockCode })));
        return;
      }
      taskStockCode = task.stockCode;
    }

    const stockCode = taskStockCode;
    
    // 获取或创建该股票的轮询状态
    if (!stockPollingStates.current.has(stockCode)) {
      stockPollingStates.current.set(stockCode, {
        lastPollTime: 0,
        consecutiveErrors: 0,
        currentDelay: 8000, // 初始8秒
        activeTasks: new Set()
      });
    }
    
    const stockState = stockPollingStates.current.get(stockCode);
    stockState.activeTasks.add(taskId);

    const poll = async () => {
      try {
        // 检查该股票是否需要等待
        const now = Date.now();
        const timeSinceLastPoll = now - stockState.lastPollTime;
        const minInterval = 3000; // 每只股票最小间隔3秒
        
        if (timeSinceLastPoll < minInterval) {
          const waitTime = minInterval - timeSinceLastPoll;
          console.log(`⏳ 股票 ${stockCode} 等待 ${waitTime}ms 后继续轮询`);
          setTimeout(poll, waitTime);
          return;
        }
        
        stockState.lastPollTime = now;
        console.log(`🔍 轮询股票 ${stockCode} 的任务 ${taskId}`);
        
        const status = await apiService.current.getAnalysisStatus(taskId);
        
        // 重置错误计数和延迟
        stockState.consecutiveErrors = 0;
        stockState.currentDelay = 8000;
        
        setTasks(prev => {
          const updatedTasks = prev.map(task => {
            if (task.taskId !== taskId) return task;
            const merged = { ...task, ...status };
            // 兼容后端未返回顶层 stockName 的情况，从 result.stockBasic 中回填
            if (!merged.stockName) {
              merged.stockName = status.stockName || status.result?.stockBasic?.stockName || task.stockName;
            }
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
          clearTimeout(pollingRefs.current.get(taskId));
          pollingRefs.current.delete(taskId);
          stockState.activeTasks.delete(taskId);
          
          // 如果没有活跃任务，清理股票状态
          if (stockState.activeTasks.size === 0) {
            stockPollingStates.current.delete(stockCode);
          }
          
          if (status.status === 'completed' && onTaskComplete) {
            onTaskComplete(status);
          }
        } else {
          // 继续轮询，使用该股票的当前延迟
          const nextPoll = setTimeout(poll, stockState.currentDelay);
          pollingRefs.current.set(taskId, nextPoll);
        }
      } catch (error) {
        stockState.consecutiveErrors++;
        console.error(`轮询股票 ${stockCode} 任务 ${taskId} 失败 (第${stockState.consecutiveErrors}次):`, error);
        
        // 处理速率限制错误
        if (error.response?.status === 429 || error.message?.includes('RATE_LIMIT_EXCEEDED')) {
          console.warn(`⚠️ 股票 ${stockCode} 速率限制，增加轮询间隔到 ${stockState.currentDelay * 2}ms`);
          stockState.currentDelay = Math.min(stockState.currentDelay * 2, 30000); // 最大30秒
        } else {
          // 其他错误，适度增加延迟
          stockState.currentDelay = Math.min(stockState.currentDelay * 1.5, 15000); // 最大15秒
        }
        
        // 如果连续错误太多，停止轮询
        if (stockState.consecutiveErrors >= 10) {
          console.error(`❌ 股票 ${stockCode} 连续错误过多，停止轮询`);
          clearTimeout(pollingRefs.current.get(taskId));
          pollingRefs.current.delete(taskId);
          stockState.activeTasks.delete(taskId);
          
          // 清理股票状态
          if (stockState.activeTasks.size === 0) {
            stockPollingStates.current.delete(stockCode);
          }
          return;
        }
        
        // 继续轮询，使用新的延迟
        const nextPoll = setTimeout(poll, stockState.currentDelay);
        pollingRefs.current.set(taskId, nextPoll);
      }
    };

    // 开始第一次轮询
    const initialPoll = setTimeout(poll, stockState.currentDelay);
    pollingRefs.current.set(taskId, initialPoll);
  };

  // 停止轮询
  const stopPolling = (taskId) => {
    const timeout = pollingRefs.current.get(taskId);
    if (timeout) {
      clearTimeout(timeout);
      pollingRefs.current.delete(taskId);
    }
    
    // 清理股票状态
    const task = tasks.find(t => t.taskId === taskId);
    if (task) {
      const stockState = stockPollingStates.current.get(task.stockCode);
      if (stockState) {
        stockState.activeTasks.delete(taskId);
        if (stockState.activeTasks.size === 0) {
          stockPollingStates.current.delete(task.stockCode);
        }
      }
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
      pollingRefs.current.forEach((timeout) => {
        clearTimeout(timeout);
      });
      pollingRefs.current.clear();
      stockPollingStates.current.clear();
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
              {item.stockName && (
                <Text style={styles.stockName}> - {item.stockName}</Text>
              )}
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
     marginRight: 4,
   },
   stockName: {
     fontSize: 16,
     fontWeight: '500',
     color: '#666666',
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
