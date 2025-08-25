#!/usr/bin/env python3
"""
分时走势与实时资金流向数据精炼分析脚本
整合EastMoneyStockTrendsToday.py和EastMoneyFundFlowToday.py的数据
输出关键转折点、多空攻击等精炼信息
"""

import sys
import json
import subprocess
from datetime import datetime, timedelta
from typing import List, Dict, Any, Optional, Tuple
import statistics


class IntradayAnalyzer:
    """分时数据分析器"""
    
    def __init__(self, stock_code: str):
        self.stock_code = stock_code
        self.trends_data = []
        self.fund_flow_data = []
        self.current_fund_flow = {}
        self.analysis_result = {}
        
    def load_data(self) -> bool:
        """加载分时走势、资金流向数据和当前资金流向数据"""
        try:
            # 获取分时走势数据
            # 获取脚本所在目录的绝对路径
            import os
            script_dir = os.path.dirname(os.path.abspath(__file__))
            trends_script_path = os.path.join(script_dir, 'EastMoneyStockTrendsToday.py')
            
            trends_result = subprocess.run([
                sys.executable, 
                trends_script_path, 
                self.stock_code
            ], capture_output=True, text=True, encoding='utf-8', errors='ignore')
            
            if trends_result.returncode == 0:
                try:
                    # 清理输出，只保留JSON部分
                    stdout_clean = trends_result.stdout.strip()
                    if stdout_clean.startswith('[') and stdout_clean.endswith(']'):
                        self.trends_data = json.loads(stdout_clean)
                        print(f"成功加载分时数据: {len(self.trends_data)}条", file=sys.stderr)
                    else:
                        # 尝试从输出中提取JSON
                        lines = stdout_clean.split('\n')
                        for line in lines:
                            line = line.strip()
                            if line.startswith('[') and line.endswith(']'):
                                self.trends_data = json.loads(line)
                                print(f"成功加载分时数据: {len(self.trends_data)}条", file=sys.stderr)
                                break
                        else:
                            print(f"分时数据格式错误: {stdout_clean[:200]}...", file=sys.stderr)
                            return False
                except json.JSONDecodeError as e:
                    print(f"分时数据JSON解析失败: {str(e)}", file=sys.stderr)
                    print(f"原始输出: {trends_result.stdout[:200]}...", file=sys.stderr)
                    return False
            else:
                print(f"获取分时数据失败: {trends_result.stderr}", file=sys.stderr)
                return False
                
            # 获取资金流向数据
            fund_script_path = os.path.join(script_dir, 'EastMoneyFundFlowToday.py')
            
            fund_result = subprocess.run([
                sys.executable, 
                fund_script_path, 
                self.stock_code
            ], capture_output=True, text=True, encoding='utf-8', errors='ignore')
            
            if fund_result.returncode == 0:
                try:
                    # 清理输出，只保留JSON部分
                    stdout_clean = fund_result.stdout.strip()
                    if stdout_clean.startswith('[') and stdout_clean.endswith(']'):
                        fund_data = json.loads(stdout_clean)
                    else:
                        # 尝试从输出中提取JSON
                        lines = stdout_clean.split('\n')
                        json_content = ""
                        in_json = False
                        for line in lines:
                            line = line.strip()
                            if line.startswith('['):
                                in_json = True
                                json_content = line
                            elif in_json:
                                json_content += line
                                if line.endswith(']'):
                                    break
                        
                        if json_content:
                            fund_data = json.loads(json_content)
                        else:
                            print(f"资金流向数据格式错误: {stdout_clean[:200]}...", file=sys.stderr)
                            # 不返回False，而是设置空数据继续执行
                            self.fund_flow_data = []
                            print(f"资金流向数据获取失败，使用空数据继续分析", file=sys.stderr)
                    
                    if isinstance(fund_data, list):
                        self.fund_flow_data = fund_data
                        print(f"成功加载资金流向数据: {len(self.fund_flow_data)}条", file=sys.stderr)
                    elif isinstance(fund_data, dict) and "error" in fund_data:
                        print(f"资金流向数据获取失败: {fund_data['error']}", file=sys.stderr)
                        # 设置空数据继续执行
                        self.fund_flow_data = []
                        print(f"资金流向数据获取失败，使用空数据继续分析", file=sys.stderr)
                    else:
                        print(f"资金流向数据格式错误: {fund_data}", file=sys.stderr)
                        # 设置空数据继续执行
                        self.fund_flow_data = []
                        print(f"资金流向数据格式错误，使用空数据继续分析", file=sys.stderr)
                except json.JSONDecodeError as e:
                    print(f"资金流向数据JSON解析失败: {str(e)}", file=sys.stderr)
                    print(f"原始输出: {fund_result.stdout[:200]}...", file=sys.stderr)
                    # 设置空数据继续执行
                    self.fund_flow_data = []
                    print(f"资金流向数据JSON解析失败，使用空数据继续分析", file=sys.stderr)
            else:
                print(f"获取资金流向数据失败: {fund_result.stderr}", file=sys.stderr)
                # 设置空数据继续执行
                self.fund_flow_data = []
                print(f"资金流向数据获取失败，使用空数据继续分析", file=sys.stderr)
            
            # 获取当前资金流向数据（新增）
            current_fund_script_path = os.path.join(script_dir, 'EastMoneyFundFlowCurrent.py')
            
            current_fund_result = subprocess.run([
                sys.executable, 
                current_fund_script_path, 
                self.stock_code
            ], capture_output=True, text=True, encoding='utf-8', errors='ignore')
            
            if current_fund_result.returncode == 0:
                try:
                    # 清理输出，只保留JSON部分
                    stdout_clean = current_fund_result.stdout.strip()
                    print(f"当前资金流向原始输出: {stdout_clean[:200]}...", file=sys.stderr)
                    
                    # 尝试从输出中提取JSON
                    lines = stdout_clean.split('\n')
                    json_found = False
                    
                    # 首先尝试直接解析整个输出
                    try:
                        current_fund_data = json.loads(stdout_clean)
                        if current_fund_data.get('success') and current_fund_data.get('data'):
                            self.current_fund_flow = current_fund_data['data']['fund_flow']
                            print(f"成功加载当前资金流向数据", file=sys.stderr)
                            json_found = True
                        else:
                            print(f"当前资金流向数据获取失败: {current_fund_data.get('error', '未知错误')}", file=sys.stderr)
                    except json.JSONDecodeError:
                        # 如果整体解析失败，尝试逐行解析
                        for line in lines:
                            line = line.strip()
                            if line.startswith('{') and line.endswith('}'):
                                try:
                                    current_fund_data = json.loads(line)
                                    if current_fund_data.get('success') and current_fund_data.get('data'):
                                        self.current_fund_flow = current_fund_data['data']['fund_flow']
                                        print(f"成功加载当前资金流向数据", file=sys.stderr)
                                        json_found = True
                                        break
                                    else:
                                        print(f"当前资金流向数据获取失败: {current_fund_data.get('error', '未知错误')}", file=sys.stderr)
                                except json.JSONDecodeError as e:
                                    print(f"JSON解析失败: {str(e)}", file=sys.stderr)
                                    continue
                    
                    if not json_found:
                        print(f"未找到有效的JSON数据", file=sys.stderr)
                        self.current_fund_flow = {}
                        
                except Exception as e:
                    print(f"当前资金流向数据处理异常: {str(e)}", file=sys.stderr)
                    print(f"原始输出: {current_fund_result.stdout[:200]}...", file=sys.stderr)
                    self.current_fund_flow = {}
            else:
                print(f"获取当前资金流向数据失败: {current_fund_result.stderr}", file=sys.stderr)
                self.current_fund_flow = {}
            
            return True
            
        except Exception as e:
            print(f"数据加载异常: {str(e)}", file=sys.stderr)
            return False

    def calculate_key_metrics(self) -> Dict[str, Any]:
        """计算关键指标"""
        if not self.trends_data:
            return {}
        
        # 价格相关指标
        prices = [item['p'] for item in self.trends_data]
        volumes = [item['v'] for item in self.trends_data]
        
        if not prices:
            return {}
        
        current_price = prices[-1]
        open_price = prices[0]
        high_price = max(prices)
        low_price = min(prices)
        
        # 涨跌幅
        change_percent = ((current_price - open_price) / open_price) * 100
        
        # 成交量分析
        total_volume = sum(volumes)
        avg_volume = total_volume / len(volumes)
        volume_ratio = total_volume / (avg_volume * len(volumes)) if avg_volume > 0 else 1.0
        
        # 资金流向指标（如果有数据的话）
        main_net_flow = 0
        total_net_flow = 0
        if self.fund_flow_data and len(self.fund_flow_data) > 0:
            latest_item = self.fund_flow_data[-1]
            main_net_flow = latest_item.get('zl', 0)  # 主力净流入
            total_net_flow = latest_item.get('zl', 0)  # 总净流入
        
        return {
            "current_price": f"{current_price:.2f}",
            "open_price": f"{open_price:.2f}",
            "high_price": f"{high_price:.2f}",
            "low_price": f"{low_price:.2f}",
            "change_percent": f"{change_percent:+.2f}%",
            "total_volume": f"{total_volume / 10000:.0f}万手",
            "volume_ratio": f"{volume_ratio:.2f}",
            "main_net_flow": f"{main_net_flow / 10000:.0f}万元",
            "total_net_flow": f"{total_net_flow / 10000:.0f}万元"
        }

    def identify_turning_points(self) -> List[Dict[str, Any]]:
        """识别关键转折点"""
        if not self.trends_data or not self.fund_flow_data:
            return []
        
        turning_points = []
        prices = [item['p'] for item in self.trends_data]
        volumes = [item['v'] for item in self.trends_data]
        times = [item['t'] for item in self.trends_data]
        
        # 寻找价格转折点
        for i in range(2, len(prices) - 2):
            # 局部高点
            if (prices[i] > prices[i-1] and prices[i] > prices[i-2] and 
                prices[i] > prices[i+1] and prices[i] > prices[i+2]):
                turning_points.append({
                    "time": times[i],
                    "type": "高点",
                    "price": f"{prices[i]:.2f}",
                    "volume": f"{volumes[i] / 10000:.0f}万手",
                    "description": f"价格达到局部高点{prices[i]:.2f}元"
                })
            
            # 局部低点
            elif (prices[i] < prices[i-1] and prices[i] < prices[i-2] and 
                  prices[i] < prices[i+1] and prices[i] < prices[i+2]):
                turning_points.append({
                    "time": times[i],
                    "type": "低点",
                    "price": f"{prices[i]:.2f}",
                    "volume": f"{volumes[i] / 10000:.0f}万手",
                    "description": f"价格达到局部低点{prices[i]:.2f}元"
                })
        
        # 寻找成交量异常点
        avg_volume = sum(volumes) / len(volumes)
        for i in range(len(volumes)):
            if volumes[i] > avg_volume * 2:  # 放量
                turning_points.append({
                    "time": times[i],
                    "type": "放量",
                    "price": f"{prices[i]:.2f}",
                    "volume": f"{volumes[i] / 10000:.0f}万手",
                    "description": f"成交量异常放大，达到{volumes[i] / 10000:.0f}万手"
                })
        
        # 寻找资金流向转折点
        flows = [item.get('zl', 0) for item in self.fund_flow_data]
        flow_times = [item.get('t', '') for item in self.fund_flow_data]
        
        for i in range(1, len(flows)):
            # 资金流入转为流出
            if flows[i-1] > 0 and flows[i] < 0:
                turning_points.append({
                    "time": flow_times[i],
                    "type": "资金转向",
                    "price": f"{prices[min(i, len(prices)-1)]:.2f}",
                    "volume": f"{volumes[min(i, len(volumes)-1)] / 10000:.0f}万手",
                    "description": "主力资金由流入转为流出"
                })
            # 资金流出转为流入
            elif flows[i-1] < 0 and flows[i] > 0:
                turning_points.append({
                    "time": flow_times[i],
                    "type": "资金转向",
                    "price": f"{prices[min(i, len(prices)-1)]:.2f}",
                    "volume": f"{volumes[min(i, len(volumes)-1)] / 10000:.0f}万手",
                    "description": "主力资金由流出转为流入"
                })
        
        # 按时间排序
        turning_points.sort(key=lambda x: x["time"])
        
        # 限制数量，避免过多
        return turning_points[-10:] if len(turning_points) > 10 else turning_points

    def analyze_capital_attacks(self) -> Dict[str, Any]:
        """分析多空攻击情况"""
        if not self.trends_data or not self.fund_flow_data:
            return {"bull_attacks": {"count": 0, "details": []}, 
                   "bear_attacks": {"count": 0, "details": []}}
        
        prices = [item['p'] for item in self.trends_data]
        volumes = [item['v'] for item in self.trends_data]
        times = [item['t'] for item in self.trends_data]
        
        bull_attacks = []
        bear_attacks = []
        
        # 分析多头攻击（放量上涨）
        for i in range(1, len(prices)):
            if (prices[i] > prices[i-1] and 
                volumes[i] > sum(volumes[max(0, i-5):i]) / min(5, i) * 1.5):
                bull_attacks.append({
                    "time": times[i],
                    "price": f"{prices[i]:.2f}",
                    "volume": f"{volumes[i] / 10000:.0f}万手",
                    "strength": "强" if volumes[i] > sum(volumes[max(0, i-5):i]) / min(5, i) * 2 else "中"
                })
        
        # 分析空头攻击（放量下跌）
        for i in range(1, len(prices)):
            if (prices[i] < prices[i-1] and 
                volumes[i] > sum(volumes[max(0, i-5):i]) / min(5, i) * 1.5):
                bear_attacks.append({
                    "time": times[i],
                    "price": f"{prices[i]:.2f}",
                    "volume": f"{volumes[i] / 10000:.0f}万手",
                    "strength": "强" if volumes[i] > sum(volumes[max(0, i-5):i]) / min(5, i) * 2 else "中"
                })
        
        return {
            "bull_attacks": {
                "count": len(bull_attacks),
                "details": bull_attacks[-5:]  # 最近5次多头攻击
            },
            "bear_attacks": {
                "count": len(bear_attacks),
                "details": bear_attacks[-5:]  # 最近5次空头攻击
            }
        }

    def generate_market_indicators(self) -> Dict[str, Any]:
        """生成盘面重要指标数据"""
        if not self.trends_data or not self.fund_flow_data:
            return {"status": "数据不足", "message": "无法获取分时数据"}
        
        # 计算价格指标
        recent_prices = [item['p'] for item in self.trends_data[-10:]]  # 最近10分钟价格
        all_prices = [item['p'] for item in self.trends_data]
        
        current_price = recent_prices[-1]
        high_price = max(all_prices)
        low_price = min(all_prices)
        open_price = all_prices[0] if all_prices else current_price
        
        # 价格位置和涨跌幅
        price_position = (current_price - low_price) / (high_price - low_price) if high_price != low_price else 0.5
        price_change = current_price - open_price
        price_change_pct = (price_change / open_price * 100) if open_price != 0 else 0
        
        # 计算资金流向指标
        recent_flows = [item.get('zl', 0) for item in self.fund_flow_data[-10:]]  # 最近10分钟主力流向
        avg_flow = sum(recent_flows) / len(recent_flows) if recent_flows else 0
        total_flow = sum(recent_flows)
        
        # 计算成交量指标
        recent_volumes = [item.get('v', 0) for item in self.trends_data[-10:]]
        avg_volume = sum(recent_volumes) / len(recent_volumes) if recent_volumes else 0
        volume_trend = "放量" if recent_volumes[-1] > avg_volume * 1.2 else "缩量" if recent_volumes[-1] < avg_volume * 0.8 else "正常"
        
        # 计算价格趋势
        price_trend = "上涨" if recent_prices[-1] > recent_prices[0] else "下跌" if recent_prices[-1] < recent_prices[0] else "横盘"
        
        # 获取资金攻击数据
        capital_attacks = self.analyze_capital_attacks()
        bull_attacks_count = capital_attacks.get("bull_attacks", {}).get("count", 0)
        bear_attacks_count = capital_attacks.get("bear_attacks", {}).get("count", 0)
        
        # 获取当前资金结构
        fund_structure = self.analyze_fund_structure()
        
        return {
            "price_indicators": {
                "current_price": current_price,
                "high_price": high_price,
                "low_price": low_price,
                "open_price": open_price,
                "price_change": price_change,
                "price_change_pct": price_change_pct,
                "price_position": price_position,
                "price_trend": price_trend
            },
            "volume_indicators": {
                "current_volume": recent_volumes[-1] if recent_volumes else 0,
                "avg_volume": avg_volume,
                "volume_trend": volume_trend,
                "volume_ratio": recent_volumes[-1] / avg_volume if avg_volume > 0 else 1
            },
            "fund_flow_indicators": {
                "recent_avg_flow": avg_flow,
                "total_flow": total_flow,
                "flow_trend": "流入" if avg_flow > 0 else "流出" if avg_flow < 0 else "平衡",
                "flow_intensity": abs(avg_flow) / 1000000  # 以百万为单位
            },
            "capital_attack_indicators": {
                "bull_attacks": bull_attacks_count,
                "bear_attacks": bear_attacks_count,
                "attack_balance": bull_attacks_count - bear_attacks_count
            },
            "fund_structure": fund_structure,
            "market_status": {
                "data_quality": "完整" if len(self.trends_data) > 10 else "不足",
                "update_time": datetime.now().strftime("%H:%M:%S")
            }
        }
    
    def analyze_fund_structure(self) -> Dict[str, Any]:
        """分析当前资金结构"""
        if not self.current_fund_flow or len(self.current_fund_flow) == 0:
            return {
                "status": "数据不足",
                "message": "无法获取当前资金流向数据"
            }
        
        try:
            # 提取资金流向数据
            super_large_net_ratio = self.current_fund_flow.get('super_large_net_ratio', 0)
            large_net_ratio = self.current_fund_flow.get('large_net_ratio', 0)
            medium_net_ratio = self.current_fund_flow.get('medium_net_ratio', 0)
            small_net_ratio = self.current_fund_flow.get('small_net_ratio', 0)
            main_force_net_ratio = self.current_fund_flow.get('main_force_net_ratio', 0)
            
            super_large_net_amount = self.current_fund_flow.get('super_large_net_amount', 0)
            large_net_amount = self.current_fund_flow.get('large_net_amount', 0)
            medium_net_amount = self.current_fund_flow.get('medium_net_amount', 0)
            small_net_amount = self.current_fund_flow.get('small_net_amount', 0)
            main_force_net_amount = self.current_fund_flow.get('main_force_net_amount', 0)
            total_amount = self.current_fund_flow.get('total_amount', 0)
            
            # 分析资金结构特征
            fund_structure_analysis = []
            
            # 超大单分析
            if super_large_net_ratio > 5:
                fund_structure_analysis.append("超大单大幅净流入，机构资金积极买入")
            elif super_large_net_ratio < -5:
                fund_structure_analysis.append("超大单大幅净流出，机构资金大量卖出")
            elif abs(super_large_net_ratio) <= 1:
                fund_structure_analysis.append("超大单资金流向平稳")
            
            # 大单分析
            if large_net_ratio > 3:
                fund_structure_analysis.append("大单净流入，大户资金跟进")
            elif large_net_ratio < -3:
                fund_structure_analysis.append("大单净流出，大户资金撤离")
            
            # 中单分析
            if medium_net_ratio > 2:
                fund_structure_analysis.append("中单净流入，中等资金参与")
            elif medium_net_ratio < -2:
                fund_structure_analysis.append("中单净流出，中等资金谨慎")
            
            # 小单分析
            if small_net_ratio > 1:
                fund_structure_analysis.append("小单净流入，散户资金活跃")
            elif small_net_ratio < -1:
                fund_structure_analysis.append("小单净流出，散户资金观望")
            
            # 主力资金分析
            if main_force_net_ratio > 3:
                fund_structure_analysis.append("主力资金大幅净流入，看涨信号强烈")
            elif main_force_net_ratio < -3:
                fund_structure_analysis.append("主力资金大幅净流出，看跌信号强烈")
            elif abs(main_force_net_ratio) <= 1:
                fund_structure_analysis.append("主力资金流向平稳，多空力量均衡")
            
            # 资金结构特征总结
            if len(fund_structure_analysis) > 0:
                structure_summary = "；".join(fund_structure_analysis)
            else:
                structure_summary = "资金结构无明显特征"
            
            # 计算资金流向强度
            total_net_ratio = abs(super_large_net_ratio) + abs(large_net_ratio) + abs(medium_net_ratio) + abs(small_net_ratio)
            flow_intensity = "强" if total_net_ratio > 10 else "中" if total_net_ratio > 5 else "弱"
            
            return {
                "status": "分析完成",
                "structure_summary": structure_summary,
                "flow_intensity": flow_intensity,
                "details": {
                    "super_large": {
                        "net_ratio": f"{super_large_net_ratio:+.2f}%",
                        "net_amount": f"{super_large_net_amount / 10000:.0f}万元",
                        "description": "机构资金流向"
                    },
                    "large": {
                        "net_ratio": f"{large_net_ratio:+.2f}%",
                        "net_amount": f"{large_net_amount / 10000:.0f}万元",
                        "description": "大户资金流向"
                    },
                    "medium": {
                        "net_ratio": f"{medium_net_ratio:+.2f}%",
                        "net_amount": f"{medium_net_amount / 10000:.0f}万元",
                        "description": "中等资金流向"
                    },
                    "small": {
                        "net_ratio": f"{small_net_ratio:+.2f}%",
                        "net_amount": f"{small_net_amount / 10000:.0f}万元",
                        "description": "散户资金流向"
                    },
                    "main_force": {
                        "net_ratio": f"{main_force_net_ratio:+.2f}%",
                        "net_amount": f"{main_force_net_amount / 10000:.0f}万元",
                        "description": "主力资金流向（超大单+大单）"
                    }
                },
                "total_amount": f"{total_amount / 100000000:.2f}亿元"
            }
            
        except Exception as e:
            return {
                "status": "分析失败",
                "message": f"资金结构分析异常: {str(e)}"
            }
    
    def analyze(self) -> Dict[str, Any]:
        """执行完整分析"""
        print(f"开始分析股票 {self.stock_code} 的分时数据...", file=sys.stderr)
        
        if not self.load_data():
            return {"error": "数据加载失败"}
        
        # 执行各项分析
        key_metrics = self.calculate_key_metrics()
        turning_points = self.identify_turning_points()
        capital_attacks = self.analyze_capital_attacks()
        market_indicators = self.generate_market_indicators()
        fund_structure = self.analyze_fund_structure()
        
        # 构建结果
        result = {
            "date": datetime.now().strftime("%Y-%m-%d"),
            "stock_code": self.stock_code,
            "key_metrics": key_metrics,
            "key_turning_points": turning_points,
            "capital_attacks": capital_attacks,
            "market_indicators": market_indicators,
            "fund_structure": fund_structure
        }
        
        print("分时数据分析完成", file=sys.stderr)
        return result


def main():
    """主函数"""
    if len(sys.argv) < 2:
        print(json.dumps({"error": "请提供股票代码"}, ensure_ascii=False))
        return
    
    stock_code = sys.argv[1]
    
    try:
        analyzer = IntradayAnalyzer(stock_code)
        result = analyzer.analyze()
        print(json.dumps(result, ensure_ascii=False, indent=2))
        
    except Exception as e:
        error_result = {
            "error": "分时数据分析失败",
            "message": str(e),
            "stock_code": stock_code
        }
        print(json.dumps(error_result, ensure_ascii=False))


if __name__ == "__main__":
    main()
