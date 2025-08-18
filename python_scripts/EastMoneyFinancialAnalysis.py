#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
东方财富财务分析数据提取脚本
从HTML页面提取营收、净利、毛利率、净利率、ROE、ROIC、负债率、经营现金流、PE、PEG、PB、PS、分红率等指标
"""

import json
import re
import time
from bs4 import BeautifulSoup
from typing import Dict, Any, List
import sys

# 可选渲染回退
try:
    from playwright.sync_api import sync_playwright
    HAS_PLAYWRIGHT = True
except Exception:
    HAS_PLAYWRIGHT = False


class EastMoneyFinancialAnalysis:
    def __init__(self):
        self.headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36'
        }
        self.max_retries = 3
        self.retry_delay = 2

    def get_financial_data(self, stock_code: str) -> Dict[str, Any]:
        """
        获取财务分析数据
        
        Args:
            stock_code: 股票代码
            
        Returns:
            包含财务分析数据的字典
        """
        if not HAS_PLAYWRIGHT:
            return {
                "success": False,
                "error": "Playwright未安装，请安装: pip install playwright && playwright install",
                "data": None
            }
        
        for attempt in range(self.max_retries):
            try:
                print(f"🔄 第 {attempt + 1} 次尝试获取财务数据...")
                
                # 构建市场代码
                market = "SZ" if stock_code.startswith(("00", "30")) else "SH" if stock_code.startswith(("60", "68")) else "SZ"
                code = f"{market}{stock_code}"
                url = f"https://emweb.securities.eastmoney.com/pc_hsf10/pages/index.html?type=web&code={code}&color=b#/cwfx"
                
                print(f"正在访问财务分析页面: {url}")
                
                with sync_playwright() as p:
                    browser = p.chromium.launch(headless=True)
                    page = browser.new_page(user_agent=self.headers['User-Agent'])
                    
                    # 访问页面并等待加载
                    page.goto(url, wait_until="domcontentloaded", timeout=30000)
                    
                    # 等待财务分析内容加载
                    try:
                        page.wait_for_selector(".section.cwfx", timeout=15000)
                        print("✅ 财务分析表格已加载")
                    except Exception:
                        print("⚠️ 财务分析表格加载超时")
                    
                    try:
                        page.wait_for_selector("table", timeout=15000)
                        print("✅ 表格内容已加载")
                    except Exception:
                        print("⚠️ 表格内容加载超时")
                    
                    # 额外等待页面完全加载
                    page.wait_for_timeout(3000)
                    
                    # 获取页面HTML内容
                    html_content = page.content()
                    browser.close()
                    
                    # 解析HTML内容
                    result = self.parse_html_data(html_content, stock_code)
                    
                    if result["success"] and result["data"]:
                        print(f"✅ 第 {attempt + 1} 次尝试成功获取财务数据")
                        return result
                    else:
                        print(f"⚠️ 第 {attempt + 1} 次尝试未获取到有效数据")
                        
            except Exception as e:
                print(f"❌ 第 {attempt + 1} 次尝试失败: {str(e)}")
                if attempt < self.max_retries - 1:
                    print(f"⏳ 等待 {self.retry_delay} 秒后重试...")
                    time.sleep(self.retry_delay)
                    self.retry_delay *= 2  # 指数退避
                else:
                    print("❌ 所有重试尝试都失败了")
        
        return {
            "success": False,
            "error": f"经过 {self.max_retries} 次尝试后仍无法获取财务数据",
            "data": None
        }

    def parse_html_data(self, html_content: str, stock_code: str) -> Dict[str, Any]:
        """
        解析HTML内容提取财务分析数据
        
        Args:
            html_content: HTML页面内容
            stock_code: 股票代码
            
        Returns:
            解析后的数据字典
        """
        try:
            soup = BeautifulSoup(html_content, 'html.parser')
            
            # 仅提取“综合财务指标大表”（包含：每股指标/成长能力/盈利能力/收益质量/财务风险/营运能力）
            data = {
                "综合财务指标表": {}
            }
            self.extract_core_metrics_table(soup, data)
            
            return {
                "success": True,
                "data": data
            }
            
        except Exception as e:
            return {
                "success": False,
                "error": f"HTML解析错误: {str(e)}",
                "data": None
            }

    def extract_core_metrics_table(self, soup: BeautifulSoup, data: Dict):
        """仅提取页面上的综合财务指标大表，结构为：
        - [每股指标] / [成长能力指标] / [盈利能力指标] / [收益质量指标] / [财务风险指标] / [营运能力指标]
        - 每个分段标题行后跟若干日期列，分段内每个指标均与这些日期对应
        """
        try:
            section_names = [
                "每股指标", "成长能力指标", "盈利能力指标", "收益质量指标", "财务风险指标", "营运能力指标"
            ]

            candidate_tables = []
            for table in soup.find_all('table'):
                text = table.get_text()
                hit = sum(1 for n in section_names if n in text)
                if hit >= 2:
                    candidate_tables.append((hit, table))

            if not candidate_tables:
                return

            # 选择命中最多的表
            candidate_tables.sort(key=lambda x: x[0], reverse=True)
            table = candidate_tables[0][1]

            rows = table.find_all('tr')
            current_section = None
            current_dates: List[str] = []

            def start_section(name: str, dates: List[str]):
                nonlocal current_section, current_dates
                current_section = name
                # 日期标准化 + 只保留最近4期
                norm = [self.normalize_date_string(d) for d in dates]
                current_dates = norm[:4] if len(norm) > 4 else norm[:]
                data["综合财务指标表"][f"[{name}]"] = []

            for row in rows:
                cells = row.find_all(['td', 'th'])
                if not cells:
                    continue
                first = cells[0].get_text(strip=True)

                # 如果是分段标题行（后续单元格应为日期）
                if first in section_names:
                    dates = []
                    for cell in cells[1:]:
                        t = cell.get_text(strip=True)
                        if self.is_date_format(t):
                            dates.append(self.normalize_date_string(t))
                    if dates:
                        start_section(first, dates)
                    continue

                # 普通指标行（归属于当前分段）
                if current_section and len(cells) >= 2:
                    indicator_name = first
                    values = []
                    # 与分段日期一一对应（确保每条都有日期）
                    for i, dt in enumerate(current_dates):
                        if i < len(cells) - 1:
                            val = cells[i + 1].get_text(strip=True)
                        else:
                            val = "--"
                        values.append({"日期": dt, "数值": (val if val else "--")})

                    # 写入
                    data["综合财务指标表"][indicator_name] = values

        except Exception as e:
            print(f"❌ 提取综合财务指标表失败: {str(e)}")

    def normalize_date_string(self, text: str) -> str:
        """将日期规范化为 YYYY-MM-DD（两位年份补 20 前缀，分隔符统一为 '-'）"""
        if not text:
            return text
        text = text.strip()
        # 25-03-31 → 2025-03-31
        m = re.match(r'^(\d{2})-(\d{2})-(\d{2})$', text)
        if m:
            yy, mm, dd = m.groups()
            return f"20{yy}-{mm}-{dd}"
        m = re.match(r'^(\d{2})/(\d{2})/(\d{2})$', text)
        if m:
            yy, mm, dd = m.groups()
            return f"20{yy}-{mm}-{dd}"
        # 2024/12/31 → 2024-12-31
        m = re.match(r'^(\d{4})/(\d{2})/(\d{2})$', text)
        if m:
            yyyy, mm, dd = m.groups()
            return f"{yyyy}-{mm}-{dd}"
        # 2024年12月31日 → 2024-12-31
        m = re.match(r'^(\d{4})年(\d{1,2})月(\d{1,2})日$', text)
        if m:
            yyyy, mm, dd = m.groups()
            return f"{yyyy}-{int(mm):02d}-{int(dd):02d}"
        return text

    def extract_balance_sheet_data(self, soup: BeautifulSoup, data: Dict, stock_code: str):
        """提取资产负债表数据"""
        try:
            # 查找包含资产负债表数据的表格
            tables = soup.find_all('table')
            
            for table in tables:
                rows = table.find_all('tr')
                if len(rows) < 2:
                    continue
                
                # 检查是否包含资产负债表相关关键词
                table_text = table.get_text()
                if any(keyword in table_text for keyword in ['总资产', '流动资产', '非流动资产', '总负债', '流动负债', '非流动负债']):
                    print("🎯 找到资产负债表数据")
                    
                    # 提取表头
                    header_row = rows[0]
                    header_cells = header_row.find_all(['th', 'td'])
                    
                    # 检查是否有日期信息
                    has_date = False
                    for cell in header_cells:
                        cell_text = cell.get_text(strip=True)
                        if self.is_date_format(cell_text):
                            has_date = True
                            break
                    
                    if has_date:
                        # 如果有日期，按时间序列处理
                        dates = []
                        for cell in header_cells[1:]:
                            date_text = cell.get_text(strip=True)
                            if self.is_date_format(date_text):
                                dates.append(date_text)
                        
                        if dates:
                            # 处理数据行
                            for row in rows[1:]:
                                cells = row.find_all(['td', 'th'])
                                if len(cells) >= 2:
                                    indicator_name = cells[0].get_text(strip=True).strip()
                                    if indicator_name and not indicator_name.startswith('指标'):
                                        # 资产负债表：强制仅保留日期+数值，不带占比
                                        values = self.extract_values_with_dates(cells[1:], dates, include_ratio=False)
                                        if values:
                                            data["资产负债表"][indicator_name] = values
                                            print(f"✅ 提取资产负债表指标: {indicator_name} = {len(values)}个数据点")
                    else:
                        # 如果没有日期，按单期数据处理
                        for row in rows[1:]:
                            cells = row.find_all(['td', 'th'])
                            if len(cells) >= 3:
                                indicator_name = cells[0].get_text(strip=True).strip()
                                if indicator_name and not indicator_name.startswith('指标'):
                                    amount = cells[1].get_text(strip=True)
                                    ratio = cells[2].get_text(strip=True) if len(cells) > 2 else ""
                                    
                                    if amount and amount != '--':
                                        data_item = {
                                            "数值": amount
                                        }
                                        # 资产负债表：单期表也不保留占比
                                        
                                        data["资产负债表"][indicator_name] = [data_item]
                                        print(f"✅ 提取资产负债表指标: {indicator_name} = {amount}")
                                
        except Exception as e:
            print(f"❌ 提取资产负债表数据失败: {str(e)}")

    def filter_invalid_data(self, data: Dict):
        """过滤无效数据（连续几个季度都为空或0的数据）"""
        try:
            for category in data:
                if isinstance(data[category], dict):
                    filtered_indicators = {}
                    
                    for indicator, values in data[category].items():
                        if isinstance(values, list) and len(values) > 0:
                            # 检查是否所有数据都为空或0
                            all_empty = True
                            for value_item in values:
                                if isinstance(value_item, dict):
                                    numeric_value = value_item.get('数值', '')
                                    if numeric_value and numeric_value not in ['--', '0', '0.0', '0.00', '0.00%']:
                                        all_empty = False
                                        break
                                else:
                                    all_empty = False
                                    break
                            
                            if not all_empty:
                                filtered_indicators[indicator] = values
                            else:
                                print(f"🗑️ 过滤掉无效指标: {indicator}")
                    
                    data[category] = filtered_indicators
                    
        except Exception as e:
            print(f"❌ 过滤无效数据失败: {str(e)}")

    def optimize_financial_data(self, data: Dict):
        """优化财务数据，保留核心指标，删除冗余数据"""
        try:
            # 定义核心指标
            core_indicators = {
                "财务指标": [
                    "营业收入", "净利润", "营业利润", "毛利率", "净利率", 
                    "净资产收益率(加权)(%)", "资产负债率(%)", "ROE", "ROA"
                ],
                "现金流量": [
                    "经营活动产生的现金流量净额", "投资活动产生的现金流量净额", 
                    "筹资活动产生的现金流量净额"
                ],
                "资产负债表": [
                    "总资产", "流动资产", "货币资金", "应收账款", "存货",
                    "固定资产", "总负债", "流动负债", "短期借款", 
                    "应付票据及应付账款", "长期借款", "股东权益合计"
                ]
            }
            
            # 时间序列指标（应该带日期，不是占比）
            time_series_indicators = [
                "营业收入", "营业利润", "净利润", "经营活动产生的现金流量净额",
                "投资活动产生的现金流量净额", "筹资活动产生的现金流量净额",
                "总资产", "流动资产", "货币资金", "应收账款", "存货", "固定资产",
                "短期借款", "应付票据及应付账款", "长期借款", "股东权益合计"
            ]
            
            # 删除的冗余指标
            redundant_indicators = [
                "营业利润其他项目", "营业利润平衡项目", "加:影响净利润的其他项目",
                "净利润其他项目", "净利润差额(合计平衡项目)", "终止经营净利润",
                "经营活动产生的现金流量净额其他项目", "经营活动产生的现金流量净额平衡项目",
                "投资活动产生的现金流量净额其他项目", "投资活动产生的现金流量净额平衡项目",
                "筹资活动产生的现金流量净额其他项目", "筹资活动产生的现金流量净额平衡项目",
                "流动资产平衡项目", "非流动资产平衡项目", "资产总计",
                "流动负债平衡项目", "非流动负债平衡项目", "负债合计",
                "归属于母公司股东权益平衡项目", "归属于母公司股东权益总计",
                "负债和股东权益总计", "营业总收入", "营业总成本", "营业成本",
                "研发费用", "营业税金及附加", "销售费用", "管理费用", "财务费用",
                "其中:利息费用", "其中:利息收入", "加:公允价值变动收益", "投资收益",
                "其中:对联营企业和合营企业的投资收益", "资产处置收益", "资产减值损失(新)",
                "信用减值损失(新)", "其他收益", "加:营业外收入", "减:营业外支出",
                "利润总额", "减:所得税", "持续经营净利润", "归属于母公司股东的净利润",
                "扣除非经常性损益后的净利润", "基本每股收益", "稀释每股收益",
                "归属于母公司股东的其他综合收益", "综合收益总额", "归属于母公司股东的综合收益总额",
                "审计意见(境内)", "其他综合收益", "专项储备", "盈余公积", "未分配利润",
                "实收资本（或股本）", "资本公积", "减:库存股", "在建工程", "使用权资产",
                "长期待摊费用", "商誉", "递延所得税资产", "其他非流动资产", "非流动资产合计",
                "预付账款", "非流动资产", "总负债金额", "流动负债", "非流动负债",
                "其他应付款合计", "一年内到期的非流动负债", "其他流动负债", "流动负债合计",
                "租赁负债", "长期应付款", "递延收益", "递延所得税负债", "非流动负债合计"
            ]
            
            # 优化数据
            for category in data:
                if isinstance(data[category], dict):
                    optimized_indicators = {}
                    
                    for indicator, values in data[category].items():
                        # 跳过冗余指标
                        if indicator in redundant_indicators:
                            print(f"🗑️ 删除冗余指标: {indicator}")
                            continue
                        
                        # 保留核心指标
                        if category in core_indicators and indicator in core_indicators[category]:
                            optimized_indicators[indicator] = values
                            print(f"✅ 保留核心指标: {indicator}")
                        # 保留其他有意义的指标（不在冗余列表中）
                        elif indicator not in redundant_indicators:
                            optimized_indicators[indicator] = values
                            print(f"📊 保留指标: {indicator}")
                    
                    data[category] = optimized_indicators
                    
        except Exception as e:
            print(f"❌ 优化财务数据失败: {str(e)}")

    def simplify_data_format(self, data: Dict):
        """简化数据格式，减少文本长度"""
        try:
            for category in data:
                if isinstance(data[category], dict):
                    for indicator, values in data[category].items():
                        if isinstance(values, list):
                            # 简化时间序列数据
                            simplified_values = []
                            for value_item in values:
                                if isinstance(value_item, dict):
                                    simplified_item = {}
                                    
                                    # 保留日期
                                    if '日期' in value_item:
                                        simplified_item['日期'] = value_item['日期']
                                    
                                    # 简化数值（去掉不必要的精度）
                                    if '数值' in value_item:
                                        numeric_value = value_item['数值']
                                        if numeric_value and numeric_value != '--':
                                            # 简化数值格式
                                            simplified_item['数值'] = self.simplify_numeric_value(numeric_value)
                                        else:
                                            simplified_item['数值'] = numeric_value
                                    
                                    # 保留占比（如果重要）
                                    if '占比' in value_item:
                                        ratio = value_item['占比']
                                        if ratio and ratio != '0.00%' and ratio != '0%':
                                            simplified_item['占比'] = ratio
                                    
                                    simplified_values.append(simplified_item)
                            
                            data[category][indicator] = simplified_values
                            
        except Exception as e:
            print(f"❌ 简化数据格式失败: {str(e)}")

    def simplify_numeric_value(self, value: str) -> str:
        """简化数值格式"""
        if not value:
            return value
        
        # 处理百分比
        if '%' in value:
            try:
                numeric_part = re.sub(r'[^\d.-]', '', value)
                if numeric_part:
                    num = float(numeric_part)
                    if num == int(num):
                        return f"{int(num)}%"
                    else:
                        return f"{num:.1f}%"
            except:
                pass
            return value
        
        # 处理金额（亿、万）
        if '亿' in value or '万' in value:
            try:
                # 保持原始格式，但去掉多余的小数位
                if '亿' in value:
                    # 提取数字部分
                    numeric_part = re.sub(r'[^\d.-]', '', value)
                    if numeric_part:
                        num = float(numeric_part)
                        if num == int(num):
                            return f"{int(num)}亿"
                        else:
                            return f"{num:.2f}亿"
                elif '万' in value:
                    numeric_part = re.sub(r'[^\d.-]', '', value)
                    if numeric_part:
                        num = float(numeric_part)
                        if num == int(num):
                            return f"{int(num)}万"
                        else:
                            return f"{num:.1f}万"
            except:
                pass
            return value
        
        # 处理普通数字
        try:
            num = float(value)
            if num == int(num):
                return str(int(num))
            else:
                return f"{num:.2f}"
        except:
            return value

    def extract_profitability_indicators(self, soup: BeautifulSoup, data: Dict, stock_code: str):
        """提取盈利能力指标（营收、净利、毛利率、净利率）"""
        try:
            tables = soup.find_all('table')
            
            for table in tables:
                rows = table.find_all('tr')
                if len(rows) < 2:
                    continue
                
                # 获取表头（日期）
                header_row = rows[0]
                header_cells = header_row.find_all(['th', 'td'])
                dates = []
                for cell in header_cells[1:]:  # 跳过第一列（指标名称）
                    date_text = cell.get_text(strip=True)
                    if self.is_date_format(date_text):
                        dates.append(date_text)
                
                if not dates:
                    continue
                
                # 处理数据行
                for row in rows[1:]:
                    cells = row.find_all(['td', 'th'])
                    if len(cells) >= 2:
                        indicator_name = cells[0].get_text(strip=True)
                        
                        # 检查是否包含盈利能力相关指标
                        if any(keyword in indicator_name for keyword in ['营业收入', '净利润', '毛利率', '净利率', '营业利润']):
                            values = self.extract_values_with_dates(cells[1:], dates)
                            if values:
                                data["财务指标"][indicator_name] = values
                                print(f"✅ 提取盈利能力指标: {indicator_name} = {len(values)}个数据点")
                                
        except Exception as e:
            print(f"❌ 提取盈利能力指标失败: {str(e)}")

    def extract_operation_indicators(self, soup: BeautifulSoup, data: Dict, stock_code: str):
        """提取运营指标（ROE、ROIC、负债率等）"""
        try:
            tables = soup.find_all('table')
            
            for table in tables:
                rows = table.find_all('tr')
                if len(rows) < 2:
                    continue
                
                # 获取表头（日期）
                header_row = rows[0]
                header_cells = header_row.find_all(['th', 'td'])
                dates = []
                for cell in header_cells[1:]:  # 跳过第一列（指标名称）
                    date_text = cell.get_text(strip=True)
                    if self.is_date_format(date_text):
                        dates.append(date_text)
                
                if not dates:
                    continue
                
                # 处理数据行
                for row in rows[1:]:
                    cells = row.find_all(['td', 'th'])
                    if len(cells) >= 2:
                        indicator_name = cells[0].get_text(strip=True)
                        
                        # 检查是否包含运营相关指标
                        if any(keyword in indicator_name for keyword in ['ROE', 'ROIC', '负债率', '资产负债率', '净资产收益率', '总资产收益率']):
                            values = self.extract_values_with_dates(cells[1:], dates)
                            if values:
                                data["财务指标"][indicator_name] = values
                                print(f"✅ 提取运营指标: {indicator_name} = {len(values)}个数据点")
                                
        except Exception as e:
            print(f"❌ 提取运营指标失败: {str(e)}")

    def extract_valuation_indicators(self, soup: BeautifulSoup, data: Dict, stock_code: str):
        """提取估值指标（PE、PB、PS、PEG等）"""
        try:
            tables = soup.find_all('table')
            
            for table in tables:
                rows = table.find_all('tr')
                if len(rows) < 2:
                    continue
                
                # 获取表头（日期）
                header_row = rows[0]
                header_cells = header_row.find_all(['th', 'td'])
                dates = []
                for cell in header_cells[1:]:  # 跳过第一列（指标名称）
                    date_text = cell.get_text(strip=True)
                    if self.is_date_format(date_text):
                        dates.append(date_text)
                
                if not dates:
                    continue
                
                # 处理数据行
                for row in rows[1:]:
                    cells = row.find_all(['td', 'th'])
                    if len(cells) >= 2:
                        indicator_name = cells[0].get_text(strip=True)
                        
                        # 检查是否包含估值相关指标
                        if any(keyword in indicator_name for keyword in ['PE', 'PB', 'PS', 'PEG', '市盈率', '市净率', '市销率']):
                            values = self.extract_values_with_dates(cells[1:], dates)
                            if values:
                                data["估值指标"][indicator_name] = values
                                print(f"✅ 提取估值指标: {indicator_name} = {len(values)}个数据点")
                                
        except Exception as e:
            print(f"❌ 提取估值指标失败: {str(e)}")

    def extract_cash_flow_indicators(self, soup: BeautifulSoup, data: Dict, stock_code: str):
        """提取现金流量指标"""
        try:
            tables = soup.find_all('table')
            
            for table in tables:
                rows = table.find_all('tr')
                if len(rows) < 2:
                    continue
                
                # 获取表头（日期）
                header_row = rows[0]
                header_cells = header_row.find_all(['th', 'td'])
                dates = []
                for cell in header_cells[1:]:  # 跳过第一列（指标名称）
                    date_text = cell.get_text(strip=True)
                    if self.is_date_format(date_text):
                        dates.append(date_text)
                
                if not dates:
                    continue
                
                # 处理数据行
                for row in rows[1:]:
                    cells = row.find_all(['td', 'th'])
                    if len(cells) >= 2:
                        indicator_name = cells[0].get_text(strip=True)
                        
                        # 检查是否包含现金流量相关指标
                        if any(keyword in indicator_name for keyword in ['经营现金流', '投资现金流', '筹资现金流', '现金流量', '经营活动现金流量', '经营活动产生的现金流量净额']):
                            values = self.extract_values_with_dates(cells[1:], dates)
                            if values:
                                # 确保数据完整性
                                if len(values) == len(dates):
                                    data["现金流量"][indicator_name] = values
                                    print(f"✅ 提取现金流量指标: {indicator_name} = {len(values)}个数据点")
                                
        except Exception as e:
            print(f"❌ 提取现金流量指标失败: {str(e)}")

    def extract_dividend_indicators(self, soup: BeautifulSoup, data: Dict, stock_code: str):
        """提取分红指标"""
        try:
            tables = soup.find_all('table')
            
            for table in tables:
                rows = table.find_all('tr')
                if len(rows) < 2:
                    continue
                
                # 获取表头（日期）
                header_row = rows[0]
                header_cells = header_row.find_all(['th', 'td'])
                dates = []
                for cell in header_cells[1:]:  # 跳过第一列（指标名称）
                    date_text = cell.get_text(strip=True)
                    if self.is_date_format(date_text):
                        dates.append(date_text)
                
                if not dates:
                    continue
                
                # 处理数据行
                for row in rows[1:]:
                    cells = row.find_all(['td', 'th'])
                    if len(cells) >= 2:
                        indicator_name = cells[0].get_text(strip=True)
                        
                        # 检查是否包含分红相关指标
                        if any(keyword in indicator_name for keyword in ['分红率', '股息率', '分红', '股息']):
                            values = self.extract_values_with_dates(cells[1:], dates)
                            if values:
                                data["分红指标"][indicator_name] = values
                                print(f"✅ 提取分红指标: {indicator_name} = {len(values)}个数据点")
                                
        except Exception as e:
            print(f"❌ 提取分红指标失败: {str(e)}")

    def extract_values_with_dates(self, cells: List, dates: List, include_ratio: bool = True) -> List[Dict]:
        """从单元格中提取数值，并与日期对应
        include_ratio: 是否尝试从相邻单元格提取占比（默认开启）
        """
        values = []
        i = 0
        while i < len(cells) and i < len(dates):
            value = cells[i].get_text(strip=True)
            if value and value != '-':
                data_item = {
                    "日期": dates[i],
                    "数值": value
                }
                
                # 根据需要决定是否提取占比
                if include_ratio and i + 1 < len(cells):
                    next_value = cells[i + 1].get_text(strip=True)
                    if next_value and '%' in next_value:
                        data_item["占比"] = next_value
                        i += 1  # 跳过占比单元格
                
                values.append(data_item)
            elif value == '-':
                # 对于缺失数据，也添加记录
                data_item = {
                    "日期": dates[i],
                    "数值": "--"
                }
                values.append(data_item)
            i += 1
        return values

    def is_date_format(self, text: str) -> bool:
        """判断是否为日期格式"""
        if not text:
            return False
        
        # 匹配常见的日期格式
        date_patterns = [
            r'\d{4}-\d{2}-\d{2}',  # 2024-12-31
            r'\d{4}/\d{2}/\d{2}',  # 2024/12/31
            r'\d{4}年\d{2}月\d{2}日',  # 2024年12月31日
            r'\d{4}年\d{1,2}月',  # 2024年12月
            r'\d{4}-\d{2}',  # 2024-12
            r'\d{2}-\d{2}-\d{2}',  # 25-03-31（东方财富常见样式）
            r'\d{2}/\d{2}/\d{2}',  # 25/03/31
        ]
        
        for pattern in date_patterns:
            if re.match(pattern, text):
                return True
        
        return False

    def clean_financial_data(self, financial_data: Dict) -> Dict:
        """清理和格式化财务数据"""
        cleaned_data = {}
        
        for category, indicators in financial_data.items():
            if isinstance(indicators, dict) and indicators:
                cleaned_data[category] = {}
                for indicator, values in indicators.items():
                    if values:
                        cleaned_data[category][indicator] = values
            elif isinstance(indicators, list) and indicators:
                cleaned_data[category] = indicators
        
        return cleaned_data

    def fix_data_format_issues(self, data: Dict):
        """修复数据格式问题，确保时间序列数据有日期，单期数据有占比"""
        try:
            # 时间序列指标（应该带日期）
            time_series_indicators = [
                "营业收入", "营业利润", "净利润", "经营活动产生的现金流量净额",
                "投资活动产生的现金流量净额", "筹资活动产生的现金流量净额",
                "总资产", "流动资产", "货币资金", "应收账款", "存货", "固定资产",
                "短期借款", "应付票据及应付账款", "长期借款", "股东权益合计"
            ]
            
            # 单期指标（应该带占比）
            single_period_indicators = [
                "基本每股收益(元)", "稀释每股收益(元)", "每股净资产(元)", "每股公积金(元)",
                "每股未分配利润(元)", "每股经营现金流(元)", "毛利率(%)", "净利率(%)",
                "净资产收益率(加权)(%)", "资产负债率(%)", "流动比率", "速动比率"
            ]
            
            for category in data:
                if isinstance(data[category], dict):
                    for indicator, values in data[category].items():
                        if isinstance(values, list) and len(values) > 0:
                            # 检查第一个数据项的格式
                            first_item = values[0]
                            if isinstance(first_item, dict):
                                has_date = '日期' in first_item
                                has_ratio = '占比' in first_item
                                
                                # 修复时间序列数据（应该有日期，不应该有占比）
                                if indicator in time_series_indicators and has_ratio and not has_date:
                                    print(f"🔧 修复时间序列数据格式: {indicator}")
                                    for item in values:
                                        if '占比' in item:
                                            # 将占比转换为日期（这里需要根据实际情况调整）
                                            # 暂时删除占比，因为时间序列数据不应该有占比
                                            del item['占比']
                                
                                # 修复单期数据（应该有占比，不应该有日期）
                                elif indicator in single_period_indicators and has_date and not has_ratio:
                                    print(f"🔧 修复单期数据格式: {indicator}")
                                    for item in values:
                                        if '日期' in item:
                                            # 删除日期，因为单期数据不应该有日期
                                            del item['日期']
                                
        except Exception as e:
            print(f"❌ 修复数据格式失败: {str(e)}")


    def restructure_balance_sheet_sections(self, data: Dict):
        """重构资产负债表区块：
        - 将“盈利能力指标/收益质量指标/财务风险指标/营运能力指标”识别为分段标题，用方括号包裹
        - 只保留至“应收账款周转率(次)”及以上的时间序列指标
        - 所有保留指标均为多期，保证每条都有日期
        """
        try:
            if "资产负债表" not in data or not isinstance(data["资产负债表"], dict):
                return
            original = data["资产负债表"]

            section_order = [
                "盈利能力指标",
                "收益质量指标",
                "财务风险指标",
                "营运能力指标",
            ]

            section_to_indicators = {
                "盈利能力指标": [
                    "净资产收益率(加权)(%)",
                    "总资产收益率(加权)(%)",
                    "毛利率(%)",
                    "净利率(%)",
                ],
                "收益质量指标": [
                    "销售净现金流/营业收入",
                    "经营净现金流/营业收入",
                    "实际税率(%)",
                ],
                "财务风险指标": [
                    "流动比率",
                    "速动比率",
                    "现金流量比率",
                    "资产负债率(%)",
                    "权益系数",
                    "产权比率",
                ],
                "营运能力指标": [
                    "总资产周转天数(天)",
                    "存货周转天数(天)",
                    "应收账款周转天数(天)",
                    "总资产周转率(次)",
                    "存货周转率(次)",
                    "应收账款周转率(次)",
                ],
            }

            # 获取每个分段的日期序列（这些行的数值和日期相同）
            def get_section_dates(name: str):
                key_candidates = [name, f"[{name}]"]
                for k in key_candidates:
                    if k in original and isinstance(original[k], list):
                        items = original[k]
                        if items and isinstance(items[0], dict) and items[0].get("日期") == items[0].get("数值"):
                            return [it.get("日期") for it in items if isinstance(it, dict)]
                return None

            def normalize_values(values, dates):
                normalized = []
                if not isinstance(values, list) or not dates:
                    return normalized
                # 如果本身已是含日期的结构，直接按最短长度截断
                if values and isinstance(values[0], dict) and "日期" in values[0]:
                    for i, it in enumerate(values[: len(dates)]):
                        if isinstance(it, dict):
                            item = {"日期": it.get("日期", dates[i]), "数值": it.get("数值", "--")}
                            normalized.append(item)
                    return normalized
                # 否则按索引配对日期
                for i in range(min(len(values), len(dates))):
                    v = values[i]
                    if isinstance(v, dict):
                        val = v.get("数值", "--")
                    else:
                        val = str(v)
                    normalized.append({"日期": dates[i], "数值": val})
                return normalized

            rebuilt = {}

            for section in section_order:
                dates = get_section_dates(section)
                # 分段标题（用方括号包裹）
                rebuilt[f"[{section}]"] = []
                for ind in section_to_indicators.get(section, []):
                    vals = original.get(ind)
                    if vals:
                        rebuilt[ind] = normalize_values(vals, dates)

            # 仅保留重构后的这些键
            data["资产负债表"] = rebuilt

        except Exception as e:
            print(f"❌ 重构资产负债表区块失败: {str(e)}")

    def trim_to_recent_periods(self, data: Dict, periods: int = 4):
        """将所有时间序列指标裁剪为最近 N 期"""
        try:
            for category, indicators in list(data.items()):
                if isinstance(indicators, dict):
                    for indicator, values in list(indicators.items()):
                        if isinstance(values, list) and len(values) > periods:
                            # 仅对时间序列裁剪；若元素为 dict 且大多包含 '日期' 字段则裁剪
                            if isinstance(values[0], dict):
                                if '日期' in values[0] or '数值' in values[0]:
                                    # 先规范日期；若缺失日期，尝试从同类标题行收集（上一步已在 extract 中完成）
                                    trimmed = []
                                    for item in values[:periods]:
                                        if isinstance(item, dict):
                                            if '日期' in item and item['日期']:
                                                item['日期'] = self.normalize_date_string(item['日期'])
                                                trimmed.append(item)
                                            else:
                                                # 没有日期则补全为最近 periods 中的相应日期（若存在）
                                                # 这里无法获知分段的统一日期，只能丢弃，防止无日期数据
                                                pass
                                    data[category][indicator] = trimmed
        except Exception as e:
            print(f"❌ 裁剪时间序列失败: {str(e)}")


def main():
    if len(sys.argv) != 2:
        print("使用方法: python EastMoneyFinancialAnalysis.py <股票代码>", file=sys.stderr)
        sys.exit(1)
    
    stock_code = sys.argv[1]
    
    # 创建分析器实例
    analyzer = EastMoneyFinancialAnalysis()
    
    # 获取财务数据
    result = analyzer.get_financial_data(stock_code)
    
    if result["success"]:
        # 清理数据
        cleaned_data = analyzer.clean_financial_data(result["data"])
        
        # 优化数据（不改变数值本身）
        analyzer.optimize_financial_data(cleaned_data)

        # 重构资产负债表区块并裁剪到“应收账款周转率(次)”及以上
        analyzer.restructure_balance_sheet_sections(cleaned_data)

        # 仅保留最近4期
        analyzer.trim_to_recent_periods(cleaned_data, periods=8)
        
        # 输出JSON格式数据
        output = {
            "success": True,
            "stock_code": stock_code,
            "financial_data": cleaned_data
        }
        
        print(json.dumps(output, ensure_ascii=False, indent=2))
    else:
        output = {
            "success": False,
            "stock_code": stock_code,
            "message": result.get("error", "获取财务数据失败")
        }
        print(json.dumps(output, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
