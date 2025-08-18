#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
测试同行比较数据提取功能
"""

import sys
import os
import json
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from EastMoneyPeerComparison import EastMoneyPeerComparison


def test_peer_comparison():
    """测试同行比较数据提取"""
    print("测试同行比较数据提取功能...")
    
    # 创建实例
    extractor = EastMoneyPeerComparison()
    
    # 测试股票代码
    test_stock_code = "688333"
    
    print(f"正在获取股票 {test_stock_code} 的同行比较数据...")
    
    try:
        # 获取数据
        result = extractor.get_stock_peer_data(test_stock_code)
        
        if result["success"]:
            print("✅ 成功获取同行比较数据:")
            print(json.dumps(result["data"], ensure_ascii=False, indent=2))
        else:
            print("❌ 获取数据失败:")
            print(result.get("error", "未知错误"))
            
    except Exception as e:
        print(f"❌ 测试过程中发生错误: {str(e)}")


if __name__ == "__main__":
    test_peer_comparison()
