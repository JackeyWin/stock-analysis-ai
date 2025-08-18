#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
东方财富股票资讯、公告、研报爬取和情感分析脚本 - 使用API接口版本
"""

import sys
import json
import time
import re
import random
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Tuple
import requests
from bs4 import BeautifulSoup
import os

class EastMoneyStockNews:
    def __init__(self, deepseek_api_key: str):
        """
        初始化股票资讯爬取器
        
        Args:
            deepseek_api_key: DeepSeek API密钥
        """
        self.deepseek_api_key = deepseek_api_key
        self.deepseek_url = "https://api.deepseek.com/v1/chat/completions"
        self.deepseek_model = "deepseek-chat"  # DeepSeek V3
        
        # 用户代理池
        self.user_agents = [
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36',
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0',
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Edge/119.0.0.0',
            'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
        ]
        
        # 请求头池
        self.headers_pool = [
            {
                'Accept': 'application/json, text/plain, */*',
                'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
                'Accept-Encoding': 'gzip, deflate, br',
                'Connection': 'keep-alive',
                'Referer': 'https://data.eastmoney.com/',
                'Sec-Fetch-Dest': 'empty',
                'Sec-Fetch-Mode': 'cors',
                'Sec-Fetch-Site': 'same-site',
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
            },
            {
                'Accept': 'application/json, text/plain, */*',
                'Accept-Language': 'zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3',
                'Accept-Encoding': 'gzip, deflate',
                'Connection': 'keep-alive',
                'Referer': 'https://data.eastmoney.com/',
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0'
            }
        ]
    
    def _get_random_user_agent(self) -> str:
        """获取随机用户代理"""
        return random.choice(self.user_agents)
    
    def _get_random_headers(self) -> Dict[str, str]:
        """获取随机请求头"""
        headers = random.choice(self.headers_pool).copy()
        headers['User-Agent'] = self._get_random_user_agent()
        return headers
        
    def get_stock_news_data(self, stock_code: str) -> Dict:
        """
        通过API接口获取股票的资讯、公告、研报数据
        
        Args:
            stock_code: 股票代码
            
        Returns:
             包含资讯、公告、研报的字典
        """
        max_retries = 3
        retry_delay = 2
        
        for attempt in range(max_retries):
            try:
                print(f"尝试获取股票资讯数据，第 {attempt + 1} 次...", file=sys.stderr)
                
                # 获取资讯数据
                print("正在获取资讯数据...", file=sys.stderr)
                news_data = self._get_news_from_api(stock_code)
                
                # 获取公告数据
                print("正在获取公告数据...", file=sys.stderr)
                notices_data = self._get_notices_from_api(stock_code)
                
                # 获取研报数据
                print("正在获取研报数据...", file=sys.stderr)
                reports_data = self._get_reports_from_api(stock_code)
                
                # 合并结果
                result = {
                    'stockCode': stock_code,
                    'timestamp': int(time.time() * 1000),
                    'news': news_data,
                    'notices': notices_data,
                    'reports': reports_data
                }
                
                # 验证是否获取到数据
                total_items = len(result['news']) + len(result['notices']) + len(result['reports'])
                if total_items == 0:
                    raise Exception("所有API都未能获取到数据")
                
                print(f"✅ 成功获取数据: {len(result['news'])} 条资讯, {len(result['notices'])} 条公告, {len(result['reports'])} 条研报", file=sys.stderr)
                return result
                    
            except Exception as e:
                print(f"第 {attempt + 1} 次尝试失败: {e}", file=sys.stderr)
                
                if attempt < max_retries - 1:
                    print(f"等待 {retry_delay} 秒后重试...", file=sys.stderr)
                    time.sleep(retry_delay)
                    retry_delay *= 2  # 指数退避
                else:
                    print(f"所有重试都失败了，无法获取股票资讯数据", file=sys.stderr)
                    return None
        
        return None
    
    def _get_news_from_api(self, stock_code: str) -> List[Dict]:
        """通过API获取资讯数据"""
        try:
            # 资讯API接口
            url = "https://np-listapi.eastmoney.com/comm/web/getListInfo"
            params = {
                'client': 'web',
                'biz': 'web_voice',
                'mTypeAndCode': f'0.{stock_code}',  # 格式：0.002241
                'pageSize': 50,  # 增加页面大小
                'type': 1,
                'req_trace': self._generate_trace_id()
            }
            
            print(f"🔍 资讯API请求参数: {params}", file=sys.stderr)
            
            headers = self._get_random_headers()
            
            response = requests.get(url, params=params, headers=headers, timeout=30)
            response.raise_for_status()
            
            data = response.json()
            
            print(f"🔍 资讯API响应: {data}", file=sys.stderr)
            
            if data.get('code') == 1 and 'data' in data:
                if data['data'] is None:
                    print("⚠️ 资讯API返回data为None，可能该股票暂无资讯数据", file=sys.stderr)
                    return []
                
                if 'list' in data['data'] and data['data']['list']:
                    news_list = []
                    for item in data['data']['list']:
                        news_list.append({
                            'title': item.get('Art_Title', ''),
                            'link': item.get('Art_Url', ''),
                            'date': item.get('Art_ShowTime', '')[:10] if item.get('Art_ShowTime') else '',
                            'timeStr': item.get('Art_ShowTime', ''),
                            'artCode': item.get('Art_Code', ''),
                            'source': item.get('Np_dst', '')
                        })
                    
                    print(f"✅ 成功获取 {len(news_list)} 条资讯", file=sys.stderr)
                    return news_list
                else:
                    print("⚠️ 资讯API返回的list为空", file=sys.stderr)
                    return []
            else:
                print(f"⚠️ 资讯API返回异常: {data}", file=sys.stderr)
                return []
                
        except Exception as e:
            print(f"获取资讯数据失败: {e}", file=sys.stderr)
            return []
    
    def _get_notices_from_api(self, stock_code: str) -> List[Dict]:
        """通过API获取公告数据"""
        try:
            # 公告API接口
            url = "https://np-anotice-stock.eastmoney.com/api/security/ann"
            params = {
                'sr': -1,
                'page_size': 50,  # 增加页面大小
                'page_index': 1,
                'ann_type': 'A',
                'client_source': 'web',
                'stock_list': stock_code,
                'f_node': 0,
                's_node': 0
            }
            
            headers = self._get_random_headers()
            
            response = requests.get(url, params=params, headers=headers, timeout=30)
            response.raise_for_status()
            
            data = response.json()
            
            if data.get('success') == 1 and 'data' in data and 'list' in data['data']:
                notices_list = []
                for item in data['data']['list']:
                    notices_list.append({
                        'title': item.get('title', ''),
                        'link': f"https://np-anotice-stock.eastmoney.com/api/security/ann?art_code={item.get('art_code', '')}",
                        'date': item.get('notice_date', '')[:10] if item.get('notice_date') else '',
                        'timeStr': item.get('display_time', ''),
                        'artCode': item.get('art_code', ''),
                        'columnName': item.get('columns', [{}])[0].get('column_name', '') if item.get('columns') else ''
                    })
                
                print(f"✅ 成功获取 {len(notices_list)} 条公告", file=sys.stderr)
                return notices_list
            else:
                print(f"⚠️ 公告API返回异常: {data}", file=sys.stderr)
                return []
                
        except Exception as e:
            print(f"获取公告数据失败: {e}", file=sys.stderr)
            return []
    
    def _get_reports_from_api(self, stock_code: str) -> List[Dict]:
        """通过API获取研报数据"""
        try:
            # 研报API接口
            url = "https://reportapi.eastmoney.com/report/list"
            
            # 计算时间范围（最近一年）
            end_time = datetime.now()
            begin_time = end_time - timedelta(days=365)
            
            params = {
                'pageNo': 1,
                'pageSize': 50,  # 增加页面大小
                'code': stock_code,
                'industryCode': '*',
                'industry': '*',
                'rating': '*',
                'ratingchange': '*',
                'beginTime': begin_time.strftime('%Y-%m-%d'),
                'endTime': end_time.strftime('%Y-%m-%d'),
                'fields': '',
                'qType': 0,
                'sort': 'publishDate,desc'
            }
            
            headers = self._get_random_headers()
            
            response = requests.get(url, params=params, headers=headers, timeout=30)
            response.raise_for_status()
            
            data = response.json()
            
            if 'data' in data and isinstance(data['data'], list):
                reports_list = []
                for item in data['data']:
                    reports_list.append({
                        'title': item.get('title', ''),
                        'link': f"https://reportapi.eastmoney.com/report/detail?infoCode={item.get('infoCode', '')}",
                        'date': item.get('publishDate', '')[:10] if item.get('publishDate') else '',
                        'timeStr': item.get('publishDate', ''),
                        'orgName': item.get('orgSName', ''),
                        'rating': item.get('emRatingName', ''),
                        'researcher': item.get('researcher', ''),
                        'targetPrice': item.get('indvAimPriceT', '') or item.get('indvAimPriceL', '')
                    })
                
                print(f"✅ 成功获取 {len(reports_list)} 条研报", file=sys.stderr)
                return reports_list
            else:
                print(f"⚠️ 研报API返回异常: {data}", file=sys.stderr)
                return []
                
        except Exception as e:
            print(f"获取研报数据失败: {e}", file=sys.stderr)
            return []
    
    def _generate_trace_id(self) -> str:
        """生成请求追踪ID"""
        import hashlib
        timestamp = str(int(time.time() * 1000))
        random_str = str(random.randint(1000, 9999))
        combined = timestamp + random_str
        return hashlib.md5(combined.encode()).hexdigest()
    
    def filter_recent_week(self, data: Dict) -> Dict:
        """
        过滤出最近一周的数据
        
        Args:
            data: 原始数据
            
        Returns:
             过滤后的数据
        """
        try:
            # 计算一周前的日期
            week_ago = datetime.now() - timedelta(days=7)
            week_ago_str = week_ago.strftime('%Y-%m-%d')
            
            filtered_data = {
                'stockCode': data['stockCode'],
                'timestamp': data['timestamp'],
                'news': [],
                'notices': [],
                'reports': []
            }
            
            # 过滤资讯
            for news in data.get('news', []):
                if news.get('date') and news['date'] >= week_ago_str:
                    filtered_data['news'].append(news)
            
            # 过滤公告
            for notice in data.get('notices', []):
                if notice.get('date') and notice['date'] >= week_ago_str:
                    filtered_data['notices'].append(notice)
            
            # 过滤研报
            for report in data.get('reports', []):
                if report.get('date') and report['date'] >= week_ago_str:
                    filtered_data['reports'].append(report)
            
            return filtered_data
            
        except Exception as e:
            print(f"过滤最近一周数据失败: {e}", file=sys.stderr)
            return data
    
    def get_news_content(self, url: str) -> str:
        """
        获取新闻内容
        
        Args:
            url: 新闻链接
            
        Returns:
             新闻内容
        """
        max_retries = 3
        retry_delay = 1
        
        for attempt in range(max_retries):
            try:
                if attempt > 0:
                    print(f"重试获取新闻内容，第 {attempt + 1} 次...", file=sys.stderr)
                
                # 使用随机请求头
                headers = self._get_random_headers()
                
                # 添加随机延时
                time.sleep(random.uniform(0.5, 2.0))
                
                response = requests.get(url, headers=headers, timeout=20)
                response.raise_for_status()
                response.encoding = 'utf-8'
                
                soup = BeautifulSoup(response.text, 'html.parser')
                
                # 尝试多种方式提取内容
                content = ""
                
                # 方法1: 查找常见的新闻内容容器
                content_selectors = [
                    '.newsContent',
                    '.article-content',
                    '.content',
                    '.main-content',
                    '.article-body',
                    '.news-body',
                    '.detail-content',
                    '.article-detail',
                    '.news-detail',
                    '.post-content',
                    '.entry-content',
                    '.story-body',
                    '.article-text',
                    '.news-text'
                ]
                
                for selector in content_selectors:
                    content_elem = soup.select_one(selector)
                    if content_elem:
                        content = content_elem.get_text(strip=True)
                        print(f"使用选择器 '{selector}' 成功提取内容", file=sys.stderr)
                        break
                
                # 方法2: 如果没有找到，尝试查找所有段落
                if not content:
                    paragraphs = soup.find_all('p')
                    if paragraphs:
                        # 过滤掉太短的段落
                        valid_paragraphs = [p.get_text(strip=True) for p in paragraphs if len(p.get_text(strip=True)) > 20]
                        if valid_paragraphs:
                            content = '\n'.join(valid_paragraphs)
                            print("使用段落标签提取内容", file=sys.stderr)
                
                # 方法3: 如果还是没有，使用body内容
                if not content:
                    body = soup.find('body')
                    if body:
                        # 移除脚本和样式标签
                        for script in body(["script", "style", "nav", "header", "footer"]):
                            script.decompose()
                        content = body.get_text(strip=True)
                        print("使用body标签提取内容", file=sys.stderr)
                
                # 清理内容
                if content:
                    # 移除多余的空白字符
                    content = re.sub(r'\s+', ' ', content)
                    # 移除一些常见的无用内容
                    content = re.sub(r'分享到.*?微信', '', content, flags=re.DOTALL)
                    content = re.sub(r'相关阅读.*', '', content, flags=re.DOTALL)
                    content = re.sub(r'点击查看.*', '', content, flags=re.DOTALL)
                    # 限制长度
                    if len(content) > 2000:
                        content = content[:2000] + "..."
                    
                    print(f"成功提取内容，长度: {len(content)} 字符", file=sys.stderr)
                    return content
                else:
                    raise Exception("无法提取到任何内容")
                    
            except Exception as e:
                print(f"第 {attempt + 1} 次获取新闻内容失败 {url}: {e}", file=sys.stderr)
                
                if attempt < max_retries - 1:
                    print(f"等待 {retry_delay} 秒后重试...", file=sys.stderr)
                    time.sleep(retry_delay)
                    retry_delay *= 1.5  # 轻微指数退避
                else:
                    print(f"所有重试都失败了，无法获取新闻内容", file=sys.stderr)
                    return ""
        
        return ""
    
    def analyze_sentiment(self, content: str, title: str = "") -> Dict:
        """
        使用DeepSeek进行情感分析
        
        Args:
            content: 新闻内容
            title: 新闻标题
            
        Returns:
             情感分析结果
        """
        try:
            if not content:
                return {
                    'sentiment': 'neutral',
                    'score': 0,
                    'confidence': 0,
                    'reason': '内容为空'
                }
            
            # 构建提示词
            prompt = f"""
