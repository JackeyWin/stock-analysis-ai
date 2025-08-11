from curl_cffi import requests
import json
import time
import sys
import logging

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def get_recent_fund_flow(stock_code, max_retries=3):
    """获取股票当日资金流向数据"""
    
    for attempt in range(max_retries):
        try:
            # 确定股票市场前缀
            prefix = '1' if stock_code.startswith(('600', '601', '603', '605', '688')) else '0'
            secid = f"{prefix}.{stock_code}"

            # 构建请求URL
            url = "https://push2.eastmoney.com/api/qt/stock/fflow/kline/get"
            params = {
                "lmt": 0,  # 获取所有数据
                "klt": 1,  # 1分钟线
                "fields1": "f1,f2,f3,f7",
                "fields2": "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61,f62,f63,f64,f65",
                "ut": "b2884a393a59ad64002292a3e90d46a5",  # 固定token
                "secid": secid,
                "_": int(time.time() * 1000)  # 时间戳
            }

            # 发送请求
            headers = {
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36",
                "Referer": "https://data.eastmoney.com/",
                "Accept": "application/json, text/javascript, */*; q=0.01",
                "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
                "Accept-Encoding": "gzip, deflate, br",
                "Connection": "keep-alive",
                "Cache-Control": "no-cache"
            }

            # 使用curl_cffi发送请求，它会自动处理代理问题
            response = requests.get(
                url, 
                params=params, 
                headers=headers,
                timeout=15
            )
            response.raise_for_status()

            # 解析JSON数据
            data_str = response.text

            # 处理JSONP格式（如果有回调函数）
            if data_str.startswith('jQuery'):
                start_index = data_str.find('(') + 1
                end_index = data_str.rfind(')')
                json_data = json.loads(data_str[start_index:end_index])
            else:
                json_data = json.loads(data_str)

            # 检查并提取数据
            if "data" in json_data and "klines" in json_data["data"]:
                klines = json_data["data"]["klines"]
                result = []

                for line in klines:
                    parts = line.split(',')
                    if len(parts) < 6:  # 确保有足够的数据字段
                        continue

                    try:
                        # 创建符合要求的字典对象
                        item = {
                            "t": parts[0],  # 时间
                            "zl": float(parts[1]),  # 主力净流入
                            "xd": float(parts[2]),  # 小单净流入
                            "zd": float(parts[3]),  # 中单净流入
                            "dd": float(parts[4]),  # 大单净流入
                            "cdd": float(parts[5])  # 超大单净流入
                        }
                        result.append(item)
                    except (ValueError, IndexError) as e:
                        logger.warning(f"解析数据行失败: {line}, 错误: {e}")
                        continue

                logger.info(f"成功获取股票 {stock_code} 的资金流向数据，共 {len(result)} 条记录")
                return result
            else:
                logger.warning(f"股票 {stock_code} 未找到资金流入数据")
                return {"error": "未找到资金流入数据"}

        except Exception as e:
            logger.error(f"尝试 {attempt + 1}/{max_retries} 获取股票 {stock_code} 资金流向失败: {str(e)}")
            if attempt < max_retries - 1:
                wait_time = (attempt + 1) * 2  # 递增等待时间
                logger.info(f"等待 {wait_time} 秒后重试...")
                time.sleep(wait_time)
            else:
                logger.error(f"获取股票 {stock_code} 资金流向数据失败，已重试 {max_retries} 次")
                return {"error": f"获取资金流向数据失败: {str(e)}"}

    return {"error": "获取资金流向数据失败"}


# 使用示例
if __name__ == "__main__":
    try:
        stock_code = sys.argv[1] if len(sys.argv) > 1 else "600986"
        fund_flow_data = get_recent_fund_flow(stock_code)

        if isinstance(fund_flow_data, list) and fund_flow_data:
            # 直接输出JSON格式数据
            print(json.dumps(fund_flow_data, ensure_ascii=False))
        elif isinstance(fund_flow_data, dict) and "error" in fund_flow_data:
            print(json.dumps({"error": fund_flow_data['error']}, ensure_ascii=False))
        else:
            print(json.dumps({"error": "未获取到有效的资金流入数据"}, ensure_ascii=False))
    except Exception as e:
        error_result = {
            "error": "获取资金流向数据失败",
            "message": str(e)
        }
        print(json.dumps(error_result, ensure_ascii=False))