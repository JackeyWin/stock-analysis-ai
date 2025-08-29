from curl_cffi import requests
import json
import time
import re
import sys


def get_market_index_for_stock(stock_code):
    """
    根据股票代码判断所属大盘指数

    参数:
        stock_code (str): 股票代码（如'600986'）

    返回:
        tuple: (市场名称, secid)
    """
    # 判断股票市场
    if stock_code.startswith('6'):
        return "上证指数", "1.000001"
    elif stock_code.startswith('0') or stock_code.startswith('3'):
        return "深证成指", "0.399001"
    else:
        # 默认返回上证指数
        return "上证指数", "1.000001"


def get_market_kline_data(stock_code=None, secid=None):
    # 确定secid
    if secid:
        market_name = "上证指数" if secid.startswith("1.") else "深证成指"
    elif stock_code:
        market_name, secid = get_market_index_for_stock(stock_code)
    else:
        # 默认使用上证指数
        market_name, secid = "上证指数", "1.000001"

    # 生成动态回调函数名
    timestamp = int(time.time() * 1000)

    # 请求参数
    params = {
        "fields1": "f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11,f12,f13",
        "fields2": "f51,f52,f53,f54,f55,f56,f57,f58",
        "iscr": "0",
        "ndays": "1",
        "ut": "fa5fd1943c7b386f172d6893dbfba10b",
        "cb": "jQuery112302465227496454343_1753342967210",
        "secid": secid,
        "_": timestamp
    }

    url = "https://push2.eastmoney.com/api/qt/stock/trends2/get"

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": "https://quote.eastmoney.com/",
        "Accept": "*/*"
    }

    try:
        # 发送请求
        response = requests.get(url, headers=headers, params=params)
        response.raise_for_status()

        # 改进的JSON解析逻辑
        raw_text = response.text

        # 尝试直接解析JSON（适用于无回调函数的情况）
        try:
            data = json.loads(raw_text)
        except json.JSONDecodeError:
            # 处理JSONP格式（带回调函数）
            if '(' in raw_text and ')' in raw_text:
                # 提取JSON数据部分
                json_start = raw_text.find('(') + 1
                json_end = raw_text.rfind(')')
                json_str = raw_text[json_start:json_end]
                data = json.loads(json_str)
            else:
                raise ValueError("无法识别的返回格式")

        # 检查返回状态
        if data.get("rc") != 0:
            return None

        # 解析分时数据
        trends = data.get("data", {}).get("trends", [])
        if not trends:
            return None

        result = []
        for item in trends:
            parts = item.split(',')
            if len(parts) < 8:
                continue

            try:
                record = {
                    "t": parts[0],
                    "o": float(parts[1]),
                    "p": float(parts[2]),
                    "h": float(parts[3]),
                    "l": float(parts[4]),
                    "v": int(parts[5]),
                    "tu": float(parts[6]),
                    "avg": float(parts[7])
                }
                result.append(record)
            except (ValueError, TypeError):
                continue

        return result

    except requests.exceptions.RequestException as e:
        print(f"网络请求失败: {str(e)}")
    except Exception as e:
        print(f"数据处理出错: {str(e)}")

    return None


# 示例使用
if __name__ == "__main__":
    stock_code = sys.argv[1] if len(sys.argv) > 1 else "600986"
    result = get_market_kline_data(stock_code=stock_code)

    if result:
        print(json.dumps(result, indent=2, ensure_ascii=False))