#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
测试股票资讯爬取功能
"""

import sys
import os
import json
from EastMoneyStockNews import EastMoneyStockNews

def test_stock_news():
    """测试股票资讯爬取"""
    
    # 设置DeepSeek API密钥
    deepseek_api_key = "sk-2c60a0afb4004678be7c5703e940d360"  # 从配置文件获取
    
    # 测试股票代码
    stock_code = "688333"  # 铂力特
    
    try:
        print(f"开始测试股票 {stock_code} 的资讯爬取...")
        
        # 创建爬取器
        news_crawler = EastMoneyStockNews(deepseek_api_key)
        
        # 获取原始数据
        print("1. 获取原始资讯数据...")
        raw_data = news_crawler.get_stock_news_data(stock_code)
        
        if not raw_data:
            print("❌ 获取股票资讯数据失败")
            return
        
        print(f"✅ 获取成功:")
        print(f"   - 资讯: {len(raw_data.get('news', []))} 条")
        print(f"   - 公告: {len(raw_data.get('notices', []))} 条")
        print(f"   - 研报: {len(raw_data.get('reports', []))} 条")
        
        # 显示前几条资讯
        if raw_data.get('news'):
            print("\n📰 前3条资讯:")
            for i, news in enumerate(raw_data['news'][:3]):
                print(f"   {i+1}. {news['title']} ({news['date']})")
                print(f"      链接: {news['link']}")
        
        # 过滤最近一周的数据
        print("\n2. 过滤最近一周的数据...")
        filtered_data = news_crawler.filter_recent_week(raw_data)
        
        print(f"✅ 过滤完成:")
        print(f"   - 资讯: {len(filtered_data.get('news', []))} 条")
        print(f"   - 公告: {len(filtered_data.get('notices', []))} 条")
        print(f"   - 研报: {len(filtered_data.get('reports', []))} 条")
        
        # 测试新闻内容获取
        if filtered_data.get('news'):
            print("\n3. 测试新闻内容获取...")
            test_news = filtered_data['news'][0]
            print(f"测试资讯: {test_news['title']}")
            
            content = news_crawler.get_news_content(test_news['link'])
            if content:
                print(f"✅ 内容获取成功，长度: {len(content)} 字符")
                print(f"内容预览: {content[:200]}...")
            else:
                print("❌ 内容获取失败")
        
        # 测试情感分析
        if filtered_data.get('news'):
            print("\n4. 测试情感分析...")
            test_news = filtered_data['news'][0]
            print(f"分析资讯: {test_news['title']}")
            
            # 获取内容
            content = news_crawler.get_news_content(test_news['link'])
            if content:
                # 情感分析
                sentiment = news_crawler.analyze_sentiment(content, test_news['title'])
                print(f"✅ 情感分析完成:")
                print(f"   - 情感倾向: {sentiment.get('sentiment', 'unknown')}")
                print(f"   - 情感强度: {sentiment.get('score', 0)}/10")
                print(f"   - 置信度: {sentiment.get('confidence', 0)}%")
                print(f"   - 分析理由: {sentiment.get('reason', 'N/A')}")
            else:
                print("❌ 无法获取内容，跳过情感分析")
        
        print("\n✅ 测试完成!")
        
        # 保存结果到文件
        output_file = f"stock_news_{stock_code}_test.json"
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(filtered_data, f, ensure_ascii=False, indent=2)
        print(f"结果已保存到: {output_file}")
        
    except Exception as e:
        print(f"❌ 测试失败: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    test_stock_news()
