from curl_cffi import requests
import json
import time
import random
import sys


def get_stock_trends(stock_code):
    """
    获取股票分时数据
    :param stock_code: 股票代码（字符串），如 '600986' 或 '000001'
    :return: 分时数据列表，每个元素是包含时间、价格等信息的字典
    """
    # 判断股票市场（沪市：1，深市：0）
    market = "1" if stock_code.startswith(("6", "9")) else "0"
    secid = f"{market}.{stock_code}"

    # 构造请求URL（移除回调参数）
    base_url = "https://push2.eastmoney.com/api/qt/stock/trends2/get"
    params = {
        "fields1": "f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11,f12,f13",
        "fields2": "f51,f52,f53,f54,f55,f56,f57,f58",
        "ut": "fa5fd1943c7b386f172d6893dbfba10b",
        "iscr": "0",
        "ndays": "1",
        "secid": secid
    }

    # 添加浏览器头避免被拦截
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        "Referer": "https://quote.eastmoney.com/"
    }

    try:
        # 添加随机延迟避免被限流
        time.sleep(random.uniform(0.5, 1.2))

        response = requests.get(
            base_url,
            params=params,
            headers=headers,
            timeout=10
        )
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
                # 直接创建字典对象
                record = {
                    "t": parts[0],  # 时间
                    "o": float(parts[1]),  # 开盘价
                    "p": float(parts[2]),  # 当前价
                    "h": float(parts[3]),  # 最高价
                    "l": float(parts[4]),  # 最低价
                    "v": int(parts[5]),  # 成交量
                    "tu": float(parts[6]),  # 成交额
                    "avg": float(parts[7])  # 均价
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


# 使用示例
if __name__ == "__main__":
    stock_code = sys.argv[1] if len(sys.argv) > 1 else "600986"
    trends_data = get_stock_trends(stock_code)

    if trends_data:
        # 直接打印JSON格式的数据
        print(json.dumps(trends_data, indent=2, ensure_ascii=False))
    else:
        print("未获取到数据")