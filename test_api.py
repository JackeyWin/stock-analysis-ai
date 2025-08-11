#!/usr/bin/env python3
"""
股票分析API测试脚本
"""

import requests
import json
import time

# API基础URL
BASE_URL = "http://localhost:8080/api/stock"

def test_health():
    """测试健康检查接口"""
    print("=== 测试健康检查接口 ===")
    try:
        response = requests.get(f"{BASE_URL}/health", timeout=10)
        print(f"状态码: {response.status_code}")
        print(f"响应: {response.text}")
        return response.status_code == 200
    except Exception as e:
        print(f"健康检查失败: {e}")
        return False

def test_analyze_stock_get(stock_code="000001"):
    """测试GET方式的股票分析接口"""
    print(f"\n=== 测试GET方式分析股票 {stock_code} ===")
    try:
        response = requests.get(f"{BASE_URL}/analyze/{stock_code}", timeout=120)
        print(f"状态码: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            print("分析成功!")
            print(f"股票代码: {data.get('stockCode')}")
            print(f"成功状态: {data.get('success')}")
            
            # 打印AI分析结果
            ai_result = data.get('aiAnalysisResult')
            if ai_result:
                print("\n=== AI分析结果 ===")
                print(f"趋势分析: {ai_result.get('trendAnalysis', 'N/A')}")
                print(f"技术形态: {ai_result.get('technicalPattern', 'N/A')}")
                print(f"移动平均线: {ai_result.get('movingAverage', 'N/A')}")
                print(f"RSI指标: {ai_result.get('rsiAnalysis', 'N/A')}")
                print(f"价格预测: {ai_result.get('pricePredict', 'N/A')}")
                print(f"交易建议: {ai_result.get('tradingAdvice', 'N/A')}")
        else:
            print(f"分析失败: {response.text}")
            
        return response.status_code == 200
        
    except Exception as e:
        print(f"GET分析失败: {e}")
        return False

def test_analyze_stock_post(stock_code="000001", days=250):
    """测试POST方式的股票分析接口"""
    print(f"\n=== 测试POST方式分析股票 {stock_code} ===")
    try:
        payload = {
            "stockCode": stock_code,
            "days": days
        }
        
        response = requests.post(
            f"{BASE_URL}/analyze",
            json=payload,
            headers={"Content-Type": "application/json"},
            timeout=120
        )
        
        print(f"状态码: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            print("分析成功!")
            print(f"股票代码: {data.get('stockCode')}")
            print(f"成功状态: {data.get('success')}")
            
            # 打印技术指标数据
            tech_indicators = data.get('technicalIndicators')
            if tech_indicators:
                print("\n=== 技术指标 ===")
                print(json.dumps(tech_indicators, ensure_ascii=False, indent=2))
            
            # 打印AI分析结果
            ai_result = data.get('aiAnalysisResult')
            if ai_result:
                print("\n=== AI分析结果 ===")
                print(f"完整分析: {ai_result.get('fullAnalysis', 'N/A')}")
        else:
            print(f"分析失败: {response.text}")
            
        return response.status_code == 200
        
    except Exception as e:
        print(f"POST分析失败: {e}")
        return False

def main():
    """主测试函数"""
    print("股票分析API测试开始...")
    
    # 等待服务启动
    print("等待服务启动...")
    time.sleep(2)
    
    # 测试健康检查
    if not test_health():
        print("服务未启动，请先启动应用")
        return
    
    # 测试股票分析
    test_stocks = ["000001", "600036", "000002"]
    
    for stock_code in test_stocks:
        print(f"\n{'='*50}")
        print(f"测试股票: {stock_code}")
        print(f"{'='*50}")
        
        # 测试GET接口
        success_get = test_analyze_stock_get(stock_code)
        
        # 测试POST接口
        success_post = test_analyze_stock_post(stock_code)
        
        if success_get and success_post:
            print(f"✅ 股票 {stock_code} 测试通过")
        else:
            print(f"❌ 股票 {stock_code} 测试失败")
        
        # 避免请求过于频繁
        time.sleep(1)
    
    print("\n测试完成!")

if __name__ == "__main__":
    main()