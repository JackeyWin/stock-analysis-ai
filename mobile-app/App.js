import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createStackNavigator } from '@react-navigation/stack';
import { StatusBar } from 'expo-status-bar';
import { Provider as PaperProvider } from 'react-native-paper';
import { Ionicons } from '@expo/vector-icons';

// 导入屏幕组件
import HomeScreen from './src/screens/HomeScreen';
import SearchScreen from './src/screens/SearchScreen';
import AnalysisScreen from './src/screens/AnalysisScreen';
import SettingsScreen from './src/screens/SettingsScreen';
import StockDetailScreen from './src/screens/StockDetailScreen';
import RecommendationDetailScreen from './src/screens/RecommendationDetailScreen';
import HistoryScreen from './src/screens/HistoryScreen';
import DetailedAnalysisScreen from './src/screens/DetailedAnalysisScreen';
import MonitoringListScreen from './src/screens/MonitoringListScreen';


// 导入主题
import { theme } from './src/utils/theme';

const Tab = createBottomTabNavigator();
const Stack = createStackNavigator();

// 主标签导航
function MainTabs() {
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        tabBarIcon: ({ focused, color, size }) => {
          let iconName;

          if (route.name === 'Home') {
            iconName = focused ? 'home' : 'home-outline';
          } else if (route.name === 'Search') {
            iconName = focused ? 'search' : 'search-outline';
          } else if (route.name === 'Analysis') {
            iconName = focused ? 'analytics' : 'analytics-outline';
          } else if (route.name === 'Settings') {
            iconName = focused ? 'settings' : 'settings-outline';
          }

          return <Ionicons name={iconName} size={size} color={color} />;
        },
        tabBarActiveTintColor: theme.colors.primary,
        tabBarInactiveTintColor: 'gray',
        headerStyle: {
          backgroundColor: theme.colors.primary,
        },
        headerTintColor: '#fff',
        headerTitleStyle: {
          fontWeight: 'bold',
        },
      })}
    >
      <Tab.Screen 
        name="Home" 
        component={HomeScreen} 
        options={{ title: '首页' }}
      />
      <Tab.Screen 
        name="Search" 
        component={SearchScreen} 
        options={{ title: '搜索' }}
      />
      <Tab.Screen 
        name="Analysis" 
        component={AnalysisScreen} 
        options={{ title: '分析' }}
      />
      <Tab.Screen 
        name="Settings" 
        component={SettingsScreen} 
        options={{ title: '设置' }}
      />
    </Tab.Navigator>
  );
}

// 主导航栈
function AppNavigator() {
  return (
    <NavigationContainer>
      <Stack.Navigator>
        <Stack.Screen 
          name="MainTabs" 
          component={MainTabs} 
          options={{ headerShown: false }}
        />
        <Stack.Screen 
          name="StockDetail" 
          component={StockDetailScreen}
          options={{ 
            title: '股票详情',
            headerStyle: {
              backgroundColor: theme.colors.primary,
            },
            headerTintColor: '#fff',
          }}
        />
        <Stack.Screen 
          name="RecommendationDetail" 
          component={RecommendationDetailScreen}
          options={{ 
            title: '推荐详情',
            headerStyle: {
              backgroundColor: theme.colors.primary,
            },
            headerTintColor: '#fff',
          }}
        />
        <Stack.Screen 
          name="History" 
          component={HistoryScreen}
          options={{ 
            title: '历史推荐',
            headerStyle: {
              backgroundColor: theme.colors.primary,
            },
            headerTintColor: '#fff',
          }}
        />
        <Stack.Screen 
          name="DetailedAnalysis" 
          component={DetailedAnalysisScreen}
          options={{ 
            title: 'AI详细分析',
            headerStyle: {
              backgroundColor: theme.colors.primary,
            },
            headerTintColor: '#fff',
          }}
        />
        <Stack.Screen 
          name="MonitoringList" 
          component={MonitoringListScreen}
          options={{ 
            title: '盯盘管理',
            headerStyle: {
              backgroundColor: theme.colors.primary,
            },
            headerTintColor: '#fff',
          }}
        />


      </Stack.Navigator>
    </NavigationContainer>
  );
}

export default function App() {
  return (
    <PaperProvider theme={theme}>
      <StatusBar style="light" backgroundColor={theme.colors.primary} />
      <AppNavigator />
    </PaperProvider>
  );
}