请分析以下股票相关新闻的情感倾向，判断是正面、负面还是中性：

标题：{title}
内容：{content}

请从以下几个方面进行分析：
1. 对股票价格的影响（利好/利空/中性）
2. 对公司基本面的影响
3. 对行业的影响
4. 市场情绪的影响

请给出：
1. 情感倾向：正面/负面/中性
2. 情感强度：0-10分（0最负面，10最正面）
3. 置信度：0-100%
4. 分析理由：简要说明判断依据

请用JSON格式返回结果：
{{
    "sentiment": "正面/负面/中性",
    "score": 0-10的分数,
    "confidence": 0-100的置信度,
    "reason": "分析理由"
}}
"""
            
            # 调用DeepSeek API - 参考EasyMoneyNewsData.py的实现
            headers = {"Authorization": f"Bearer {self.deepseek_api_key}", "Content-Type": "application/json"}
            payload = {
                "model": self.deepseek_model,
                "messages": [
                    {"role": "system", "content": "仅输出JSON格式，不要任何解释。"},
                    {"role": "user", "content": prompt},
                ],
                "temperature": 0.3,
                "max_tokens": 500,
                "top_p": 0.9,
                "frequency_penalty": 0.0,
                "presence_penalty": 0.0,
            }
            
            # 重试机制
            max_retries = 3
            retry_delay = 2
            
            for attempt in range(max_retries):
                try:
                    if attempt > 0:
                        print(f"重试情感分析，第 {attempt + 1} 次...", file=sys.stderr)
                    
                    response = requests.post(self.deepseek_url, headers=headers, json=payload, timeout=30)
                    response.raise_for_status()
                    response_data = response.json()
                    response_text = response_data["choices"][0]["message"]["content"].strip()
                    
                    # 尝试提取JSON
                    try:
                        # 查找JSON部分
                        json_match = re.search(r'\{.*\}', response_text, re.DOTALL)
                        if json_match:
                            result = json.loads(json_match.group())
                            print(f"情感分析成功，情感倾向: {result.get('sentiment', 'unknown')}", file=sys.stderr)
                            return result
                        else:
                            # 如果没有找到JSON，尝试手动解析
                            print("未找到JSON格式，尝试手动解析...", file=sys.stderr)
                            return self._parse_sentiment_response(response_text)
                    except json.JSONDecodeError as e:
                        print(f"JSON解析失败: {e}，尝试手动解析...", file=sys.stderr)
                        return self._parse_sentiment_response(response_text)
                        
                except Exception as e:
                    print(f"DeepSeek API调用失败 第{attempt + 1}次: {e}", file=sys.stderr)
                    
                    if attempt < max_retries - 1:
                        print(f"等待 {retry_delay} 秒后重试...", file=sys.stderr)
                        time.sleep(retry_delay)
                        retry_delay *= 2  # 指数退避
                    else:
                        # 兜底：返回中性结果
                        print("所有重试都失败了，返回默认中性结果", file=sys.stderr)
                        return {
                            'sentiment': 'neutral',
                            'score': 5,
                            'confidence': 0,
                            'reason': f'API调用失败-默认中性: {str(e)}'
                        }
                
        except Exception as e:
            print(f"情感分析失败: {e}", file=sys.stderr)
            return {
                'sentiment': 'neutral',
                'score': 5,
                'confidence': 0,
                'reason': f'分析失败: {str(e)}'
            }
    
    def _parse_sentiment_response(self, response_text: str) -> Dict:
        """
        手动解析情感分析响应
        
        Args:
            response_text: 响应文本
            
        Returns:
             解析后的结果
        """
        try:
            result = {
                'sentiment': 'neutral',
                'score': 5,
                'confidence': 50,
                'reason': response_text
            }
            
            # 尝试提取情感倾向
            if '正面' in response_text or '利好' in response_text or '积极' in response_text:
                result['sentiment'] = 'positive'
            elif '负面' in response_text or '利空' in response_text or '消极' in response_text:
                result['sentiment'] = 'negative'
            
            # 尝试提取分数
            score_match = re.search(r'(\d+)(?:\s*分|\s*分)')
            if score_match:
                score = int(score_match.group(1))
                if 0 <= score <= 10:
                    result['score'] = score
            
            # 尝试提取置信度
            confidence_match = re.search(r'置信度[：:]\s*(\d+)%?')
            if confidence_match:
                confidence = int(confidence_match.group(1))
                if 0 <= confidence <= 100:
                    result['confidence'] = confidence
            
            return result
            
        except Exception as e:
            print(f"手动解析情感分析响应失败: {e}", file=sys.stderr)
            return {
                'sentiment': 'neutral',
                'score': 5,
                'confidence': 0,
                'reason': response_text
            }
    
    def analyze_all_news(self, data: Dict) -> Dict:
        """
        分析所有新闻的情感
        
        Args:
            data: 新闻数据
            
        Returns:
             包含情感分析结果的数据
        """
        try:
            analyzed_data = {
                'stockCode': data['stockCode'],
                'timestamp': data['timestamp'],
                'news': [],
                'notices': [],
                'reports': []
            }
            
            # 分析资讯
            print("正在分析资讯情感...", file=sys.stderr)
            for news in data.get('news', []):
                print(f"分析资讯: {news['title']}", file=sys.stderr)
                
                # 获取内容
                content = self.get_news_content(news['link'])
                
                # 情感分析
                sentiment = self.analyze_sentiment(content, news['title'])
                
                analyzed_news = news.copy()
                analyzed_news['content'] = content
                analyzed_news['sentiment'] = sentiment
                
                analyzed_data['news'].append(analyzed_news)
                
                # 避免请求过于频繁
                time.sleep(1)
            
            # 分析公告
            print("正在分析公告情感...", file=sys.stderr)
            for notice in data.get('notices', []):
                print(f"分析公告: {notice['title']}", file=sys.stderr)
                
                # 获取内容
                content = self.get_news_content(notice['link'])
                
                # 情感分析
                sentiment = self.analyze_sentiment(content, notice['title'])
                
                analyzed_notice = notice.copy()
                analyzed_notice['content'] = content
                analyzed_notice['sentiment'] = sentiment
                
                analyzed_data['notices'].append(analyzed_notice)
                
                # 避免请求过于频繁
                time.sleep(1)
            
            # 分析研报
            print("正在分析研报情感...", file=sys.stderr)
            for report in data.get('reports', []):
                print(f"分析研报: {report['title']}", file=sys.stderr)
                
                # 获取内容
                content = self.get_news_content(report['link'])
                
                # 情感分析
                sentiment = self.analyze_sentiment(content, report['title'])
                
                analyzed_report = report.copy()
                analyzed_report['content'] = content
                analyzed_report['sentiment'] = sentiment
                
                analyzed_data['reports'].append(analyzed_report)
                
                # 避免请求过于频繁
                time.sleep(1)
            
            return analyzed_data
            
        except Exception as e:
            print(f"分析所有新闻情感失败: {e}", file=sys.stderr)
            return data

def main():
    """主函数"""
    if len(sys.argv) < 2:
        print("使用方法: python EastMoneyStockNews.py <股票代码> [DeepSeek_API_Key]", file=sys.stderr)
        print("示例: python EastMoneyStockNews.py 688333", file=sys.stderr)
        sys.exit(1)
    
    stock_code = sys.argv[1]
    
    # 获取API密钥
    deepseek_api_key = os.getenv('DEEPSEEK_API_KEY')
    if len(sys.argv) > 2:
        deepseek_api_key = sys.argv[2]
    
    # 如果没有API密钥，只测试数据爬取
    if not deepseek_api_key:
        print("⚠️ 未设置DEEPSEEK_API_KEY，将只测试数据爬取功能", file=sys.stderr)
        deepseek_api_key = "test_key"  # 使用测试密钥
    
    try:
        # 创建爬取器
        news_crawler = EastMoneyStockNews(deepseek_api_key)
        
        print(f"开始获取股票 {stock_code} 的资讯数据...", file=sys.stderr)
        
        # 获取原始数据
        raw_data = news_crawler.get_stock_news_data(stock_code)
        if not raw_data:
            print("获取股票资讯数据失败", file=sys.stderr)
            sys.exit(1)
        
        print(f"获取到 {len(raw_data.get('news', []))} 条资讯, {len(raw_data.get('notices', []))} 条公告, {len(raw_data.get('reports', []))} 条研报", file=sys.stderr)
        
        # 过滤最近一周的数据
        filtered_data = news_crawler.filter_recent_week(raw_data)
        print(f"最近一周: {len(filtered_data.get('news', []))} 条资讯, {len(filtered_data.get('notices', []))} 条公告, {len(filtered_data.get('reports', []))} 条研报", file=sys.stderr)
        
        # 如果没有API密钥，跳过情感分析
        if deepseek_api_key == "test_key":
            print("⚠️ 跳过情感分析（需要有效的DeepSeek API密钥）", file=sys.stderr)
            analyzed_data = filtered_data
        else:
            # 分析情感
            analyzed_data = news_crawler.analyze_all_news(filtered_data)
        
        # 输出结果
        print(json.dumps(analyzed_data, ensure_ascii=False, indent=2))
        
    except Exception as e:
        print(f"程序执行失败: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
