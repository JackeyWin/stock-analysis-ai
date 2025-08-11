from curl_cffi import requests
import datetime
import json
import re
import time
import sys
import logging

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def retry(max_retries=3, delay=1, backoff=2):
    """重试装饰器"""
    def decorator(func):
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

@retry(max_retries=3, delay=1, backoff=2)
def get_board_code(stock_code):
    """
    获取股票所属板块信息
    返回: 板块代码列表和板块名称列表
    """
    # 确定市场类型（沪市1.，深市0.）
    market = "1." if stock_code.startswith(("6", "9")) else "0."
    secid = market + stock_code
    # 构造请求参数
    params = {
        "fltt": "1",
        "invt": "2",
        "cb": f"jQuery351043724314404027953_{int(time.time() * 1000)}",
        "fields": "f14,f12,f13,f3,f152,f4,f128,f140,f141",
        "secid": secid,
        "ut": "fa5fd1943c7b386f172d6893dbfba10b",
        "pi": "0",
        "po": "1",
        "np": "1",
        "pz": "5",
        "spt": "3",
        "wbp2u": "|0|0|0|web",
        "_": int(time.time() * 1000)
    }

    url = "https://push2.eastmoney.com/api/qt/slist/get"

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": f"https://quote.eastmoney.com/{'sh' if market == '1.' else 'sz'}{stock_code}.html",
        "Accept": "*/*",
        "Accept-Encoding": "gzip, deflate, br",
        "Connection": "keep-alive"
    }

    response = requests.get(url, params=params, headers=headers)
    response.raise_for_status()

    # 提取 JSON 数据
    jsonp_text = response.text
    json_str = jsonp_text.split("(", 1)[1].rsplit(")", 1)[0]
    json_data = json.loads(json_str)

    if json_data.get("rc") != 0 or "data" not in json_data or "diff" not in json_data["data"]:
        return '', ''

    return json_data["data"]["diff"][0]["f12"], json_data["data"]["diff"][0]["f14"]

@retry(max_retries=3, delay=1, backoff=2)
def get_board_k_data(board_code, days=250):
    """
    获取板块近指定天数的K线数据
    :param board_code: 板块代码（例如：BK0486）
    :param days: 获取的天数（默认250天）
    :return: JSON数组格式的K线数据
    """
    # 构建请求URL
    url = "https://push2his.eastmoney.com/api/qt/stock/kline/get"
    params = {
        "secid": f"90.{board_code}",
        "ut": "fa5fd1943c7b386f172d6893dbfba10b",
        "fields1": "f1,f2,f3,f4,f5",
        "fields2": "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61",
        "klt": "101",  # 日K线
        "fqt": "1",  # 复权类型：1前复权
        "lmt": str(days),
        "cb": f"jQuery1123049046756194812247_{int(time.time() * 1000)}",
        "end": "29991010",
        "_": int(datetime.datetime.now().timestamp() * 1000)
    }

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": f"https://data.eastmoney.com/board/{board_code}.html",
        "Accept": "*/*",
        "Accept-Encoding": "gzip, deflate, br",
        "Connection": "keep-alive"
    }

    # 发送请求
    response = requests.get(url, headers=headers, params=params)
    response.raise_for_status()

    # 处理JSONP响应
    jsonp_text = response.text
    json_text = re.search(r'jQuery\d+_\d+\((.*)\);', jsonp_text).group(1)
    data = json.loads(json_text)

    # 提取K线数据
    if data["rc"] == 0 and "klines" in data["data"]:
        klines = data["data"]["klines"]
        json_data = []

        for kline in klines:
            parts = kline.split(",")
            if len(parts) < 11:
                continue

            # 格式化成交量（万手）
            volume = int(parts[5])
            volume_str = f"{volume / 10000:.2f}"

            # 格式化成交额（亿元）
            amount = float(parts[6])
            amount_str = f"{amount / 100000000:.2f}"

            # 构建JSON对象
            item = {
                "d": parts[0],  # 日期
                "o": float(parts[1]),  # 开盘价
                "c": float(parts[2]),  # 收盘价
                "h": float(parts[3]),  # 最高价
                "l": float(parts[4]),  # 最低价
                "v": volume_str,  # 成交量（万手）
                "tu": amount_str,  # 成交额（亿元）

                # 可选：添加原始字段
                # "volume": volume,      # 原始成交量（手）
                # "amount": amount,      # 原始成交额（元）
                # "change_percent": parts[8] + "%",  # 涨跌幅
                # "turnover_rate": parts[10] + "%"   # 换手率
            }
            json_data.append(item)

        return json_data
    else:
        return {"error": "获取数据失败", "code": data.get('rt', '未知错误')}

@retry(max_retries=3, delay=1, backoff=2)
def get_board_k_data_by_stock(stock_code, days=250):
    """
    根据股票代码获取所属板块的K线数据
    :param stock_code: 股票代码
    :param days: 获取的天数（默认210天）
    :return: 包含板块名称和K线数据的字典
    """
    # 获取板块代码
    board_code, board_name = get_board_code(stock_code)

    if not board_code:
        return {"error": f"无法获取股票{stock_code}所属的板块代码"}

    # 获取板块K线数据
    k_data = get_board_k_data(board_code, days)

    # 返回包含板块名称和数据的字典
    return {
        "board_name": board_name,
        "data": k_data
    }


# 示例使用
if __name__ == "__main__":
    # 输入股票代码
    stock_code = sys.argv[1] if len(sys.argv) > 1 else "600986"

    try:
        # 获取板块K线数据
        result = get_board_k_data_by_stock(stock_code, 250)

        # 打印结果（JSON格式）
        print(json.dumps(result['data'], indent=2, ensure_ascii=False))
    except Exception as e:
        error_result = {
            "error": "获取板块K线数据失败",
            "message": str(e)
        }
        print(json.dumps(error_result, ensure_ascii=False))