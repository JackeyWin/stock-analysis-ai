from curl_cffi import requests
import json
import time
import re
import sys
import logging
from functools import wraps

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def retry(max_retries=3, delay=1, backoff=2):
    """重试装饰器"""
    def decorator(func):
        @wraps(func)
        def wrapper(*args, **kwargs):
            retries = 0
            while retries < max_retries:
                try:
                    return func(*args, **kwargs)
                except Exception as e:
                    retries += 1
                    wait_time = delay * (backoff ** (retries - 1))
                    logger.warning(f"尝试 {retries}/{max_retries} 失败: {str(e)}. {wait_time}秒后重试...")
                    time.sleep(wait_time)
            # 所有重试都失败后抛出异常
            raise Exception(f"操作失败，已重试 {max_retries} 次")
        return wrapper
    return decorator

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

@retry(max_retries=3, delay=1, backoff=2)
def get_market_kline_data(stock_code=None, secid=None, days=250):
    """
    获取大盘指数K线数据

    参数:
        stock_code (str): 股票代码（用于判断所属大盘）
        secid (str): 直接指定市场代码.指数代码
        days (int): 获取的天数（默认250天）

    返回:
        dict: 包含市场名称和K线数据的字典
    """
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
        "fields1": "f1,f2,f3,f4,f5",
        "fields2": "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61",
        "fqt": "0",
        "end": "29991010",  # 固定结束日期
        "ut": "fa5fd1943c7b386f172d6893dbfba10b",
        "cb": f"jQuery1123049046756194812247_{timestamp}",  # 动态回调函数名
        "klt": "101",  # 日线数据
        "secid": secid,
        "fqt": "1",
        "lmt": days,  # 指定获取的数据天数
        "_": timestamp
    }

    url = "https://push2his.eastmoney.com/api/qt/stock/kline/get"

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": "https://quote.eastmoney.com/",
        "Accept": "*/*"
    }

    # 发送请求
    response = requests.get(url, headers=headers, params=params)
    response.raise_for_status()

    # 解析JSONP响应
    jsonp_data = response.text
    json_match = re.search(r'jQuery\d+_\d+\((.*)\);', jsonp_data)
    if not json_match:
        raise ValueError("无法解析JSONP响应")

    json_str = json_match.group(1)
    data = json.loads(json_str)

    # 检查数据有效性
    if data.get("rc") != 0 or "data" not in data or "klines" not in data["data"]:
        raise ValueError(f"API返回无效数据: {data.get('rt', '未知错误')}")

    klines = data["data"]["klines"]
    json_data = []

    for kline in klines:
        parts = kline.split(",")
        if len(parts) < 11:
            continue

        try:
            # 解析数据点
            volume = int(parts[5])
            amount = float(parts[6])

            # 构建JSON对象
            item = {
                "d": parts[0],  # 日期
                "o": float(parts[1]),  # 开盘价
                "c": float(parts[2]),  # 收盘价
                "h": float(parts[3]),  # 最高价
                "l": float(parts[4]),  # 最低价
                "v": f"{volume / 10000:.2f}",  # 成交量（万手）
                "tu": f"{amount / 100000000:.2f}",  # 成交额（亿元）
#                 "change_percent": parts[8] + "%",  # 涨跌幅
#                 "turnover_rate": parts[10] + "%"   # 换手率
            }
            json_data.append(item)
        except (ValueError, IndexError) as e:
            logger.error(f"解析K线数据点失败: {kline}, 错误: {str(e)}")
            continue

    # 返回包含市场名称和数据的字典
    return {
        "market": market_name,
        "data": json_data
    }

# 主函数（确保有全局错误处理）
def main():
    """主函数"""
    try:
        stock_code = sys.argv[1] if len(sys.argv) > 1 else "002215"
        result = get_market_kline_data(stock_code=stock_code)
        # 打印结果（JSON格式）
        print(json.dumps(result['data'], indent=2, ensure_ascii=False))
    except Exception as e:
        error_result = {
            "error": "获取大盘K线数据失败",
            "message": str(e)
        }
        print(json.dumps(error_result, ensure_ascii=False))

if __name__ == "__main__":
    main()