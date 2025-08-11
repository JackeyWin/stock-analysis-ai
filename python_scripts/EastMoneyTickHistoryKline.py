from curl_cffi import requests
import datetime
import json
import re
import sys
import time
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

@retry(max_retries=3, delay=1, backoff=2)
def get_stock_k_data(stock_code):
    """
    获取股票近250天的K线数据
    :param stock_code: 股票代码（例如：600986）
    :return: 包含近250天K线数据的列表
    """
    # 确定市场类型（沪市1.，深市0.）
    market = "1." if stock_code.startswith(("6", "9")) else "0."
    secid = market + stock_code

    # 获取当前日期作为结束日期
    end_date = datetime.datetime.now().strftime("%Y%m%d")

    # 构建请求URL
    url = "https://push2his.eastmoney.com/api/qt/stock/kline/get"
    params = {
        "secid": secid,
        "ut": "fa5fd1943c7b386f172d6893dbfba10b",
        "fields1": "f1,f2,f3,f4,f5,f6",
        "fields2": "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61",
        "klt": "101",  # 日K线
        "fqt": "1",  # 复权类型：1前复权
        "end": end_date,
        "lmt": "250",  # 获取250条数据
        "cb": f"quote_jp1_{int(time.time() * 1000)}"  # 动态回调函数名
    }

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": "https://quote.eastmoney.com/"
    }

    try:
        # 发送请求
        response = requests.get(url, headers=headers, params=params)
        response.raise_for_status()

        # 处理JSONP响应
        jsonp_text = response.text
        json_match = re.search(r'quote_jp1_\d+\((.*)\);', jsonp_text)
        if not json_match:
            raise ValueError("无法解析JSONP响应")

        json_text = json_match.group(1)
        data = json.loads(json_text)

        # 检查数据有效性
        if data.get("rc") != 0 or "data" not in data or "klines" not in data["data"]:
            raise ValueError(f"API返回无效数据: {data.get('rt', '未知错误')}")

        klines = data["data"]["klines"]
        json_data = []

        for kline in klines:
            parts = kline.split(",")
            if len(parts) < 11:
                logger.warning(f"跳过无效K线数据: {kline}")
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
#                     "change_percent": parts[8] + "%",  # 涨跌幅
#                     "turnover_rate": parts[10] + "%"   # 换手率
                }
                json_data.append(item)
            except (ValueError, IndexError) as e:
                logger.error(f"解析K线数据点失败: {kline}, 错误: {str(e)}")
                continue

        return json_data

    except Exception as e:
        logger.error(f"获取K线数据失败: {str(e)}")
        raise


@retry(max_retries=3, delay=1, backoff=2)
def get_stock_k_data_60m(stock_code, limit: int = 120):
    """
    获取股票60分钟K线数据（前复权）
    :param stock_code: 股票代码（例如：600986）
    :param limit: 返回条数，默认120
    :return: 60分钟K线数据列表
    """
    # 市场标识
    market = "1." if stock_code.startswith(("6", "9")) else "0."
    secid = market + stock_code

    # 使用远端较大的结束日期，确保尽可能拿到最新数据
    end_date = "20500101"

    url = "https://push2his.eastmoney.com/api/qt/stock/kline/get"
    params = {
        "secid": secid,
        "ut": "fa5fd1943c7b386f172d6893dbfba10b",
        "fields1": "f1,f2,f3,f4,f5,f6",
        "fields2": "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61",
        "klt": "60",            # 60分钟K线
        "fqt": "1",             # 前复权
        "end": end_date,
        "lmt": str(limit),
        "cb": f"quote_jp1_{int(time.time() * 1000)}"
    }

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": "https://quote.eastmoney.com/"
    }

    try:
        resp = requests.get(url, headers=headers, params=params)
        resp.raise_for_status()

        jsonp_text = resp.text
        json_match = re.search(r'quote_jp1_\d+\((.*)\);', jsonp_text)
        if not json_match:
            raise ValueError("无法解析JSONP响应")

        data = json.loads(json_match.group(1))

        if data.get("rc") != 0 or "data" not in data or "klines" not in data["data"]:
            raise ValueError(f"API返回无效数据: {data.get('rt', '未知错误')}")

        klines = data["data"]["klines"]
        json_data = []

        for kline in klines:
            parts = kline.split(",")
            if len(parts) < 11:
                logger.warning(f"跳过无效K线数据: {kline}")
                continue

            try:
                volume = int(parts[5])
                amount = float(parts[6])

                item = {
                    "d": parts[0],                # 时间: YYYY-MM-DD HH:MM
                    "o": float(parts[1]),        # 开
                    "c": float(parts[2]),        # 收
                    "h": float(parts[3]),        # 高
                    "l": float(parts[4]),        # 低
                    "v": f"{volume / 10000:.2f}",   # 成交量（万手）
                    "tu": f"{amount / 100000000:.2f}"  # 成交额（亿元）
                }
                json_data.append(item)
            except (ValueError, IndexError) as e:
                logger.error(f"解析K线数据点失败: {kline}, 错误: {str(e)}")
                continue

        return json_data

    except Exception as e:
        logger.error(f"获取60分钟K线数据失败: {str(e)}")
        raise


