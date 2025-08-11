from curl_cffi import requests
import json
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
def get_margin_data(stock_code):
    """获取融资融券数据，返回可直接解析为List<Map>的JSON数组"""
    # 构建请求URL和参数
    url = "https://datacenter-web.eastmoney.com/api/data/v1/get"
    params = {
        "reportName": "RPTA_WEB_RZRQ_GGMX",
        "columns": "ALL",
        "source": "WEB",
        "sortColumns": "DATE",
        "sortTypes": "-1",
        "pageNumber": "1",
        "pageSize": "10",
        "filter": f"(scode={stock_code})",
    }

    # 请求头信息
    headers = {
            "Accept": "*/*",
            "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6",
            "Connection": "keep-alive",
            "Cookie": "qgqp_b_id=f8b7c8e86e3a89d4a9e477dbfffcc277; st_si=59446910390068; fullscreengg=1; fullscreengg2=1; emshistory=%5B%22%E6%B5%99%E6%96%87%E4%BA%92%E8%81%94%22%2C%22601127%22%2C%22002952%22%5D; HAList=ty-1-600986-%u6D59%u6587%u4E92%u8054%2Cty-1-000001-%u4E0A%u8BC1%u6307%u6570; st_asi=delete; JSESSIONID=D0249A34C79A9C796FBA2699C720F047; st_pvi=06757786959103; st_sp=2025-07-21%2016%3A43%3A21; st_inirUrl=https%3A%2F%2Fcn.bing.com%2F; st_sn=125; st_psi=20250724164905164-113200301201-9046401595; JSESSIONID=1FEA80B475035D0908868B3EE072EEFA",
            "Referer": f"https://data.eastmoney.com/rzrq/stock/{stock_code}.html",
            "Sec-Fetch-Dest": "script",
            "Sec-Fetch-Mode": "no-cors",
            "Sec-Fetch-Site": "same-site",
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0",
            "sec-ch-ua": '"Microsoft Edge";v="119", "Chromium";v="119", "Not?A_Brand";v="24"',
            "sec-ch-ua-mobile": "?0",
            "sec-ch-ua-platform": '"Windows"'
        }

    try:
        response = requests.get(url, headers=headers, params=params)
        response.raise_for_status()

        # 尝试解析JSON
        try:
            data = response.json()
        except json.JSONDecodeError as e:
            # 尝试从错误响应中提取信息
            error_text = response.text[:500]  # 只取前500个字符
            raise ValueError(f"JSON解析失败: {str(e)}. 响应内容: {error_text}")

        # 检查响应结构
        if not isinstance(data, dict):
            raise ValueError(f"响应不是字典格式: {type(data)}")

        if "result" not in data:
            raise ValueError("响应缺少'result'字段")

        if "data" not in data["result"]:
            raise ValueError("响应缺少'data'字段")

        raw_data = data["result"]["data"]
        result = []

        for item in raw_data:
            # 单位转换函数
            def convert_amount(value, unit=1):
                # 确保值是数值类型
                if value is None:
                    return 0
                try:
                    value = float(value)
                except (TypeError, ValueError):
                    return 0

                if unit == 1:  # 转换为亿元
                    return round(value / 100000000, 4)
                elif unit == 2:  # 转换为万元
                    return round(value / 10000, 2)
                else:
                    return value

            # 确保所有字段都存在
            date_str = item.get("DATE", "")
            if " " in date_str:
                date_str = date_str.split(" ")[0]

            # 构建标准化数据记录
            record = {
                "日期": date_str,
                "融资余额（亿元）": convert_amount(item.get("RZYE")),  # 单位：亿元
                "融券余量（股）": item.get("RQYL", 0),  # 单位：股
                "融资融券余额（亿元）": convert_amount(item.get("RZRQYE")),  # 单位：亿元
                "融券余额（万元）": convert_amount(item.get("RQYE"), 2),  # 单位：万元
                "融券卖出量（股）": item.get("RQMCL", 0),  # 单位：股
                "融资买入额（万元）": convert_amount(item.get("RZMRE"), 2),  # 单位：万元
                "融资偿还额（万元）": convert_amount(item.get("RZCHE"), 2),  # 单位：万元
                "融资净买入额（万元）": convert_amount(item.get("RZJME"), 2),  # 单位：万元
                "融券偿还量（股）": item.get("RQCHL", 0),  # 单位：股
                "融券净卖出（股）": item.get("RQJMG", 0),  # 单位：股
                "收盘价（元）": item.get("SPJ", 0.0),  # 单位：元
                "涨跌幅（%）": item.get("ZDF", 0.0)  # 单位：%
            }
            result.append(record)

        # 返回可直接解析为List<Map>的JSON数组
        return json.dumps(result, ensure_ascii=False)

    except Exception as e:
        raise Exception(f"获取融资融券数据失败: {str(e)}")

# 使用示例
if __name__ == "__main__":
    try:
        stock_code = sys.argv[1] if len(sys.argv) > 1 else "300402"
        json_data = get_margin_data(stock_code)
        print(json_data)
    except Exception as e:
        error_result = {
            "error": "获取融资融券数据失败",
            "message": str(e),
            "stock_code": stock_code
        }
        print(json.dumps(error_result, ensure_ascii=False))