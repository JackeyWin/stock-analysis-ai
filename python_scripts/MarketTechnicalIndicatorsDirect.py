#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
直接计算大盘技术指标脚本
避免通过临时文件传输数据，直接查询数据并计算
"""

import sys
import json
import pandas as pd
import numpy as np
from datetime import datetime, timedelta
import requests
import time

def get_market_kline_data():
    """获取大盘K线数据（上证指数）"""
    try:
        # 构建请求URL - 上证指数
        url = "http://push2his.eastmoney.com/api/qt/stock/kline/get"
        
        # 计算时间范围（最近240个交易日）
        end_date = datetime.now()
        start_date = end_date - timedelta(days=365)  # 获取一年的数据
        
        params = {
            'secid': '1.000001',  # 上证指数
            'ut': 'fa5fd1943c7b386f172d6893dbfba10b',
            'fields1': 'f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11,f12,f13',
            'fields2': 'f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61',
            'klt': '101',  # 日K线
            'fqt': '1',    # 前复权
            'beg': start_date.strftime('%Y%m%d'),
            'end': end_date.strftime('%Y%m%d'),
            'smplmt': '240',
            'lmt': '240'
        }
        
        response = requests.get(url, params=params, timeout=30)
        response.raise_for_status()
        
        data = response.json()
        
        if data['rc'] != 0 or 'data' not in data:
            raise Exception(f"API返回错误: {data.get('msg', '未知错误')}")
        
        kline_data = data['data']['klines']
        
        # 解析K线数据
        parsed_data = []
        for line in kline_data:
            parts = line.split(',')
            if len(parts) >= 7:
                parsed_data.append({
                    'date': parts[0],
                    'open': float(parts[1]),
                    'close': float(parts[2]),
                    'high': float(parts[3]),
                    'low': float(parts[4]),
                    'volume': float(parts[5]),
                    'amount': float(parts[6])
                })
        
        return parsed_data
        
    except Exception as e:
        print(f"获取大盘K线数据失败: {str(e)}", file=sys.stderr)
        return []

def calculate_ma(data, periods):
    """计算移动平均线"""
    df = pd.DataFrame(data)
    ma_results = {}
    
    for period in periods:
        if len(df) >= period:
            ma_results[f'MA{period}'] = df['close'].rolling(window=period).mean().iloc[-1]
        else:
            ma_results[f'MA{period}'] = None
    
    return ma_results

def calculate_bollinger_bands(data, period=20, std_dev=2):
    """计算布林带"""
    df = pd.DataFrame(data)
    
    if len(df) < period:
        return {'upper_band': None, 'middle_band': None, 'lower_band': None}
    
    middle_band = df['close'].rolling(window=period).mean().iloc[-1]
    std = df['close'].rolling(window=period).std().iloc[-1]
    
    upper_band = middle_band + (std * std_dev)
    lower_band = middle_band - (std * std_dev)
    
    return {
        'upper_band': round(upper_band, 2),
        'middle_band': round(middle_band, 2),
        'lower_band': round(lower_band, 2)
    }

def calculate_rsi(data, period=14):
    """计算RSI指标"""
    df = pd.DataFrame(data)
    
    if len(df) < period + 1:
        return None
    
    delta = df['close'].diff()
    gain = (delta.where(delta > 0, 0)).rolling(window=period).mean()
    loss = (-delta.where(delta < 0, 0)).rolling(window=period).mean()
    
    rs = gain / loss
    rsi = 100 - (100 / (1 + rs))
    
    return round(rsi.iloc[-1], 2)

def calculate_macd(data, fast=12, slow=26, signal=9):
    """计算MACD指标"""
    df = pd.DataFrame(data)
    
    if len(df) < slow:
        return {'macd': None, 'signal': None, 'histogram': None}
    
    ema_fast = df['close'].ewm(span=fast).mean()
    ema_slow = df['close'].ewm(span=slow).mean()
    
    macd_line = ema_fast - ema_slow
    signal_line = macd_line.ewm(span=signal).mean()
    histogram = macd_line - signal_line
    
    return {
        'macd': round(macd_line.iloc[-1], 4),
        'signal': round(signal_line.iloc[-1], 4),
        'histogram': round(histogram.iloc[-1], 4)
    }

def calculate_market_trend(data):
    """计算市场趋势指标"""
    df = pd.DataFrame(data)
    
    if len(df) < 20:
        return {'trend': '未知', 'strength': None}
    
    # 计算20日均线
    ma20 = df['close'].rolling(window=20).mean().iloc[-1]
    current_price = df['close'].iloc[-1]
    
    # 计算趋势强度
    price_change = ((current_price - ma20) / ma20) * 100
    
    # 判断趋势
    if price_change > 2:
        trend = '强势上涨'
    elif price_change > 0:
        trend = '温和上涨'
    elif price_change > -2:
        trend = '温和下跌'
    else:
        trend = '强势下跌'
    
    return {
        'trend': trend,
        'strength': round(price_change, 2),
        'ma20': round(ma20, 2),
        'current_price': round(current_price, 2)
    }

def calculate_volatility(data, period=20):
    """计算波动率"""
    df = pd.DataFrame(data)
    
    if len(df) < period:
        return None
    
    # 计算日收益率
    returns = df['close'].pct_change().dropna()
    
    # 计算波动率（年化）
    volatility = returns.rolling(window=period).std().iloc[-1] * np.sqrt(252) * 100
    
    return round(volatility, 2)

def calculate_support_resistance(data):
    """计算支撑阻力位"""
    df = pd.DataFrame(data)
    
    if len(df) < 20:
        return {'support': None, 'resistance': None}
    
    # 最近20天的最高价和最低价
    recent_high = df['high'].tail(20).max()
    recent_low = df['low'].tail(20).min()
    
    # 当前价格
    current_price = df['close'].iloc[-1]
    
    # 计算支撑和阻力位
    support = round(recent_low * 0.98, 2)  # 支撑位略低于最低价
    resistance = round(recent_high * 1.02, 2)  # 阻力位略高于最高价
    
    return {
        'support': support,
        'resistance': resistance,
        'current_price': round(current_price, 2)
    }

def main():
    try:
        # 获取大盘K线数据
        kline_data = get_market_kline_data()
        
        if not kline_data:
            print(json.dumps({'error': '无法获取大盘K线数据'}), file=sys.stderr)
            sys.exit(1)
        
        # 计算各项技术指标
        ma_results = calculate_ma(kline_data, [5, 10, 20, 30, 60])
        bb_results = calculate_bollinger_bands(kline_data)
        rsi_value = calculate_rsi(kline_data)
        macd_results = calculate_macd(kline_data)
        trend_results = calculate_market_trend(kline_data)
        volatility_value = calculate_volatility(kline_data)
        sr_results = calculate_support_resistance(kline_data)
        
        # 组装结果
        result = {
            'market_type': '上证指数',
            'calculation_time': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'data_points': len(kline_data),
            'moving_averages': ma_results,
            'bollinger_bands': bb_results,
            'rsi': rsi_value,
            'macd': macd_results,
            'market_trend': trend_results,
            'volatility': volatility_value,
            'support_resistance': sr_results
        }
        
        # 输出JSON结果
        print(json.dumps(result, ensure_ascii=False, indent=2))
        
    except Exception as e:
        error_result = {
            'error': f'计算大盘技术指标失败: {str(e)}',
            'market_type': '上证指数'
        }
        print(json.dumps(error_result, ensure_ascii=False), file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
