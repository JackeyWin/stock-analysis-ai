#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
东方财富同行比较数据提取脚本
从HTML页面提取成长性、估值、杜邦分析、市场表现、公司规模等指标
"""

import json
import re
from bs4 import BeautifulSoup
from typing import Dict, Any
import sys
import time
from typing import Dict, Any, List
from bs4 import BeautifulSoup
from playwright.sync_api import sync_playwright
import re

# 重定向print到stderr，避免干扰JSON输出
import sys
original_print = print
def debug_print(*args, **kwargs):
    """调试信息输出到stderr"""
    kwargs['file'] = sys.stderr
    original_print(*args, **kwargs)

# 替换所有print为debug_print
print = debug_print

# 可选渲染回退
try:
    from playwright.sync_api import sync_playwright
    HAS_PLAYWRIGHT = True
except Exception:
    HAS_PLAYWRIGHT = False


class EastMoneyPeerComparison:
    def __init__(self):
        self.headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36'
        }

    def get_peer_comparison_data(self, stock_code: str) -> Dict[str, Any]:
        """
        获取同行比较数据
        
        Args:
            stock_code: 股票代码
            
        Returns:
            包含同行比较数据的字典
        """
        if not HAS_PLAYWRIGHT:
            return {
                "success": False,
                "error": "Playwright未安装，请安装: pip install playwright && playwright install",
                "data": None
            }
        
        try:
            # 构建市场代码
            market = "SZ" if stock_code.startswith(("00", "30")) else "SH" if stock_code.startswith(("60", "68")) else "SZ"
            code = f"{market}{stock_code}"
            url = f"https://emweb.securities.eastmoney.com/pc_hsf10/pages/index.html?type=web&code={code}&color=b#/thbj"
            
            debug_print(f"正在访问同行比较页面: {url}")
            
            with sync_playwright() as p:
                browser = p.chromium.launch(headless=True)
                page = browser.new_page(user_agent=self.headers['User-Agent'])
                page.set_default_timeout(45000)
                
                # 访问页面并等待加载
                page.goto(url, wait_until="networkidle", timeout=45000)
                
                # 等待同行比较内容加载
                try:
                    page.wait_for_selector(".section.czxbj", timeout=10000)
                    debug_print("✅ 成长性比较表格已加载")
                except Exception:
                    debug_print("⚠️ 成长性比较表格加载超时")
                
                try:
                    page.wait_for_selector(".section.gzbj", timeout=10000)
                    debug_print("✅ 估值比较表格已加载")
                except Exception:
                    debug_print("⚠️ 估值比较表格加载超时")
                
                try:
                    page.wait_for_selector(".section.dbfxbj", timeout=10000)
                    debug_print("✅ 杜邦分析表格已加载")
                except Exception:
                    debug_print("⚠️ 杜邦分析表格加载超时")
                
                # 获取页面HTML内容
                html_content = page.content()
                browser.close()
                
                # 解析HTML内容
                return self.parse_html_data(html_content, stock_code)
                
        except Exception as e:
            return {
                "success": False,
                "error": f"渲染失败: {str(e)}",
                "data": None
            }

    def parse_html_data(self, html_content: str, stock_code: str) -> Dict[str, Any]:
        """
        解析HTML内容提取同行比较数据
        
        Args:
            html_content: HTML页面内容
            stock_code: 股票代码
            
        Returns:
            解析后的数据字典
        """
        try:
            soup = BeautifulSoup(html_content, 'html.parser')
            
            data = {
                "成长性指标": {},
                "估值指标": {},
                "杜邦分析": {},
                "公司规模": {},
                "市场表现": {},
                "同行比较": {},
                "行业平均": {}
            }
            
            # 1. 提取成长性比较数据
            self.extract_growth_indicators(soup, data, stock_code)
            
            # 2. 提取估值比较数据
            self.extract_valuation_indicators(soup, data, stock_code)
            
            # 3. 提取杜邦分析比较数据
            self.extract_dupont_analysis(soup, data, stock_code)
            
            # 4. 提取公司规模数据
            self.extract_company_size(soup, data, stock_code)
            
            # 5. 提取市场表现数据
            self.extract_market_performance(soup, data, stock_code)
            
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

    def extract_growth_indicators(self, soup: BeautifulSoup, data: Dict, stock_code: str):
        """提取成长性指标"""
        try:
            # 查找成长性比较表格 - 尝试多种选择器
            growth_section = None
            selectors = [
                'div[class*="czxbj"]',
                'div[class*="growth"]',
                'div[class*="成长"]',
                'table[class*="czxbj"]',
                'table[class*="growth"]',
                '.section.czxbj',
                '.section.growth'
            ]
            
            for selector in selectors:
                growth_section = soup.select_one(selector)
                if growth_section:
                    debug_print(f"✅ 使用选择器 '{selector}' 找到成长性表格")
                    break
            
            if not growth_section:
                # 如果还是找不到，尝试查找包含"成长性"文本的div
                growth_section = soup.find('div', string=lambda text: text and '成长性' in text)
                if growth_section:
                    debug_print("✅ 通过文本内容找到成长性表格")
                    growth_section = growth_section.parent
                else:
                    debug_print("❌ 未找到成长性比较表格")
                    return
            
            debug_print("✅ 找到成长性比较表格")
            
            # 查找目标股票行和行业平均行
            target_row = None
            industry_avg_row = None
            
            # 尝试多种方式查找行
            rows = []
            if growth_section.find('tr'):
                rows = growth_section.find_all('tr')
            elif growth_section.find('tbody'):
                rows = growth_section.find('tbody').find_all('tr')
            elif growth_section.find('table'):
                rows = growth_section.find('table').find_all('tr')
            
            debug_print(f"🔍 成长性表格共有 {len(rows)} 行数据")
            
            for i, row in enumerate(rows):
                cells = row.find_all(['td', 'th'])  # 同时查找td和th
                if len(cells) > 2:
                    cell_text = cells[1].get_text(strip=True)
                    debug_print(f"  行{i}: 股票代码/名称 = '{cell_text}'")
                    
                    if cell_text == stock_code:
                        target_row = row
                        target_row_index = i
                        debug_print(f"  ✅ 找到目标股票行: {i}")
                        
                        # 获取下一行作为行业平均数据
                        if i + 1 < len(rows):
                            next_row = rows[i + 1]
                            next_cells = next_row.find_all(['td', 'th'])
                            if len(next_cells) > 0:
                                first_cell_text = next_cells[0].get_text(strip=True)
                                if "行业平均" in first_cell_text:
                                    industry_avg_row = next_row
                                    debug_print(f"  ✅ 找到行业平均行: {i + 1}")
                        break
            
            # 提取目标股票数据
            if target_row:
                cells = target_row.find_all(['td', 'th'])
                if len(cells) >= 15:
                    # 基本每股收益增长率
                    data["成长性指标"]["每股收益增长率_3年复合"] = self.clean_number(cells[3].get_text(strip=True))
                    data["成长性指标"]["每股收益增长率_2024年"] = self.clean_number(cells[4].get_text(strip=True))
                    data["成长性指标"]["每股收益增长率_滚动12个月"] = self.clean_number(cells[5].get_text(strip=True))
                    data["成长性指标"]["每股收益增长率_2025年预期"] = self.clean_number(cells[6].get_text(strip=True))
                    data["成长性指标"]["每股收益增长率_2026年预期"] = self.clean_number(cells[7].get_text(strip=True))
                    data["成长性指标"]["每股收益增长率_2027年预期"] = self.clean_number(cells[8].get_text(strip=True))
                    
                    # 营业收入增长率
                    data["成长性指标"]["营业收入增长率_3年复合"] = self.clean_number(cells[9].get_text(strip=True))
                    data["成长性指标"]["营业收入增长率_2024年"] = self.clean_number(cells[10].get_text(strip=True))
                    data["成长性指标"]["营业收入增长率_滚动12个月"] = self.clean_number(cells[11].get_text(strip=True))
                    data["成长性指标"]["营业收入增长率_2025年预期"] = self.clean_number(cells[12].get_text(strip=True))
                    data["成长性指标"]["营业收入增长率_2026年预期"] = self.clean_number(cells[13].get_text(strip=True))
                    data["成长性指标"]["营业收入增长率_2027年预期"] = self.clean_number(cells[14].get_text(strip=True))
                    
                    # 排名信息
                    rank_text = cells[0].get_text(strip=True)
                    if '/' in rank_text:
                        rank_parts = rank_text.split('/')
                        data["同行比较"]["成长性排名"] = rank_parts[0]
                        data["同行比较"]["成长性总数量"] = rank_parts[1]
                    
                    debug_print(f"✅ 成功提取成长性指标，排名: {rank_text}")
                else:
                    debug_print(f"⚠️ 成长性指标单元格数量不足: {len(cells)}")
            else:
                debug_print(f"❌ 未找到股票 {stock_code} 的成长性数据行")
            
            # 提取行业平均数据 - 修正偏移
            if industry_avg_row:
                cells = industry_avg_row.find_all(['td', 'th'])
                if len(cells) >= 15:
                    data["行业平均"]["成长性"] = {
                        "每股收益增长率_3年复合": self.clean_number(cells[1].get_text(strip=True)),  # 修正：从3改为1
                        "每股收益增长率_2024年": self.clean_number(cells[2].get_text(strip=True)),  # 修正：从4改为2
                        "每股收益增长率_滚动12个月": self.clean_number(cells[3].get_text(strip=True)),
                        "每股收益增长率_2025年预期": self.clean_number(cells[4].get_text(strip=True)),
                        "每股收益增长率_2026年预期": self.clean_number(cells[5].get_text(strip=True)),
                        "每股收益增长率_2027年预期": self.clean_number(cells[6].get_text(strip=True)),
                        "营业收入增长率_3年复合": self.clean_number(cells[7].get_text(strip=True)),
                        "营业收入增长率_2024年": self.clean_number(cells[8].get_text(strip=True)),
                        "营业收入增长率_滚动12个月": self.clean_number(cells[9].get_text(strip=True)),
                        "营业收入增长率_2025年预期": self.clean_number(cells[10].get_text(strip=True)),
                        "营业收入增长率_2026年预期": self.clean_number(cells[11].get_text(strip=True)),
                        "营业收入增长率_2027年预期": self.clean_number(cells[12].get_text(strip=True))
                    }
                    debug_print("✅ 成功提取成长性行业平均数据")
                else:
                    debug_print(f"⚠️ 行业平均行单元格数量不足: {len(cells)}")
            else:
                debug_print("❌ 未找到行业平均行")
                        
        except Exception as e:
            debug_print(f"提取成长性指标失败: {str(e)}")

    def extract_valuation_indicators(self, soup: BeautifulSoup, data: Dict, stock_code: str):
        """提取估值指标"""
        try:
            # 查找估值比较表格
            valuation_section = soup.find('div', {'class': 'gzbj'})
            if not valuation_section:
                debug_print("❌ 未找到估值比较表格")
                return
            
            debug_print("✅ 找到估值比较表格")
            
            # 查找目标股票行和行业平均行
            target_row = None
            industry_avg_row = None
            rows = valuation_section.find_all('tr')
            
            for i, row in enumerate(rows):
                cells = row.find_all(['td', 'th'])
                if len(cells) > 2:
                    cell_text = cells[1].get_text(strip=True)
                    if cell_text == stock_code:
                        target_row = row
                        target_row_index = i
                        debug_print(f"  ✅ 找到目标股票行: {i}")
                        
                        # 获取下一行作为行业平均数据
                        if i + 1 < len(rows):
                            next_row = rows[i + 1]
                            next_cells = next_row.find_all(['td', 'th'])
                            if len(next_cells) > 0:
                                first_cell_text = next_cells[0].get_text(strip=True)
                                if "行业平均" in first_cell_text:
                                    industry_avg_row = next_row
                                    debug_print(f"  ✅ 找到行业平均行: {i + 1}")
                        break
            
            # 提取目标股票数据
            if target_row:
                cells = target_row.find_all(['td', 'th'])
                if len(cells) >= 15:
                    # PEG
                    data["估值指标"]["PEG比率"] = self.clean_number(cells[3].get_text(strip=True))
                    
                    # 市盈率
                    data["估值指标"]["市盈率_2024年"] = self.clean_number(cells[4].get_text(strip=True))
                    data["估值指标"]["市盈率_滚动12个月"] = self.clean_number(cells[5].get_text(strip=True))
                    data["估值指标"]["市盈率_2025年预期"] = self.clean_number(cells[6].get_text(strip=True))
                    data["估值指标"]["市盈率_2026年预期"] = self.clean_number(cells[7].get_text(strip=True))
                    data["估值指标"]["市盈率_2027年预期"] = self.clean_number(cells[8].get_text(strip=True))
                    
                    # 市销率
                    data["估值指标"]["市销率_2024年"] = self.clean_number(cells[9].get_text(strip=True))
                    data["估值指标"]["市销率_滚动12个月"] = self.clean_number(cells[10].get_text(strip=True))
                    data["估值指标"]["市销率_2025年预期"] = self.clean_number(cells[11].get_text(strip=True))
                    data["估值指标"]["市销率_2026年预期"] = self.clean_number(cells[12].get_text(strip=True))
                    data["估值指标"]["市销率_2027年预期"] = self.clean_number(cells[13].get_text(strip=True))
                    
                    # 排名信息
                    rank_text = cells[0].get_text(strip=True)
                    if '/' in rank_text:
                        rank_parts = rank_text.split('/')
                        data["同行比较"]["估值排名"] = rank_parts[0]
                        data["同行比较"]["估值总数量"] = rank_parts[1]
                    
                    debug_print(f"✅ 成功提取估值指标，排名: {rank_text}")
                else:
                    debug_print(f"⚠️ 估值指标单元格数量不足: {len(cells)}")
            else:
                debug_print(f"❌ 未找到股票 {stock_code} 的估值数据行")
            
            # 提取行业平均数据 - 修正偏移
            if industry_avg_row:
                cells = industry_avg_row.find_all(['td', 'th'])
                if len(cells) >= 15:
                    data["行业平均"]["估值"] = {
                        "PEG比率": self.clean_number(cells[1].get_text(strip=True)),  # 修正：从3改为1
                        "市盈率_2024年": self.clean_number(cells[2].get_text(strip=True)),  # 修正：从4改为2
                        "市盈率_TTM": self.clean_number(cells[3].get_text(strip=True)),
                        "市盈率_2025年预期": self.clean_number(cells[4].get_text(strip=True)),
                        "市盈率_2026年预期": self.clean_number(cells[5].get_text(strip=True)),
                        "市盈率_2027年预期": self.clean_number(cells[6].get_text(strip=True)),
                        "市销率_2024年": self.clean_number(cells[7].get_text(strip=True)),
                        "市销率_TTM": self.clean_number(cells[8].get_text(strip=True)),
                        "市销率_2025年预期": self.clean_number(cells[9].get_text(strip=True)),
                        "市销率_2026年预期": self.clean_number(cells[10].get_text(strip=True)),
                        "市销率_2027年预期": self.clean_number(cells[11].get_text(strip=True))
                    }
                    debug_print("✅ 成功提取估值行业平均数据")
                else:
                    debug_print(f"⚠️ 行业平均行单元格数量不足: {len(cells)}")
            else:
                debug_print("❌ 未找到行业平均行")
                        
        except Exception as e:
            debug_print(f"提取估值指标失败: {str(e)}")

    def extract_dupont_analysis(self, soup: BeautifulSoup, data: Dict, stock_code: str):
        """提取杜邦分析数据"""
        try:
            # 查找杜邦分析比较表格
            dupont_section = soup.find('div', {'class': 'dbfxbj'})
            if not dupont_section:
                debug_print("❌ 未找到杜邦分析表格")
                return
            
            debug_print("✅ 找到杜邦分析表格")
            
            # 查找目标股票行和行业平均行
            target_row = None
            industry_avg_row = None
            rows = dupont_section.find_all('tr')
            
            for i, row in enumerate(rows):
                cells = row.find_all(['td', 'th'])
                if len(cells) > 2:
                    cell_text = cells[1].get_text(strip=True)
                    if cell_text == stock_code:
                        target_row = row
                        target_row_index = i
                        debug_print(f"  ✅ 找到目标股票行: {i}")
                        
                        # 获取下一行作为行业平均数据
                        if i + 1 < len(rows):
                            next_row = rows[i + 1]
                            next_cells = next_row.find_all(['td', 'th'])
                            if len(next_cells) > 0:
                                first_cell_text = next_cells[0].get_text(strip=True)
                                if "行业平均" in first_cell_text:
                                    industry_avg_row = next_row
                                    debug_print(f"  ✅ 找到行业平均行: {i + 1}")
                        break
            
            # 提取目标股票数据
            if target_row:
                cells = target_row.find_all(['td', 'th'])
                if len(cells) >= 12:
                    # ROE
                    data["杜邦分析"]["净资产收益率_3年平均"] = self.clean_number(cells[3].get_text(strip=True))
                    data["杜邦分析"]["净资产收益率_2022年"] = self.clean_number(cells[4].get_text(strip=True))
                    data["杜邦分析"]["净资产收益率_2023年"] = self.clean_number(cells[5].get_text(strip=True))
                    data["杜邦分析"]["净资产收益率_2024年"] = self.clean_number(cells[6].get_text(strip=True))
                    
                    # 净利率
                    data["杜邦分析"]["净利率_3年平均"] = self.clean_number(cells[7].get_text(strip=True))
                    data["杜邦分析"]["净利率_2022年"] = self.clean_number(cells[8].get_text(strip=True))
                    data["杜邦分析"]["净利率_2023年"] = self.clean_number(cells[9].get_text(strip=True))
                    data["杜邦分析"]["净利率_2024年"] = self.clean_number(cells[10].get_text(strip=True))
                    
                    # 排名信息
                    rank_text = cells[0].get_text(strip=True)
                    if '/' in rank_text:
                        rank_parts = rank_text.split('/')
                        data["同行比较"]["杜邦排名"] = rank_parts[0]
                        data["同行比较"]["杜邦总数量"] = rank_parts[1]
                    
                    debug_print(f"✅ 成功提取杜邦分析，排名: {rank_text}")
                else:
                    debug_print(f"⚠️ 杜邦分析单元格数量不足: {len(cells)}")
            else:
                debug_print(f"❌ 未找到股票 {stock_code} 的杜邦分析数据行")
            
            # 提取行业平均数据 - 修正偏移
            if industry_avg_row:
                cells = industry_avg_row.find_all(['td', 'th'])
                if len(cells) >= 12:
                    data["行业平均"]["杜邦分析"] = {
                        "净资产收益率_3年平均": self.clean_number(cells[1].get_text(strip=True)),  # 修正：从3改为1
                        "净资产收益率_2022年": self.clean_number(cells[2].get_text(strip=True)),  # 修正：从4改为2
                        "净资产收益率_2023年": self.clean_number(cells[3].get_text(strip=True)),
                        "净资产收益率_2024年": self.clean_number(cells[4].get_text(strip=True)),
                        "净利率_3年平均": self.clean_number(cells[5].get_text(strip=True)),
                        "净利率_2022年": self.clean_number(cells[6].get_text(strip=True)),
                        "净利率_2023年": self.clean_number(cells[7].get_text(strip=True)),
                        "净利率_2024年": self.clean_number(cells[8].get_text(strip=True))
                    }
                    debug_print("✅ 成功提取杜邦分析行业平均数据")
                else:
                    debug_print(f"⚠️ 行业平均行单元格数量不足: {len(cells)}")
            else:
                debug_print("❌ 未找到行业平均行")
                        
        except Exception as e:
            debug_print(f"提取杜邦分析失败: {str(e)}")

    def extract_company_size(self, soup: BeautifulSoup, data: Dict, stock_code: str):
        """提取公司规模数据"""
        try:
            # 查找公司规模表格
            size_section = soup.find('div', {'class': 'gsgm'})
            if not size_section:
                debug_print("❌ 未找到公司规模表格")
                return
            
            debug_print("✅ 找到公司规模表格")
            
            # 查找目标股票行和行业平均行
            target_row = None
            industry_avg_row = None
            rows = size_section.find_all('tr')
            
            for i, row in enumerate(rows):
                cells = row.find_all(['td', 'th'])
                if len(cells) > 2:
                    cell_text = cells[1].get_text(strip=True)
                    if cell_text == stock_code:
                        target_row = row
                        target_row_index = i
                        debug_print(f"  ✅ 找到目标股票行: {i}")
                        
                        # 获取下一行作为行业平均数据
                        if i + 1 < len(rows):
                            next_row = rows[i + 1]
                            next_cells = next_row.find_all(['td', 'th'])
                            if len(next_cells) > 0:
                                first_cell_text = next_cells[0].get_text(strip=True)
                                if "行业平均" in first_cell_text:
                                    industry_avg_row = next_row
                                    debug_print(f"  ✅ 找到行业平均行: {i + 1}")
                        break
            
            # 提取目标股票数据
            if target_row:
                cells = target_row.find_all(['td', 'th'])
                if len(cells) >= 7:
                    # 排名
                    data["同行比较"]["规模排名"] = cells[0].get_text(strip=True)
                    
                    # 市值数据
                    data["公司规模"]["总市值"] = cells[3].get_text(strip=True)
                    data["公司规模"]["流通市值"] = cells[4].get_text(strip=True)
                    data["公司规模"]["营业收入"] = cells[5].get_text(strip=True)
                    data["公司规模"]["净利润"] = cells[6].get_text(strip=True)
                    
                    debug_print(f"✅ 成功提取公司规模数据，排名: {data['同行比较']['规模排名']}")
                else:
                    debug_print(f"⚠️ 公司规模单元格数量不足: {len(cells)}")
            else:
                debug_print(f"❌ 未找到股票 {stock_code} 的公司规模数据行")
            
            # 提取行业平均数据 - 修正偏移
            if industry_avg_row:
                cells = industry_avg_row.find_all(['td', 'th'])
                if len(cells) >= 7:
                    data["行业平均"]["公司规模"] = {
                        "总市值": cells[1].get_text(strip=True),  # 修正：从3改为1
                        "流通市值": cells[2].get_text(strip=True),  # 修正：从4改为2
                        "营业收入": cells[3].get_text(strip=True),
                        "净利润": cells[4].get_text(strip=True)
                    }
                    debug_print("✅ 成功提取公司规模行业平均数据")
                else:
                    debug_print(f"⚠️ 行业平均行单元格数量不足: {len(cells)}")
            else:
                debug_print("❌ 未找到行业平均行")
                    
        except Exception as e:
            debug_print(f"提取公司规模失败: {str(e)}")

    def extract_market_performance(self, soup: BeautifulSoup, data: Dict, stock_code: str):
        """提取市场表现数据"""
        try:
            # 查找市场表现表格 - 修正选择器
            market_section = None
            selectors = [
                'div[class*="scbxcontent"]',  # 修正：从scbj改为scbx
                'div[class*="market"]',
                'div[class*="表现"]',
                'table[class*="scbx"]',
                'table[class*="market"]',
                '.section.scbx',  # 修正：从scbj改为scbx
                '.section.market'
            ]
            
            for selector in selectors:
                market_section = soup.select_one(selector)
                if market_section:
                    debug_print(f"✅ 使用选择器 '{selector}' 找到市场表现表格")
                    break
            
            if not market_section:
                # 如果还是找不到，尝试查找包含"市场表现"文本的div
                market_section = soup.find('div', string=lambda text: text and '市场表现' in text)
                if market_section:
                    debug_print("✅ 通过文本内容找到市场表现表格")
                    market_section = market_section.parent
                else:
                    debug_print("❌ 未找到市场表现表格")
                    return
            
            debug_print("✅ 找到市场表现表格")
            
            # 查找目标股票行、沪深300行和行业指数行
            target_row = None
            hs300_row = None
            industry_row = None
            
            # 尝试多种方式查找行
            rows = []
            if market_section.find('tr'):
                rows = market_section.find_all('tr')
            elif market_section.find('tbody'):
                rows = market_section.find('tbody').find_all('tr')
            elif market_section.find('table'):
                rows = market_section.find('table').find_all('tr')
            
            debug_print(f"🔍 市场表现表格共有 {len(rows)} 行数据")
            
            for i, row in enumerate(rows):
                cells = row.find_all(['td', 'th'])
                if len(cells) > 2:
                    cell_text = cells[1].get_text(strip=True)
                    debug_print(f"  行{i}: 股票代码/名称 = '{cell_text}'")
                    
                    if i == 1:
                        target_row = row
                        debug_print(f"  ✅ 找到目标股票行: {i}")
                    elif i == 2:
                        hs300_row = row
                        debug_print(f"  ✅ 找到沪深300行: {i}")
                    elif i == 3:
                        industry_row = row
                        debug_print(f"  ✅ 找到行业指数行: {i}")
            
            # 提取目标股票数据
            if target_row:
                cells = target_row.find_all(['td', 'th'])
                if len(cells) >= 6:
                    data["市场表现"]["目标股票"] = {
                        "最近1个月涨跌幅": self.clean_percentage(cells[2].get_text(strip=True)),
                        "最近3个月涨跌幅": self.clean_percentage(cells[3].get_text(strip=True)),
                        "最近6个月涨跌幅": self.clean_percentage(cells[4].get_text(strip=True)),
                        "今年以来涨跌幅": self.clean_percentage(cells[5].get_text(strip=True))
                    }
                    debug_print("✅ 成功提取目标股票市场表现数据")
                else:
                    debug_print(f"⚠️ 目标股票市场表现单元格数量不足: {len(cells)}")
            else:
                debug_print(f"❌ 未找到股票 {stock_code} 的市场表现数据行")
            
            # 提取沪深300数据
            if hs300_row:
                cells = hs300_row.find_all(['td', 'th'])
                if len(cells) >= 6:
                    data["市场表现"]["沪深300指数"] = {
                        "最近1个月涨跌幅": self.clean_percentage(cells[2].get_text(strip=True)),
                        "最近3个月涨跌幅": self.clean_percentage(cells[3].get_text(strip=True)),
                        "最近6个月涨跌幅": self.clean_percentage(cells[4].get_text(strip=True)),
                        "今年以来涨跌幅": self.clean_percentage(cells[5].get_text(strip=True))
                    }
                    debug_print("✅ 成功提取沪深300市场表现数据")
                else:
                    debug_print(f"⚠️ 沪深300市场表现单元格数量不足: {len(cells)}")
            else:
                debug_print("❌ 未找到沪深300行")
            
            # 提取行业指数数据
            if industry_row:
                cells = industry_row.find_all(['td', 'th'])
                if len(cells) >= 6:
                    data["市场表现"]["行业指数"] = {
                        "最近1个月涨跌幅": self.clean_percentage(cells[2].get_text(strip=True)),
                        "最近3个月涨跌幅": self.clean_percentage(cells[3].get_text(strip=True)),
                        "最近6个月涨跌幅": self.clean_percentage(cells[4].get_text(strip=True)),
                        "今年以来涨跌幅": self.clean_percentage(cells[5].get_text(strip=True))
                    }
                    debug_print("✅ 成功提取行业指数市场表现数据")
                else:
                    debug_print(f"⚠️ 行业指数市场表现单元格数量不足: {len(cells)}")
            else:
                debug_print("❌ 未找到行业指数行")
                        
        except Exception as e:
            debug_print(f"提取市场表现失败: {str(e)}")

    def clean_number(self, text: str) -> str:
        """清理数字文本"""
        if not text or text == '--':
            return '0'
        return text.replace(',', '').strip()

    def clean_percentage(self, text: str) -> str:
        """清理百分比文本"""
        if not text or text == '--':
            return '0%'
        return text.replace(',', '').strip()

    def get_stock_peer_data(self, stock_code: str) -> Dict[str, Any]:
        """
        获取股票同行比较数据的主方法
        
        Args:
            stock_code: 股票代码
            
        Returns:
            同行比较数据
        """
        return self.get_peer_comparison_data(stock_code)


def main():
    """主函数"""
    if len(sys.argv) != 2:
        debug_print("使用方法: python EastMoneyPeerComparison.py <股票代码>")
        debug_print("示例: python EastMoneyPeerComparison.py 688333")
        sys.exit(1)
    
    stock_code = sys.argv[1]
    
    # 创建实例并获取数据
    extractor = EastMoneyPeerComparison()
    result = extractor.get_stock_peer_data(stock_code)
    
    # 输出结果 - 始终返回对象格式
    if result["success"]:
        # 确保data是对象格式，如果不是则包装成对象
        data = result["data"]
        if isinstance(data, dict):
            output = {
                "stockCode": stock_code,
                "success": True,
                "data": data
            }
        elif isinstance(data, list):
            # 如果data是数组，包装成字典
            output = {
                "stockCode": stock_code,
                "success": True,
                "data": {"listData": data}
            }
        else:
            # 如果data不是字典也不是数组，包装成字典
            output = {
                "stockCode": stock_code,
                "success": True,
                "data": {"rawData": data} if data is not None else {}
            }
        # 只输出JSON到标准输出
        original_print(json.dumps(output, ensure_ascii=False, indent=2))
    else:
        # 失败时也返回对象格式
        output = {
            "stockCode": stock_code,
            "success": False,
            "error": result.get("error", "未知错误"),
            "data": {}
        }
        # 只输出JSON到标准输出
        original_print(json.dumps(output, ensure_ascii=False, indent=2))
        sys.exit(1)


if __name__ == "__main__":
    main()
