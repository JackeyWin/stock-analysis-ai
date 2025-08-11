import requests
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
def get_eastmoney_fund_flow(market_code):
    """获取东方财富网资金流向数据，返回可直接解析为List<Map>的JSON数组"""
    result = {
        "股票代码": market_code,
        "涨幅统计": {},
        "资金数据": {
            "今日": [],
            "5日": [],
            "10日": []
        },
        "主力排行": {}
    }

    success = False

    for market_prefix in ["0.", "1."]:
        try:
            timestamp = int(time.time() * 1000)
            base_url = "https://push2.eastmoney.com/api/qt/stock/get"
            params = {
                "secid": f"{market_prefix}{market_code}",  # 先尝试0.(深市)，后尝试1.(沪市)
                "fields": "f469,f137,f193,f140,f194,f143,f195,f146,f196,f149,f197,f470,f434,f454,f435,f455,f436,f456,f437,f457,f438,f458,f471,f459,f460,f461,f462,f463,f464,f465,f466,f467,f468,f170,f119,f291",
                "ut": "b2884a393a59ad64002292a3e90d46a5",
                "cb": f"jQuery1123{timestamp}_{timestamp}",
                "_": timestamp
            }
            headers = {
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
                "Referer": "https://quote.eastmoney.com/",
                "Accept": "application/json, text/javascript, */*; q=0.01",
                "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
                "Accept-Encoding": "gzip, deflate, br",
                "Connection": "keep-alive",
                "Cache-Control": "no-cache"
            }

            # 创建session并禁用代理
            session = requests.Session()
            session.trust_env = False  # 禁用环境变量中的代理设置
            
            # 发送请求
            response = session.get(
                base_url, 
                headers=headers, 
                params=params, 
                timeout=10,
                proxies={}  # 明确设置空代理
            )
            response.raise_for_status()

            # 解析JSONP响应
            raw_text = response.text
            if "(" in raw_text and ")" in raw_text:
                json_str = raw_text.split("(", 1)[1].rsplit(")", 1)[0]
                data = json.loads(json_str)
            else:
                # 如果不是JSONP格式，直接解析JSON
                data = json.loads(raw_text)

            # 提取基础信息
            if data.get("data") and data["data"].get('f170') is not None:
                # 涨幅统计
                result["涨幅统计"] = {
                    "今日涨幅（%）": data['data']['f170'] / 100 if 'f170' in data['data'] else None,
                    "5日涨幅（%）": data['data']['f119'] / 100 if 'f119' in data['data'] else None,
                    "10日涨幅（%）": data['data']['f291'] / 100 if 'f291' in data['data'] else None
                }

                # 主力排行
                result["主力排行"] = {
                    "今日": data['data'].get('f469', 0),
                    "5日": data['data'].get('f470', 0),
                    "10日": data['data'].get('f471', 0)
                }

                # 资金数据 - 今日
                for category, prefix in [("主力", ""), ("超大单", ""), ("大单", ""), ("中单", ""), ("小单", "")]:
                    result["资金数据"]["今日"].append({
                        "资金类型": category,
                        "净流入额（万元）": data['data'].get(f'f{prefix}137', 0) / 10000 if prefix == "" else data['data'].get(f'f{prefix}140', 0) / 10000,
                        "净占比（%）": data['data'].get(f'f{prefix}193', 0) / 100 if prefix == "" else data['data'].get(f'f{prefix}194', 0) / 100
                    })

                # 资金数据 - 5日
                prefixes = {"主力": "434", "超大单": "435", "大单": "436", "中单": "437", "小单": "438"}
                for category, prefix in prefixes.items():
                    result["资金数据"]["5日"].append({
                        "资金类型": category,
                        "净流入额（万元）": data['data'].get(f'f{prefix}', 0) / 10000,
                        "净占比（%）": data['data'].get(f'f{str(int(prefix)+20)}', 0) / 100
                    })

                # 资金数据 - 10日
                prefixes_10d = {"主力": "459", "超大单": "461", "大单": "463", "中单": "465", "小单": "467"}
                for category, prefix in prefixes_10d.items():
                    result["资金数据"]["10日"].append({
                        "资金类型": category,
                        "净流入额（万元）": data['data'].get(f'f{prefix}', 0) / 10000,
                        "净占比（%）": data['data'].get(f'f{str(int(prefix)+1)}', 0) / 100
                    })

                # 成功获取数据
                success = True
                break

        except Exception as e:
            logger.error(f"尝试{market_prefix}{market_code}失败: {str(e)}")
            # 继续尝试下一个市场前缀

    if success:
        return json.dumps([result], ensure_ascii=False)
    else:
        # 所有尝试都失败时返回错误信息
        return json.dumps([{
            "error": f"无法获取股票{market_code}的资金流向数据",
            "股票代码": market_code
        }])

if __name__ == "__main__":
    try:
        market_code = sys.argv[1] if len(sys.argv) > 1 else "600986"
        fund_data_json = get_eastmoney_fund_flow(market_code)
        print(fund_data_json)
    except Exception as e:
        error_result = {
            "error": "获取资金流向数据失败",
            "message": str(e)
        }
        print(json.dumps(error_result, ensure_ascii=False))