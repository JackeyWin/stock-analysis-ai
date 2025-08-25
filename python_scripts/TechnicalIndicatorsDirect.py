#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
直接计算技术指标脚本
避免通过临时文件传输数据，直接查询数据并计算
"""

import sys
import json
import pandas as pd
import numpy as np
from datetime import datetime, timedelta
import requests
import time

def get_stock_kline_data(stock_code):
    """获取股票K线数据"""
    try:
        # 构建请求URL
        url = "http://push2his.eastmoney.com/api/qt/stock/kline/get"
        
        # 计算时间范围（最近240个交易日）
        end_date = datetime.now()
        start_date = end_date - timedelta(days=365)  # 获取一年的数据
        
        params = {
            'secid': f'1.{stock_code}' if stock_code.startswith('6') else f'0.{stock_code}',
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
        print(f"获取K线数据失败: {str(e)}", file=sys.stderr)
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

def calculate_kdj(data, n=9, m1=3, m2=3):
    """计算KDJ指标"""
    df = pd.DataFrame(data)
    
    if len(df) < n:
        return {'k': None, 'd': None, 'j': None}
    
    low_min = df['low'].rolling(window=n).min()
    high_max = df['high'].rolling(window=n).max()
    
    rsv = 100 * ((df['close'] - low_min) / (high_max - low_min))
    
    k = rsv.ewm(com=m1-1).mean()
    d = k.ewm(com=m2-1).mean()
    j = 3 * k - 2 * d
    
    return {
        'k': round(k.iloc[-1], 2),
        'd': round(d.iloc[-1], 2),
        'j': round(j.iloc[-1], 2)
    }

def calculate_volume_indicators(data):
    """计算成交量指标"""
    df = pd.DataFrame(data)
    
    if len(df) < 20:
        return {'volume_ma': None, 'volume_ratio': None}
    
    volume_ma = df['volume'].rolling(window=20).mean().iloc[-1]
    current_volume = df['volume'].iloc[-1]
    volume_ratio = current_volume / volume_ma if volume_ma > 0 else 1
    
    return {
        'volume_ma': round(volume_ma, 0),
        'volume_ratio': round(volume_ratio, 2)
    }

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
    if len(sys.argv) != 2:
        print(json.dumps({'error': '请提供股票代码参数'}), file=sys.stderr)
        sys.exit(1)
    
    stock_code = sys.argv[1]
    
    try:
        # 获取K线数据
        kline_data = get_stock_kline_data(stock_code)
        
        if not kline_data:
            print(json.dumps({'error': '无法获取K线数据'}), file=sys.stderr)
            sys.exit(1)
        
        # 计算各项技术指标
        ma_results = calculate_ma(kline_data, [5, 10, 20, 30, 60])
        bb_results = calculate_bollinger_bands(kline_data)
        rsi_value = calculate_rsi(kline_data)
        macd_results = calculate_macd(kline_data)
        kdj_results = calculate_kdj(kline_data)
        volume_results = calculate_volume_indicators(kline_data)
        sr_results = calculate_support_resistance(kline_data)
        
        # 组装结果
        result = {
            'stock_code': stock_code,
            'calculation_time': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'data_points': len(kline_data),
            'moving_averages': ma_results,
            'bollinger_bands': bb_results,
            'rsi': rsi_value,
            'macd': macd_results,
            'kdj': kdj_results,
            'volume_indicators': volume_results,
            'support_resistance': sr_results
        }
        
        # 输出JSON结果
        print(json.dumps(result, ensure_ascii=False, indent=2))
        
    except Exception as e:
        error_result = {
            'error': f'计算技术指标失败: {str(e)}',
            'stock_code': stock_code
        }
        print(json.dumps(error_result, ensure_ascii=False), file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
