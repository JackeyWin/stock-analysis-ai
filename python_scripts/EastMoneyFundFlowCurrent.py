#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
东方财富网 - 股票当前资金流向数据获取
获取股票的实时资金流向情况，包括超大单、大单、中单、小单的净比数据
"""

import requests
import json
import sys
import time
from typing import Dict, Any, Optional

class EastMoneyFundFlowCurrent:
    def __init__(self):
        self.base_url = "https://push2.eastmoney.com/api/qt/ulist.np/get"
        self.headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
            'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
            'Accept-Encoding': 'gzip, deflate, br',
            'Connection': 'keep-alive',
            'Upgrade-Insecure-Requests': '1',
            'Cache-Control': 'max-age=0',
            'Referer': 'https://quote.eastmoney.com/',
        }
        
    def get_fund_flow_current(self, stock_code: str) -> Dict[str, Any]:
        """
        获取股票当前资金流向数据
        
        Args:
            stock_code (str): 股票代码
            
        Returns:
            Dict[str, Any]: 资金流向数据
        """
        try:
            # 构建请求参数
            params = {
                'cb': f'jQuery{int(time.time() * 1000)}',
                'fltt': '2',
                'secids': f'1.{stock_code}' if stock_code.startswith('6') else f'0.{stock_code}',
                'fields': 'f62,f184,f66,f69,f72,f75,f78,f81,f84,f87,f64,f65,f70,f71,f76,f77,f82,f83,f164,f166,f168,f170,f172,f252,f253,f254,f255,f256,f124,f6,f278,f279,f280,f281,f282',
                'ut': 'b2884a393a59ad64002292a3e90d46a5',
                '_': int(time.time() * 1000)
            }
            
            # 发送请求
            response = requests.get(
                self.base_url,
                params=params,
                headers=self.headers,
                timeout=10,
                proxies=None  # 禁用代理
            )
            response.raise_for_status()
            
            # 解析JSONP响应
            text = response.text
            json_start = text.find('(') + 1
            json_end = text.rfind(')')
            
            if json_start > 0 and json_end > json_start:
                json_str = text[json_start:json_end]
                data = json.loads(json_str)
            else:
                raise ValueError("无法解析JSONP响应")
            
            # 检查响应状态
            if data.get('rc') != 0:
                raise ValueError(f"API返回错误: {data.get('rt', '未知错误')}")
            
            # 提取资金流向数据
            if 'data' in data and 'diff' in data['data'] and len(data['data']['diff']) > 0:
                flow_data = data['data']['diff'][0]
                
                result = {
                    'success': True,
                    'data': {
                        'stock_code': stock_code,
                        'timestamp': int(time.time()),
                        'fund_flow': {
                            'super_large_net_ratio': flow_data.get('f69', 0),  # 超大单净比（%）
                            'large_net_ratio': flow_data.get('f75', 0),       # 大单净比（%）
                            'medium_net_ratio': flow_data.get('f81', 0),      # 中单净比（%）
                            'small_net_ratio': flow_data.get('f87', 0),       # 小单净比（%）
                            'main_force_net_ratio': flow_data.get('f184', 0), # 主力净比（%）
                            'main_force_net_amount': flow_data.get('f62', 0), # 主力净额
                            'super_large_net_amount': flow_data.get('f66', 0), # 超大单净额
                            'large_net_amount': flow_data.get('f72', 0),       # 大单净额
                            'medium_net_amount': flow_data.get('f78', 0),      # 中单净额
                            'small_net_amount': flow_data.get('f84', 0),       # 小单净额
                            'total_amount': flow_data.get('f6', 0),            # 总成交额
                        }
                    }
                }
                
                return result
            else:
                return {
                    'success': False,
                    'error': '未找到资金流向数据',
                    'data': None
                }
                
        except requests.exceptions.RequestException as e:
            return {
                'success': False,
                'error': f'网络请求失败: {str(e)}',
                'data': None
            }
        except json.JSONDecodeError as e:
            return {
                'success': False,
                'error': f'JSON解析失败: {str(e)}',
                'data': None
            }
        except Exception as e:
            return {
                'success': False,
                'error': f'获取资金流向数据失败: {str(e)}',
                'data': None
            }

def main():
    """主函数 - 用于测试"""
    if len(sys.argv) != 2:
        print("使用方法: python EastMoneyFundFlowCurrent.py <股票代码>")
        print("示例: python EastMoneyFundFlowCurrent.py 688333")
        sys.exit(1)
    
    stock_code = sys.argv[1]
    fund_flow = EastMoneyFundFlowCurrent()
    
    # 不输出中文，只输出JSON数据
    result = fund_flow.get_fund_flow_current(stock_code)
    
    if result['success']:
        print(json.dumps(result, ensure_ascii=False))
    else:
        print(json.dumps({"error": result['error']}, ensure_ascii=False))
        sys.exit(1)

if __name__ == "__main__":
    main()
