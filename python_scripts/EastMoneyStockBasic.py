#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
东方财富网股票基础数据获取脚本
获取股票的基本信息：代码、名称、价格、成交量等
"""

import requests
import json
import re
import time
import sys
from typing import Dict, Optional

class EastMoneyStockBasic:
    def __init__(self):
        self.base_url = "https://push2.eastmoney.com/api/qt/stock/get"
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
        })

    def get_stock_basic(self, stock_code: str) -> Optional[Dict]:
        """
        获取股票基础数据
        
        Args:
            stock_code: 股票代码，如 '688333'
            
        Returns:
            股票基础数据字典，失败返回None
        """
        try:
            # 判断股票市场
            if stock_code.startswith(('6', '9')):
                secid = f"1.{stock_code}"  # 上海
            elif stock_code.startswith(('0', '3')):
                secid = f"0.{stock_code}"  # 深圳
            else:
                # 将错误信息输出到stderr，避免干扰stdout的JSON
                print(f"不支持的股票代码格式: {stock_code}", file=sys.stderr)
                return None

            # 构建请求参数
            params = {
                'invt': 2,
                'fltt': 1,
                'cb': f'jQuery{int(time.time() * 1000)}',
                'fields': 'f58,f734,f107,f57,f43,f44,f45,f46,f47,f48,f49,f50,f51,f52,f59,f60,f71,f84,f85,f86,f92,f107,f108,f111,f116,f117,f152,f161,f162,f163,f164,f167,f168,f169,f170,f171,f177,f191,f192,f260,f261,f262,f277,f278,f279,f288,f292,f294,f295,f301,f31,f32,f33,f34,f35,f36,f37,f38,f39,f40,f19,f20,f17,f18,f15,f16,f13,f14,f11,f12,f734,f747,f748',
                'secid': secid,
                'ut': 'fa5fd1943c7b386f172d6893dbfba10b',
                'wbp2u': '|0|0|0|web',
                'dect': 1,
                '_': int(time.time() * 1000)
            }

            response = self.session.get(self.base_url, params=params, timeout=10)
            response.raise_for_status()

            # 解析JSONP响应
            content = response.text
            
            # 尝试多种方法解析JSONP
            data = None
            
            # 方法1: 正则表达式
            json_match = re.search(r'jQuery\d+_\d+\((.*)\)', content)
            if json_match:
                try:
                    json_str = json_match.group(1)
                    data = json.loads(json_str)
                except json.JSONDecodeError as e:
                    print(f"正则表达式方法JSON解析失败: {e}", file=sys.stderr)
            
            # 方法2: 直接查找括号
            if not data:
                try:
                    start = content.find('(')
                    end = content.rfind(')')
                    if start != -1 and end != -1 and start < end:
                        json_str = content[start+1:end]
                        data = json.loads(json_str)
                except (json.JSONDecodeError, ValueError) as e:
                    print(f"括号查找方法失败: {e}", file=sys.stderr)
            
            if not data:
                print("所有解析方法都失败了", file=sys.stderr)
                return None

            if data.get('rc') != 0:
                print(f"API返回错误: {data}", file=sys.stderr)
                return None

            stock_data = data.get('data', {})
            if not stock_data:
                print(f"未获取到股票数据: {data}", file=sys.stderr)
                return None

            # 提取关键信息
            result = {
                'stockCode': stock_data.get('f57', stock_code),
                'stockName': stock_data.get('f58', ''),
                'companyName': stock_data.get('f734', ''),
                'currentPrice': stock_data.get('f43', 0) / 100,  # 最新价格
                'highPrice': stock_data.get('f44', 0) / 100,    # 最高价
                'lowPrice': stock_data.get('f45', 0) / 100,     # 最低价
                'openPrice': stock_data.get('f46', 0) / 100,    # 今开
                'volume': stock_data.get('f47', 0),             # 成交量
                'amount': stock_data.get('f48', 0),             # 成交额
                'volumeRatio': stock_data.get('f50', 0),        # 量比
                'limitUp': stock_data.get('f51', 0) / 100,     # 涨停价
                'limitDown': stock_data.get('f52', 0) / 100,   # 跌停价
                'prevClose': stock_data.get('f60', 0) / 100,   # 昨收
                'avgPrice': stock_data.get('f71', 0) / 100,    # 均价
                'totalShares': stock_data.get('f84', 0),        # 总股本
                'circulatingShares': stock_data.get('f85', 0),  # 流通股本
                'totalMarketValue': stock_data.get('f116', 0),  # 总市值
                'circulatingMarketValue': stock_data.get('f117', 0), # 流通市值
                'peTTM': stock_data.get('f164', 0),            # 市盈率TTM
                'pb': stock_data.get('f167', 0),               # 市净率
                'turnoverRate': stock_data.get('f168', 0),      # 换手率
                'amplitude': stock_data.get('f171', 0),         # 振幅
                'change': stock_data.get('f169', 0) / 100,     # 涨跌额
                'changePercent': stock_data.get('f170', 0) / 100, # 涨跌幅
                'timestamp': int(time.time() * 1000)
            }

            return result

        except requests.exceptions.RequestException as e:
            print(f"网络请求失败: {e}", file=sys.stderr)
            return None
        except json.JSONDecodeError as e:
            print(f"JSON解析失败: {e}", file=sys.stderr)
            return None
        except Exception as e:
            print(f"获取股票基础数据失败: {e}", file=sys.stderr)
            return None

    def get_multiple_stocks(self, stock_codes: list) -> Dict[str, Dict]:
        """
        批量获取多只股票的基础数据
        
        Args:
            stock_codes: 股票代码列表
            
        Returns:
            股票代码到基础数据的映射字典
        """
        results = {}
        for stock_code in stock_codes:
            data = self.get_stock_basic(stock_code)
            if data:
                results[stock_code] = data
            time.sleep(0.5)  # 避免请求过于频繁
        return results

def main():
    """测试函数"""
    stock_basic = EastMoneyStockBasic()
    
    # 测试单只股票
    stock_code = sys.argv[1]
    result = stock_basic.get_stock_basic(stock_code)
    
    if result:
        # 仅输出纯JSON到stdout，避免混入任何前缀或日志
        print(json.dumps(result, ensure_ascii=False))
    else:
        # 失败时不向stdout输出任何内容，错误信息写入stderr（由上层捕获）
        pass

if __name__ == "__main__":
    main()