@retry(max_retries=3, delay=1, backoff=2)
def get_stock_k_data_5m(stock_code, limit: int = 460):
    """
    获取股票5分钟K线数据（前复权）
    :param stock_code: 股票代码（例如：600986）
    :param limit: 返回条数，默认460（约近一个月交易日内5分钟线）
    :return: 5分钟K线数据列表
    """
    market = "1." if stock_code.startswith(("6", "9")) else "0."
    secid = market + stock_code

    end_date = "20500101"

    url = "https://push2his.eastmoney.com/api/qt/stock/kline/get"
    params = {
        "secid": secid,
        "ut": "fa5fd1943c7b386f172d6893dbfba10b",
        "fields1": "f1,f2,f3,f4,f5,f6",
        "fields2": "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61",
        "klt": "5",              # 5分钟K线
        "fqt": "1",             # 前复权
        "beg": "0",
        "end": end_date,
        "smplmt": str(limit),
        "lmt": "1000000",
        "cb": f"quote_jp1_{int(time.time() * 1000)}"
    }

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": "https://quote.eastmoney.com/"
    }

    try:
        resp = requests.get(url, headers=headers, params=params)
        resp.raise_for_status()

        jsonp_text = resp.text
        json_match = re.search(r'quote_jp1_\\d+\\((.*)\\);', jsonp_text)
        if json_match:
            json_text = json_match.group(1)
        else:
            start = jsonp_text.find('(')
            end = jsonp_text.rfind(')')
            if start == -1 or end == -1 or end <= start:
                raise ValueError("无法解析JSONP响应")
            json_text = jsonp_text[start+1:end]

        data = json.loads(json_text)

        if data.get("rc") != 0 or "data" not in data or "klines" not in data["data"]:
            raise ValueError(f"API返回无效数据: {data.get('rt', '未知错误')}")

        klines = data["data"]["klines"]
        json_data = []

        for kline in klines:
            parts = kline.split(",")
            if len(parts) < 11:
                logger.warning(f"跳过无效K线数据: {kline}")
                continue

            try:
                volume = int(parts[5])
                amount = float(parts[6])

                item = {
                    "d": parts[0],                # 时间: YYYY-MM-DD HH:MM
                    "o": float(parts[1]),        # 开
                    "c": float(parts[2]),        # 收
                    "h": float(parts[3]),        # 高
                    "l": float(parts[4]),        # 低
                    "v": f"{volume / 10000:.2f}",   # 成交量（万手）
                    "tu": f"{amount / 100000000:.2f}"  # 成交额（亿元）
                }
                json_data.append(item)
            except (ValueError, IndexError) as e:
                logger.error(f"解析K线数据点失败: {kline}, 错误: {str(e)}")
                continue

        return json_data

    except Exception as e:
        logger.error(f"获取5分钟K线数据失败: {str(e)}")
        raise

# 主函数（确保有全局错误处理）
def main():
    """主函数"""
    try:
        # 参数：stock_code [period]
        # period 可选："day"(默认) / "60m" / "5m"
        stock_code = sys.argv[1] if len(sys.argv) > 1 else "300402"
        period = sys.argv[2] if len(sys.argv) > 2 else "day"

        if period.lower() in ("60m", "60"):
            k_data = get_stock_k_data_60m(stock_code)
        elif period.lower() in ("5m", "5"):
            k_data = get_stock_k_data_5m(stock_code)
        else:
            k_data = get_stock_k_data(stock_code)

        # 打印结果（JSON格式）
        print(json.dumps(k_data, indent=2, ensure_ascii=False))
    except Exception as e:
        error_result = {
            "error": "获取K线数据失败",
            "message": str(e),
            "stock_code": stock_code
        }
        print(json.dumps(error_result, ensure_ascii=False))

if __name__ == "__main__":
    main()