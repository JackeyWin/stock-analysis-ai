#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
东方财富行业成分股获取脚本

用法:
  python python_scripts/EastMoneySectorStocks.py 行业代码
  python python_scripts/EastMoneySectorStocks.py --list  # 获取行业列表

输出(JSON):
  {
    "sectorCode": "BK0420",
    "sectorName": "软件开发",
    "stocks": [
      {
        "code": "002415",
        "name": "海康威视"
      }
    ]
  }
"""

import json
import sys
import requests
import time
import random
import gzip
from io import BytesIO

# 东方财富API配置
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36",
    "Accept": "*/*",
    "Accept-Encoding": "gzip, deflate, br",
    "Connection": "keep-alive"
}


def get_sector_list():
    """
    获取行业列表
    """
    url = "https://push2.eastmoney.com/api/qt/clist/get"
    params = {
        "pn": 1,
        "pz": 500,  # 获取500个行业
        "po": 1,
        "np": 1,
        "ut": "bd1d9ddb04089700cf9c27f6f7426281",
        "fltt": 2,
        "invt": 2,
        "fid": "f3",
        "fs": "m:90 t:2 f:!50",
        "fields": "f12,f14,f2,f3,f62,f128,f136,f115,f152",
        "_": int(time.time() * 1000)
    }
    
    try:
        response = requests.get(url, params=params, headers=HEADERS, timeout=10)
        response.raise_for_status()
        
        # 打印响应信息用于调试
        print(f"响应状态码: {response.status_code}", file=sys.stderr)
        print(f"响应头 Content-Encoding: {response.headers.get('Content-Encoding', 'None')}", file=sys.stderr)
        print(f"响应头 Content-Type: {response.headers.get('Content-Type', 'None')}", file=sys.stderr)
        
        # 检查响应内容
        content = response.content
        print(f"原始响应内容长度: {len(content)}", file=sys.stderr)
        
        # 尝试多种解压缩方法
        original_content = content
        
        # 方法1: 处理gzip压缩
        if response.headers.get('Content-Encoding') == 'gzip':
            try:
                content = gzip.decompress(content)
                print(f"gzip解压缩后内容长度: {len(content)}", file=sys.stderr)
            except Exception as e:
                print(f"gzip解压缩失败: {e}", file=sys.stderr)
                content = original_content
        
        # 方法2: 尝试br压缩 (Brotli)
        if response.headers.get('Content-Encoding') == 'br':
            try:
                import brotli
                content = brotli.decompress(content)
                print(f"br解压缩后内容长度: {len(content)}", file=sys.stderr)
            except Exception as e:
                print(f"br解压缩失败: {e}", file=sys.stderr)
                content = original_content
        
        # 方法3: 尝试deflate压缩
        if response.headers.get('Content-Encoding') == 'deflate':
            try:
                import zlib
                content = zlib.decompress(content, -zlib.MAX_WBITS)
                print(f"deflate解压缩后内容长度: {len(content)}", file=sys.stderr)
            except Exception as e:
                print(f"deflate解压缩失败: {e}", file=sys.stderr)
                content = original_content
        
        # 尝试不同的编码解码
        json_text = ""
        for encoding in ['utf-8', 'gbk', 'gb2312']:
            try:
                json_text = content.decode(encoding).strip()
                print(f"使用 {encoding} 编码解码成功", file=sys.stderr)
                break
            except Exception as e:
                print(f"使用 {encoding} 编码解码失败: {e}", file=sys.stderr)
                continue
        
        # 如果所有编码都失败，使用默认解码
        if not json_text:
            json_text = content.decode('utf-8', errors='ignore').strip()
            print("使用 utf-8 忽略错误方式解码", file=sys.stderr)
        
        # 处理JSONP响应
        if json_text.startswith("("):
            json_text = json_text[1:]
        if json_text.endswith(")"):
            json_text = json_text[:-1]
        
        # 打印调试信息
        print(f"解码后响应内容前200字符: {repr(json_text[:200])}", file=sys.stderr)
        
        # 检查是否为空响应
        if not json_text:
            print("API返回空响应", file=sys.stderr)
            return []
        
        data = json.loads(json_text)
        
        sectors = []
        if data.get("rc") == 0 and "data" in data and "diff" in data["data"]:
            for item in data["data"]["diff"]:
                sectors.append({
                    "code": item.get("f12", ""),
                    "name": item.get("f14", "")
                })
        
        return sectors
    except json.JSONDecodeError as e:
        print(f"JSON解析失败: {e}", file=sys.stderr)
        print(f"响应内容: {response.text[:200]}...", file=sys.stderr)
        return []
    except Exception as e:
        print(f"获取行业列表失败: {e}", file=sys.stderr)
        return []


def get_sector_stocks(sector_code):
    """
    获取指定行业的成分股
    :param sector_code: 行业代码，如 BK0420
    """
    url = "https://push2.eastmoney.com/api/qt/clist/get"
    params = {
        "pn": 1,
        "pz": 1000,  # 获取最多1000只股票
        "po": 1,
        "np": 1,
        "ut": "bd1d9ddb04089700cf9c27f6f7426281",
        "fltt": 2,
        "invt": 2,
        "fid": "f3",
        "fs": f"b:{sector_code} f:!50",
        "fields": "f12,f14,f2,f3,f62,f128,f136,f115,f152",
        "_": int(time.time() * 1000)
    }
    
    try:
        response = requests.get(url, params=params, headers=HEADERS, timeout=10)
        response.raise_for_status()
        
        # 打印响应信息用于调试
        print(f"响应状态码: {response.status_code}", file=sys.stderr)
        print(f"响应头 Content-Encoding: {response.headers.get('Content-Encoding', 'None')}", file=sys.stderr)
        print(f"响应头 Content-Type: {response.headers.get('Content-Type', 'None')}", file=sys.stderr)
        
        # 检查响应内容
        content = response.content
        print(f"原始响应内容长度: {len(content)}", file=sys.stderr)
        
        # 尝试多种解压缩方法
        original_content = content
        
        # 方法1: 处理gzip压缩
        if response.headers.get('Content-Encoding') == 'gzip':
            try:
                content = gzip.decompress(content)
                print(f"gzip解压缩后内容长度: {len(content)}", file=sys.stderr)
            except Exception as e:
                print(f"gzip解压缩失败: {e}", file=sys.stderr)
                content = original_content
        
        # 方法2: 尝试br压缩 (Brotli)
        if response.headers.get('Content-Encoding') == 'br':
            try:
                import brotli
                content = brotli.decompress(content)
                print(f"br解压缩后内容长度: {len(content)}", file=sys.stderr)
            except Exception as e:
                print(f"br解压缩失败: {e}", file=sys.stderr)
                content = original_content
        
        # 方法3: 尝试deflate压缩
        if response.headers.get('Content-Encoding') == 'deflate':
            try:
                import zlib
                content = zlib.decompress(content, -zlib.MAX_WBITS)
                print(f"deflate解压缩后内容长度: {len(content)}", file=sys.stderr)
            except Exception as e:
                print(f"deflate解压缩失败: {e}", file=sys.stderr)
                content = original_content
        
        # 尝试不同的编码解码
        json_text = ""
        for encoding in ['utf-8', 'gbk', 'gb2312']:
            try:
                json_text = content.decode(encoding).strip()
                print(f"使用 {encoding} 编码解码成功", file=sys.stderr)
                break
            except Exception as e:
                print(f"使用 {encoding} 编码解码失败: {e}", file=sys.stderr)
                continue
        
        # 如果所有编码都失败，使用默认解码
        if not json_text:
            json_text = content.decode('utf-8', errors='ignore').strip()
            print("使用 utf-8 忽略错误方式解码", file=sys.stderr)
        
        # 处理JSONP响应
        if json_text.startswith("("):
            json_text = json_text[1:]
        if json_text.endswith(")"):
            json_text = json_text[:-1]
        
        # 打印调试信息
        print(f"解码后响应内容前200字符: {repr(json_text[:200])}", file=sys.stderr)
        
        data = json.loads(json_text)
        
        stocks = []
        sector_name = ""
        
        if data.get("rc") == 0 and "data" in data and "diff" in data["data"]:
            for item in data["data"]["diff"]:
                # 获取行业名称（从第一条记录中获取）
                if not sector_name and "f12" in item and item["f12"] == sector_code:
                    sector_name = item.get("f14", "")
                
                stocks.append({
                    "code": item.get("f12", ""),
                    "name": item.get("f14", "")
                })
        
        return {
            "sectorCode": sector_code,
            "sectorName": sector_name,
            "stocks": stocks
        }
    except Exception as e:
        print(f"获取行业成分股失败: {e}", file=sys.stderr)
        return {
            "sectorCode": sector_code,
            "sectorName": "",
            "stocks": []
        }


def get_sector_code_by_name(sector_name):
    """
    根据行业名称获取行业代码
    :param sector_name: 行业名称
    """
    sectors = get_sector_list()
    for sector in sectors:
        if sector["name"] == sector_name:
            return sector["code"]
    return None


def main():
    if len(sys.argv) < 2:
        print("用法:")
        print("  python EastMoneySectorStocks.py 行业代码")
        print("  python EastMoneySectorStocks.py --list  # 获取行业列表")
        print("  python EastMoneySectorStocks.py --name 行业名称  # 根据行业名称获取成分股")
        return
    
    if sys.argv[1] == "--list":
        # 获取行业列表
        sectors = get_sector_list()
        print(json.dumps(sectors, ensure_ascii=False, indent=2))
    elif sys.argv[1] == "--name":
        if len(sys.argv) < 3:
            print("请提供行业名称")
            return
        
        # 根据行业名称获取成分股
        sector_name = sys.argv[2]
        sector_code = get_sector_code_by_name(sector_name)
        
        if not sector_code:
            print(f"未找到行业名称 '{sector_name}' 对应的代码")
            return
        
        result = get_sector_stocks(sector_code)
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        # 根据行业代码获取成分股
        sector_code = sys.argv[1]
        result = get_sector_stocks(sector_code)
        print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()