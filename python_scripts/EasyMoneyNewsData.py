#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
东方财富股票资讯、公告、研报爬取和情感分析脚本 - 使用API接口版本
保持与原来EasyMoneyNewsData.py相同的接口和输出格式
"""

import json
import time
import random
import sys
import re
import os
import hashlib
import logging
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Tuple
import requests
from bs4 import BeautifulSoup
from concurrent.futures import ThreadPoolExecutor, as_completed
import threading
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

from EastMoneyCoreTags import fetch_core_tags

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# 全局变量声明
global DEEPSEEK_API_KEY, NEWS_SENTIMENT_PROVIDER

DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY")
DEEPSEEK_URL = "https://api.deepseek.com/v1/chat/completions"
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")  # DeepSeek V3
NEWS_SENTIMENT_PROVIDER = os.getenv("NEWS_SENTIMENT_PROVIDER", "keyword").lower()  # keyword | deepseek

_sentiment_cache = {}

# 全局session管理器
class SessionManager:
    """优化的网络连接管理器"""
    
    def __init__(self):
        self._session = None
        self._lock = threading.Lock()
    
    def get_session(self):
        """获取优化的session实例"""
        if self._session is None:
            with self._lock:
                if self._session is None:
                    self._session = self._create_optimized_session()
        return self._session
    
    def _create_optimized_session(self):
        """创建优化的session"""
        session = requests.Session()
        
        # 配置重试策略
        retry_strategy = Retry(
            total=3,
            backoff_factor=1,
            status_forcelist=[429, 500, 502, 503, 504],
            allowed_methods=["HEAD", "GET", "OPTIONS"]
        )
        
        # 配置HTTP适配器
        adapter = HTTPAdapter(
            max_retries=retry_strategy,
            pool_connections=10,  # 连接池大小
            pool_maxsize=20,      # 最大连接数
            pool_block=False
        )
        
        session.mount("http://", adapter)
        session.mount("https://", adapter)
        
        # 设置默认超时
        session.timeout = 30
        
        # 设置默认请求头
        session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Accept': 'application/json, text/plain, */*',
            'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
            'Accept-Encoding': 'gzip, deflate, br',
            'Connection': 'keep-alive',
            'Cache-Control': 'no-cache',
        })
        
        return session

# 全局session管理器实例
session_manager = SessionManager()

def _hash_key(target: str, title: str, snippet: str) -> str:
    h = hashlib.sha256()
    h.update((target or "" + "|" + (title or "") + "|" + (snippet or "")).encode("utf-8"))
    return h.hexdigest()

def _extract_snippet(title: str, content: str, target_name: str, aliases=None, max_len=800) -> str:
    if not content:
        content = ""
    text = (title or "") + "\n" + content
    # 清理URL和多余空白
    text = re.sub(r"https?://\S+", "", text)
    text = re.sub(r"\s+", " ", text).strip()
    # 命中句优先
    sentences = re.split(r"[。！!？?\n]", text)
    keys = [k for k in [target_name] + (aliases or []) if k]
    hits = [s for s in sentences if any(k in s for k in keys)]
    pool = (hits[:3] if hits else sentences[:2])
    snippet = (title or "") + " " + "。".join([s.strip() for s in pool if s.strip()])
    return snippet[:max_len]

def _map_label_cn(label: str) -> str:
    return {"pos": "利好", "neg": "利空", "neu": "中性"}.get((label or "").lower(), "中性")

def _score_from_label(label: str, confidence: float) -> float:
    try:
        c = float(confidence or 0.5)
    except Exception:
        c = 0.5
    if (label or "").lower() == "pos":
        return round(min(max(c, 0.0), 1.0) * 100, 1)
    if (label or "").lower() == "neg":
        return round(-min(max(c, 0.0), 1.0) * 100, 1)
    return 0.0

def _detect_index_signal(text: str) -> str | None:
    if not text:
        return None
    t = text.replace(" ", "")
    hit_index = re.search(r"(指数|ETF|板块|行业|概念)", t)
    if not hit_index:
        return None
    pos = re.search(r"(上涨|上扬|上行|走强|反弹|飙升|攀升|拉升|涨超|涨逾|走高)", t)
    neg = re.search(r"(下跌|下挫|走弱|回落|回撤|暴跌|跌超|跌逾|走低)", t)
    if pos and not neg:
        return "pos"
    if neg and not pos:
        return "neg"
    return None

def classify_news_with_deepseek_batch(target_name: str, items: list, aliases=None, context_tags: list | None = None):
    """
    批量调用 DeepSeek V3 进行相对目标公司的新闻情感分类。
    items: [{"title": str, "content": str}]，返回与items等长的结果列表。
    """
    if not DEEPSEEK_API_KEY:
        raise RuntimeError("缺少DEEPSEEK_API_KEY环境变量")

    # 预处理与缓存命中
    prepared = []
    results = [None] * len(items)
    for idx, it in enumerate(items):
        title = it.get("title", "")
        content = it.get("content", "")
        snippet = _extract_snippet(title, content, target_name, aliases)
        idx_sig = _detect_index_signal(f"{title}\n{snippet}")
        key = _hash_key(target_name, title, snippet)
        if key in _sentiment_cache:
            results[idx] = _sentiment_cache[key]
        prepared.append({"idx": idx, "title": title, "snippet": snippet, "key": key, "idx_sig": idx_sig})

    # 过滤掉已命中的，减少tokens
    pending = [p for p in prepared if results[p["idx"]] is None]
    if not pending:
        return results

    system_prompt = "仅输出JSON数组，不要任何解释。"

    # 构造批量用户提示，严格控制输出
    # 组装少量上下文，控制token
    tags_line = ""
    if context_tags and isinstance(context_tags, list):
        tags_line = "相关板块/概念：" + ", ".join(context_tags[:12])

    lines = [
        "你是金融新闻判定器。对下列新闻针对目标公司进行情感判定，逐条给出结果，数组顺序需与输入一致。",
        "仅输出JSON数组，每项形如：{\"label\":\"pos|neg|neu\",\"confidence\":0-1,\"reason\":\"<=50字\",\"role\":\"self|competitor|supplier|customer|sector|other\"}。",
        "相对性规则：竞争对手利空→目标利好；供应商利空→目标利空；大客户利空→目标利空；行业普遍事件多为中性（除非明显单边）。",
        "若新闻提到指数/ETF/板块上涨，默认与A股个股正相关；若下跌，默认负相关（除非新闻上下文明显相反）。",
        f"目标公司：{target_name}（可能为代码或简称）",
        tags_line,
        "待判定新闻："
    ]
    for p in pending:
        sig = p.get('idx_sig')
        sig_note = "+" if sig == 'pos' else ("-" if sig == 'neg' else "0")
        lines.append(f"- [{p['idx']}] 标题：{p['title']}\n内容：{p['snippet']}\n指:{sig_note}")

    user_prompt = "\n".join(lines)

    headers = {"Authorization": f"Bearer {DEEPSEEK_API_KEY}", "Content-Type": "application/json"}
    # 根据条数估算输出长度
    max_tokens = min(80 * max(1, len(pending)), 800)
    payload = {
        "model": DEEPSEEK_MODEL,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        "temperature": 0.2,
        "max_tokens": max_tokens,
        "top_p": 0.9,
        "frequency_penalty": 0.0,
        "presence_penalty": 0.0,
    }

    # 简单重试
    attempt = 0
    while True:
        attempt += 1
        try:
            resp = requests.post(DEEPSEEK_URL, headers=headers, json=payload, timeout=30)
            resp.raise_for_status()
            content = resp.json()["choices"][0]["message"]["content"].strip()
            try:
                arr = json.loads(content)
            except Exception:
                m = re.search(r"\[[\s\S]*\]", content)
                if not m:
                    raise ValueError("模型未返回JSON数组")
                arr = json.loads(m.group(0))

            # 对齐结果
            for i, p in enumerate(pending):
                one = arr[i] if i < len(arr) else {}
                label = (one.get("label") or "neu").lower()
                confidence = float(one.get("confidence", 0.5))
                reason = (one.get("reason") or "")[:60]
                role = one.get("role", "other")
                mapped = {
                    "label": label,
                    "confidence": round(confidence, 3),
                    "reason": reason,
                    "role": role,
                }
                results[p["idx"]] = mapped
                _sentiment_cache[p["key"]] = mapped
            break
        except Exception as e:
            logger.warning(f"DeepSeek批量判定失败 第{attempt}次: {e}")
            if attempt >= 3:
                # 兜底：全部中性
                for p in pending:
                    mapped = {"label": "neu", "confidence": 0.5, "reason": "判定失败-默认中性", "role": "other"}
                    results[p["idx"]] = mapped
                    _sentiment_cache[p["key"]] = mapped
                break
            time.sleep(1.5 * attempt)

    return results

def analyze_news_sentiment(title: str, content: str, summary: str) -> Dict:
    """
    基于关键词规则的情感分析（兜底方案）
    """
    # 利好关键词
    positive_keywords = [
        "上涨", "涨停", "大涨", "飙升", "暴涨", "走强", "强势", "利好", "好消息", "突破",
        "增长", "增长", "盈利", "利润", "业绩", "营收", "收入", "订单", "合同", "合作",
        "收购", "并购", "投资", "融资", "上市", "IPO", "创新", "技术", "专利", "研发",
        "扩张", "发展", "战略", "布局", "机遇", "前景", "看好", "推荐", "买入", "增持"
    ]
    
    # 利空关键词
    negative_keywords = [
        "下跌", "跌停", "大跌", "暴跌", "走弱", "弱势", "利空", "坏消息", "破位", "跌破",
        "下滑", "下降", "亏损", "损失", "业绩", "营收", "收入", "订单", "合同", "合作",
        "出售", "剥离", "减持", "退市", "ST", "风险", "问题", "困难", "挑战", "竞争",
        "收缩", "衰退", "危机", "困境", "威胁", "看空", "卖出", "减持", "回避"
    ]
    
    # 合并标题和内容进行关键词匹配
    text = (title or "") + " " + (content or "") + " " + (summary or "")
    text = text.lower()
    
    # 统计关键词出现次数
    positive_matches = [kw for kw in positive_keywords if kw in text]
    negative_matches = [kw for kw in negative_keywords if kw in text]
    
    positive_score = len(positive_matches)
    negative_score = len(negative_matches)
    total_keywords = positive_score + negative_score
    
    if total_keywords == 0:
        sentiment_score = 0  # 中性
        sentiment_label = "中性"
    else:
        # 基础评分
        raw_score = (positive_score - negative_score) / total_keywords * 100

        # 根据关键词强度调整
        if positive_score > negative_score * 2:
            sentiment_score = min(raw_score * 1.2, 100)
        elif negative_score > positive_score * 2:
            sentiment_score = max(raw_score * 1.2, -100)
        else:
            sentiment_score = raw_score

        # 分类标签
        if sentiment_score >= 30:
            sentiment_label = "利好"
        elif sentiment_score <= -30:
            sentiment_label = "利空"
        else:
            sentiment_label = "中性"

    return {
        "sentiment_score": round(sentiment_score, 1),
        "sentiment_label": sentiment_label,
        "positive_keywords": positive_matches[:5],  # 最多显示5个
        "negative_keywords": negative_matches[:5],  # 最多显示5个
        "analysis_summary": f"检测到{len(positive_matches)}个利好关键词，{len(negative_matches)}个利空关键词"
    }

def get_news_content(url: str, max_retries: int = 3) -> str:
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
                logger.info(f"重试获取新闻内容，第 {attempt + 1} 次...")
            
            # 使用优化的session
            session = session_manager.get_session()
            
            # 为这个请求添加特定的请求头
            headers = {
                'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
                'Upgrade-Insecure-Requests': '1',
            }
            
            # 添加随机延时
            time.sleep(random.uniform(0.5, 2.0))
            
            response = session.get(url, headers=headers, timeout=20)
            response.raise_for_status()
            response.encoding = 'utf-8'
            
            soup = BeautifulSoup(response.text, 'html.parser')
            
            # 尝试多种方式提取内容
            content = ""
            
            # 方法1: 查找常见的新闻内容容器
            content_selectors = [
                '.newsContent',
                '.notice_content',
                '.ctx_content',
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
                    logger.info(f"使用选择器 '{selector}' 成功提取内容")
                    break
            
            # 方法2: 如果没有找到，尝试查找所有段落
            if not content:
                paragraphs = soup.find_all('p')
                if paragraphs:
                    # 过滤掉太短的段落
                    valid_paragraphs = [p.get_text(strip=True) for p in paragraphs if len(p.get_text(strip=True)) > 20]
                    if valid_paragraphs:
                        content = '\n'.join(valid_paragraphs)
                        logger.info("使用段落标签提取内容")
            
            # 方法3: 如果还是没有，使用body内容
            if not content:
                body = soup.find('body')
                if body:
                    # 移除脚本和样式标签
                    for script in body(["script", "style", "nav", "header", "footer"]):
                        script.decompose()
                    content = body.get_text(strip=True)
                    logger.info("使用body标签提取内容")
            
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
                
                logger.info(f"成功提取内容，长度: {len(content)} 字符")
                return content
            else:
                raise Exception("无法提取到任何内容")
                
        except Exception as e:
            logger.warning(f"第 {attempt + 1} 次获取新闻内容失败 {url}: {e}")
            
            if attempt < max_retries - 1:
                logger.info(f"等待 {retry_delay} 秒后重试...")
                time.sleep(retry_delay)
                retry_delay *= 1.5  # 轻微指数退避
            else:
                logger.error(f"所有重试都失败了，无法获取新闻内容")
                return ""
    
    return ""

def get_news_content_batch(news_items: List[Dict], max_workers: int = 5) -> List[Dict]:
    """
    批量多线程获取新闻内容
    
    Args:
        news_items: 新闻项目列表，每个项目包含链接等信息
        max_workers: 最大线程数
        
    Returns:
        包含内容的新闻项目列表
    """
    # 线程安全的锁
    lock = threading.Lock()
    results = {}
    
    def fetch_single_content(item_with_index):
        """获取单条新闻内容的线程函数"""
        index, item = item_with_index
        try:
            # 添加随机延时避免过于频繁的请求
            time.sleep(random.uniform(0.2, 0.8))
            
            content = get_news_content(item["链接"], max_retries=2)
            
            # 线程安全地存储结果
            with lock:
                results[index] = {**item, "content": content}
                logger.info(f"线程完成第 {index + 1} 条新闻内容获取: {item['新闻标题'][:30]}...")
                
        except Exception as e:
            logger.warning(f"线程获取第 {index + 1} 条新闻内容失败: {e}")
            with lock:
                results[index] = {**item, "content": ""}
    
    # 使用线程池执行
    logger.info(f"开始多线程批量获取 {len(news_items)} 条新闻内容，使用 {max_workers} 个线程")
    
    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        # 提交所有任务
        futures = []
        for index, item in enumerate(news_items):
            future = executor.submit(fetch_single_content, (index, item))
            futures.append(future)
        
        # 等待所有任务完成
        for future in as_completed(futures):
            try:
                future.result()  # 获取结果，如果有异常会抛出
            except Exception as e:
                logger.warning(f"线程执行异常: {e}")
    
    # 按原始顺序返回结果
    ordered_results = []
    for i in range(len(news_items)):
        if i in results:
            ordered_results.append(results[i])
        else:
            # 如果某个索引没有结果，使用原始项目
            ordered_results.append({**news_items[i], "content": ""})
    
    logger.info(f"多线程批量获取完成，成功获取 {len([r for r in ordered_results if r.get('content')])} 条内容")
    return ordered_results

def _get_random_user_agent() -> str:
    """获取随机User-Agent"""
    user_agents = [
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/120.0',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15',
        'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Edge/120.0.0.0',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36',
    ]
    return random.choice(user_agents)

def _get_random_headers() -> Dict[str, str]:
    """获取随机请求头"""
    user_agent = _get_random_user_agent()
    
    # 随机Accept-Language
    accept_languages = [
        'zh-CN,zh;q=0.9,en;q=0.8',
        'zh-CN,zh;q=0.9',
        'en-US,en;q=0.9,zh-CN;q=0.8',
        'zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3',
        'zh-CN,zh;q=0.7,en;q=0.3',
    ]
    
    # 随机Accept-Encoding
    accept_encodings = [
        'gzip, deflate, br',
        'gzip, deflate',
        'br, gzip, deflate',
        'deflate, gzip',
    ]
    
    # 随机Connection
    connections = [
        'keep-alive',
        'close',
    ]
    
    # 随机Referer
    referers = [
        'https://data.eastmoney.com/',
        'https://www.eastmoney.com/',
        'https://quote.eastmoney.com/',
        'https://finance.eastmoney.com/',
        'https://stock.eastmoney.com/',
        'https://www.eastmoney.com/quote/',
    ]
    
    headers = {
        'Accept': 'application/json, text/plain, */*',
        'Accept-Language': random.choice(accept_languages),
        'Accept-Encoding': random.choice(accept_encodings),
        'Connection': random.choice(connections),
        'Referer': random.choice(referers),
        'User-Agent': user_agent,
        'Cache-Control': random.choice(['no-cache', 'max-age=0', 'no-store']),
        'Pragma': random.choice(['no-cache', '']),
        'Sec-Fetch-Dest': 'empty',
        'Sec-Fetch-Mode': 'cors',
        'Sec-Fetch-Site': random.choice(['same-origin', 'cross-site']),
        'X-Requested-With': 'XMLHttpRequest',
        'sec-ch-ua': '"Not_A Brand";v="8", "Chromium";v="120", "Google Chrome";v="120"',
        'sec-ch-ua-mobile': '?0',
        'sec-ch-ua-platform': random.choice(['"Windows"', '"macOS"', '"Linux"']),
    }
    
    # 随机添加一些额外的头部
    if random.random() < 0.3:
        headers['DNT'] = '1'
    if random.random() < 0.3:
        headers['Upgrade-Insecure-Requests'] = '1'
    if random.random() < 0.2:
        headers['Origin'] = 'https://data.eastmoney.com'
    if random.random() < 0.2:
        headers['X-Forwarded-For'] = f'{random.randint(1, 255)}.{random.randint(1, 255)}.{random.randint(1, 255)}.{random.randint(1, 255)}'
    
    return headers

def _get_random_cookies() -> Dict[str, str]:
    """获取随机Cookie"""
    cookies = {}
    
    # 随机添加一些常见的Cookie
    if random.random() < 0.5:
        cookies['HMF_CI'] = f'{random.randint(1000000000, 9999999999)}'
    if random.random() < 0.3:
        cookies['HMF_CI'] = f'{random.randint(1000000000, 9999999999)}'
    if random.random() < 0.4:
        cookies['_ga'] = f'GA1.1.{random.randint(1000000000, 9999999999)}.{int(time.time())}'
    if random.random() < 0.3:
        cookies['_gid'] = f'GA1.1.{random.randint(100000000, 999999999)}.{int(time.time())}'
    
    return cookies

def _random_delay(min_seconds: float = 0.5, max_seconds: float = 2.0):
    """随机延迟"""
    delay = random.uniform(min_seconds, max_seconds)
    time.sleep(delay)

# 请求频率控制
_last_request_time = 0
_min_request_interval = 1.0  # 最小请求间隔（秒）

def _rate_limit():
    """请求频率控制"""
    global _last_request_time
    current_time = time.time()
    time_since_last = current_time - _last_request_time
    
    if time_since_last < _min_request_interval:
        sleep_time = _min_request_interval - time_since_last + random.uniform(0, 0.5)
        time.sleep(sleep_time)
    
    _last_request_time = time.time()

def _get_news_from_api(stock_code: str, max_retries: int = 3) -> List[Dict]:
    """通过API获取资讯数据"""
    cutoff_date = _get_week_ago_date()  # 获取一周前的日期
    
    for attempt in range(max_retries):
        try:
            # 请求频率控制
            _rate_limit()
            
            # 随机延迟
            _random_delay(1.0, 3.0)
            
            # 资讯API接口
            url = "https://np-listapi.eastmoney.com/comm/web/getListInfo"
            
            # 修复mTypeAndCode参数格式 - 根据股票代码前缀确定格式
            if stock_code.startswith('60') or stock_code.startswith('68'):
                # 上海股票（包括科创板）：使用1.600562或1.688297格式
                m_type_and_code = f'1.{stock_code}'
            elif stock_code.startswith('00') or stock_code.startswith('30'):
                # 深圳股票：使用0.002241格式
                m_type_and_code = f'0.{stock_code}'
            else:
                # 其他情况：尝试默认格式
                m_type_and_code = f'0.{stock_code}'

            params = {
                'client': 'web',
                'biz': 'web_voice',
                'mTypeAndCode': m_type_and_code,
                'pageSize': 50,
                'type': 1,
                'req_trace': _generate_trace_id()
            }
            
            # 简化参数，减少反爬虫策略的激进性
            if random.random() < 0.3:
                params['_'] = str(int(time.time() * 1000))
            
            logger.info(f"资讯API请求参数: {params}")
            logger.info(f"股票代码: {stock_code}, mTypeAndCode: {m_type_and_code}")
            
            # 使用优化的session
            session = session_manager.get_session()
            
            # 为这个请求添加特定的请求头
            headers = {
                'Referer': 'https://data.eastmoney.com/',
                'X-Requested-With': 'XMLHttpRequest',
            }
            
            response = session.get(url, params=params, headers=headers, timeout=30)
            response.raise_for_status()
            
            # 调试：打印原始响应内容
            logger.debug(f"原始响应状态码: {response.status_code}")
            logger.debug(f"原始响应头: {dict(response.headers)}")
            logger.debug(f"原始响应内容前200字符: {response.text[:200]}")
            
            # 检查响应内容类型
            content_type = response.headers.get('content-type', '')
            if 'application/json' not in content_type and 'text/html' in content_type:
                logger.warning(f"API返回了HTML内容而不是JSON，可能被反爬虫拦截")
                if attempt < max_retries - 1:
                    retry_delay = (attempt + 1) * 3 + random.uniform(0, 2)  # 增加延迟
                    logger.info(f"第 {attempt + 1} 次尝试失败，等待 {retry_delay:.1f} 秒后重试...")
                    time.sleep(retry_delay)
                    continue
                return []
            
            try:
                data = response.json()
            except json.JSONDecodeError as e:
                logger.error(f"JSON解析失败: {e}")
                logger.error(f"响应内容: {response.text[:500]}")
                if attempt < max_retries - 1:
                    retry_delay = (attempt + 1) * 3 + random.uniform(0, 2)
                    logger.info(f"第 {attempt + 1} 次尝试失败，等待 {retry_delay:.1f} 秒后重试...")
                    time.sleep(retry_delay)
                    continue
                return []
            
            logger.info(f"资讯API响应: {data}")
            
            if data.get('code') == 1 and 'data' in data:
                if data['data'] is None:
                    logger.warning("资讯API返回data为None，可能该股票暂无资讯数据")
                    # 尝试不同的mTypeAndCode格式
                    if attempt == 0 and stock_code.startswith('60'):
                        logger.info("尝试使用深圳股票格式...")
                        params['mTypeAndCode'] = f'0.{stock_code}'
                        continue
                    elif attempt == 1 and stock_code.startswith('00'):
                        logger.info("尝试使用上海股票格式...")
                        params['mTypeAndCode'] = f'1.{stock_code}'
                        continue
                    return []
                
                if 'list' in data['data'] and data['data']['list']:
                    news_list = []
                    for item in data['data']['list']:
                        # 调试：打印原始日期字段
                        raw_date = item.get('Art_ShowTime', '')
                        logger.debug(f"原始日期字段: {raw_date}, 类型: {type(raw_date)}")
                        
                        # 提取日期，确保格式正确
                        date_str = ''
                        if raw_date:
                            try:
                                # 尝试解析完整时间戳
                                if len(raw_date) >= 19:  # YYYY-MM-DD HH:MM:SS
                                    date_str = raw_date[:10]
                                elif len(raw_date) >= 10:  # YYYY-MM-DD
                                    date_str = raw_date[:10]
                                else:
                                    logger.warning(f"日期格式异常: {raw_date}")
                            except Exception as e:
                                logger.warning(f"日期解析失败: {raw_date}, 错误: {e}")
                        
                        news_list.append({
                            'title': item.get('Art_Title', ''),
                            'link': item.get('Art_Url', ''),
                            'date': date_str,
                            'timeStr': item.get('Art_ShowTime', ''),
                            'artCode': item.get('Art_Code', ''),
                            'source': item.get('Np_dst', '')
                        })
                    
                    logger.info(f"API返回 {len(news_list)} 条资讯")
                    
                    # 在API层面进行日期过滤
                    filtered_news = []
                    for item in news_list:
                        # 检查日期字段 - 使用正确的字段名
                        date_field = item.get('date')
                        if date_field and _is_recent_date(str(date_field), cutoff_date):
                            filtered_news.append(item)
                    
                    logger.info(f"日期过滤后剩余 {len(filtered_news)} 条资讯")
                    return filtered_news
                else:
                    logger.warning("资讯API返回的list为空")
                    return []
            else:
                logger.warning(f"资讯API返回异常: {data}")
                if attempt < max_retries - 1:
                    retry_delay = (attempt + 1) * 3 + random.uniform(0, 2)  # 增加延迟
                    logger.info(f"第 {attempt + 1} 次尝试失败，等待 {retry_delay:.1f} 秒后重试...")
                    time.sleep(retry_delay)
                    continue
                return []
                
        except Exception as e:
            logger.error(f"第 {attempt + 1} 次获取资讯数据失败: {e}")
            if attempt < max_retries - 1:
                retry_delay = (attempt + 1) * 3 + random.uniform(0, 2)  # 增加延迟
                logger.info(f"等待 {retry_delay:.1f} 秒后重试...")
                time.sleep(retry_delay)
            else:
                logger.error(f"所有重试都失败了，无法获取资讯数据")
                return []
    
    return []

def _get_notices_from_api(stock_code: str, max_retries: int = 3) -> List[Dict]:
    """通过API获取公告数据"""
    cutoff_date = _get_week_ago_date()  # 获取一周前的日期
    
    for attempt in range(max_retries):
        try:
            # 请求频率控制
            _rate_limit()
            
            # 随机延迟
            _random_delay(1.0, 3.0)
            
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
            
            # 随机添加一些参数
            if random.random() < 0.5:
                params['_'] = str(int(time.time() * 1000))
            if random.random() < 0.3:
                params['callback'] = f'jQuery{random.randint(1000000, 9999999)}'
            if random.random() < 0.4:
                params['t'] = str(int(time.time() * 1000))
            
            # 使用优化的session
            session = session_manager.get_session()
            
            # 为这个请求添加特定的请求头和Cookie
            headers = {
                'Referer': 'https://data.eastmoney.com/',
                'X-Requested-With': 'XMLHttpRequest',
            }
            cookies = _get_random_cookies()
            
            response = session.get(url, params=params, headers=headers, cookies=cookies, timeout=30)
            response.raise_for_status()
            
            data = response.json()
            
            if data.get('success') == 1 and 'data' in data and 'list' in data['data']:
                notices_list = []
                for item in data['data']['list']:
                    # 调试：打印原始日期字段
                    raw_date = item.get('notice_date', '')
                    logger.debug(f"公告原始日期字段: {raw_date}, 类型: {type(raw_date)}")
                    
                    # 提取日期，确保格式正确
                    date_str = ''
                    if raw_date:
                        try:
                            # 尝试解析完整时间戳
                            if len(raw_date) >= 19:  # YYYY-MM-DD HH:MM:SS
                                date_str = raw_date[:10]
                            elif len(raw_date) >= 10:  # YYYY-MM-DD
                                date_str = raw_date[:10]
                            else:
                                logger.warning(f"公告日期格式异常: {raw_date}")
                        except Exception as e:
                            logger.warning(f"公告日期解析失败: {raw_date}, 错误: {e}")
                    
                    notices_list.append({
                        'title': item.get('title', ''),
                        'link': f"https://np-anotice-stock.eastmoney.com/api/security/ann?art_code={item.get('art_code', '')}",
                        'date': date_str,
                        'timeStr': item.get('display_time', ''),
                        'artCode': item.get('art_code', ''),
                        'columnName': item.get('columns', [{}])[0].get('column_name', '') if item.get('columns') else ''
                    })
                
                logger.info(f"API返回 {len(notices_list)} 条公告")
                
                # 在API层面进行日期过滤
                filtered_notices = []
                for item in notices_list:
                    # 检查日期字段 - 使用正确的字段名
                    date_field = item.get('date')
                    if date_field and _is_recent_date(str(date_field), cutoff_date):
                        filtered_notices.append(item)
                
                logger.info(f"日期过滤后剩余 {len(filtered_notices)} 条公告")
                return filtered_notices
            else:
                logger.warning(f"公告API返回异常: {data}")
                if attempt < max_retries - 1:
                    retry_delay = (attempt + 1) * 2 + random.uniform(0, 1)  # 递增延迟+随机
                    logger.info(f"第 {attempt + 1} 次尝试失败，等待 {retry_delay:.1f} 秒后重试...")
                    time.sleep(retry_delay)
                    continue
                return []
                
        except Exception as e:
            logger.error(f"第 {attempt + 1} 次获取公告数据失败: {e}")
            if attempt < max_retries - 1:
                retry_delay = (attempt + 1) * 2 + random.uniform(0, 1)  # 递增延迟+随机
                logger.info(f"等待 {retry_delay:.1f} 秒后重试...")
                time.sleep(retry_delay)
            else:
                logger.error(f"所有重试都失败了，无法获取公告数据")
                return []
    
    return []

def _get_reports_from_api(stock_code: str, max_retries: int = 3) -> List[Dict]:
    """通过API获取研报数据"""
    cutoff_date = _get_week_ago_date()  # 获取一周前的日期
    
    for attempt in range(max_retries):
        try:
            # 随机延迟
            _random_delay(1.0, 3.0)
            
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
            
            # 随机添加一些参数
            if random.random() < 0.5:
                params['_'] = str(int(time.time() * 1000))
            if random.random() < 0.3:
                params['callback'] = f'jQuery{random.randint(1000000, 9999999)}'
            
            # 使用优化的session
            session = session_manager.get_session()
            
            # 为这个请求添加特定的请求头
            headers = {
                'Referer': 'https://data.eastmoney.com/',
                'X-Requested-With': 'XMLHttpRequest',
            }
            
            response = session.get(url, params=params, headers=headers, timeout=30)
            response.raise_for_status()

            data = response.json()
            
            if 'data' in data and isinstance(data['data'], list):
                reports_list = []
                for item in data['data']:
                    # 调试：打印原始日期字段
                    raw_date = item.get('publishDate', '')
                    logger.debug(f"研报原始日期字段: {raw_date}, 类型: {type(raw_date)}")
                    
                    # 提取日期，确保格式正确
                    date_str = ''
                    if raw_date:
                        try:
                            # 尝试解析完整时间戳
                            if len(raw_date) >= 19:  # YYYY-MM-DD HH:MM:SS
                                date_str = raw_date[:10]
                            elif len(raw_date) >= 10:  # YYYY-MM-DD
                                date_str = raw_date[:10]
                            else:
                                logger.warning(f"研报日期格式异常: {raw_date}")
                        except Exception as e:
                            logger.warning(f"研报日期解析失败: {raw_date}, 错误: {e}")
                    
                    reports_list.append({
                        'title': item.get('title', ''),
                        'link': f"https://reportapi.eastmoney.com/report/detail?infoCode={item.get('infoCode', '')}",
                        'date': date_str,
                        'timeStr': item.get('publishDate', ''),
                        'orgName': item.get('orgSName', ''),
                        'rating': item.get('emRatingName', ''),
                        'researcher': item.get('researcher', ''),
                        'targetPrice': item.get('indvAimPriceT', '') or item.get('indvAimPriceL', '')
                    })
                
                logger.info(f"API返回 {len(reports_list)} 条研报")
                
                # 在API层面进行日期过滤
                filtered_reports = []
                for item in reports_list:
                    # 检查日期字段 - 使用正确的字段名
                    date_field = item.get('date')
                    if date_field and _is_recent_date(str(date_field), cutoff_date):
                        filtered_reports.append(item)
                
                logger.info(f"日期过滤后剩余 {len(filtered_reports)} 条研报")
                return filtered_reports
            else:
                logger.warning(f"研报API返回异常: {data}")
                if attempt < max_retries - 1:
                    retry_delay = (attempt + 1) * 2 + random.uniform(0, 1)  # 递增延迟+随机
                    logger.info(f"第 {attempt + 1} 次尝试失败，等待 {retry_delay:.1f} 秒后重试...")
                    time.sleep(retry_delay)
                    continue
                return []
                
        except Exception as e:
            logger.error(f"第 {attempt + 1} 次获取研报数据失败: {e}")
            if attempt < max_retries - 1:
                retry_delay = (attempt + 1) * 2 + random.uniform(0, 1)  # 递增延迟+随机
                logger.info(f"等待 {retry_delay:.1f} 秒后重试...")
                time.sleep(retry_delay)
            else:
                logger.error(f"所有重试都失败了，无法获取研报数据")
                return []
    
    return []

def _generate_trace_id() -> str:
    """生成请求追踪ID"""
    timestamp = str(int(time.time() * 1000))
    random_str = str(random.randint(1000, 9999))
    combined = timestamp + random_str
    return hashlib.md5(combined.encode()).hexdigest()

def _get_week_ago_date() -> str:
    """获取一周前的日期字符串"""
    week_ago = datetime.now() - timedelta(days=6)
    return week_ago.strftime('%Y-%m-%d')

def _is_recent_date(date_str: str, cutoff_date: str) -> bool:
    """检查日期是否在截止日期之后"""
    if not date_str or len(date_str) < 10:
        return True  # 如果日期格式异常，保留该项目
    
    try:
        item_date = datetime.strptime(date_str[:10], '%Y-%m-%d')
        cutoff = datetime.strptime(cutoff_date, '%Y-%m-%d')
        return item_date >= cutoff
    except ValueError:
        return True  # 如果日期解析失败，保留该项目

def filter_recent_week(data: List[Dict]) -> List[Dict]:
    """
    过滤出最近一周的数据
    
    Args:
        data: 原始数据列表
        
    Returns:
         过滤后的数据列表
    """
    try:
        # 计算一周前的日期
        week_ago = datetime.now() - timedelta(days=7)
        week_ago_str = week_ago.strftime('%Y-%m-%d')
        
        logger.info(f"过滤一周前的数据，截止日期: {week_ago_str}")
        logger.info(f"总数据量: {len(data)}")
        
        filtered_data = []
        for item in data:
            item_date = item.get('发布日期', '')  # 使用正确的字段名
            
            # 确保日期格式正确
            if item_date and len(item_date) >= 10:
                try:
                    # 尝试解析日期
                    parsed_date = datetime.strptime(item_date[:10], '%Y-%m-%d')
                    if parsed_date >= week_ago:
                        filtered_data.append(item)
                    else:
                        logger.debug(f"过滤项目: {item.get('新闻标题', '')[:30]}... 日期: {item_date}")
                except ValueError as e:
                    logger.warning(f"日期解析失败: {item_date}, 错误: {e}")
                    # 如果日期解析失败，保留该项目
                    filtered_data.append(item)
            else:
                logger.warning(f"日期格式异常: {item_date}")
                # 如果日期格式异常，保留该项目
                filtered_data.append(item)
        
        logger.info(f"过滤后剩余 {len(filtered_data)} 条数据")
        return filtered_data

    except Exception as e:
        logger.error(f"过滤最近一周数据失败: {e}")
        return data

def get_estock_news(stock_code, max_pages=1, max_retries=3, crawl_content=True, target_name=None, aliases=None):
    """获取指定股票的新闻数据，返回可直接解析为List<Map>的JSON数组"""
    results = []
    pending_for_deepseek = []  # 收集待LLM判定的数据 {idx,title,content}

    # 若使用DeepSeek，预先抓一次板块/概念标签，供提示词参考
    core_tags = []
    if NEWS_SENTIMENT_PROVIDER == "deepseek":
        try:
            core_tags_data = fetch_core_tags(stock_code)
            # 将字典格式转换为列表格式
            if isinstance(core_tags_data, dict):
                concepts = core_tags_data.get("concepts", [])
                industries = core_tags_data.get("industries", [])
                core_tags = concepts + industries
            elif isinstance(core_tags_data, list):
                core_tags = core_tags_data
        except Exception as e:
            logger.debug(f"获取核心题材失败，忽略: {e}")

    try:
        logger.info(f"开始获取股票 {stock_code} 的资讯数据...")
        
        # 并行获取资讯、公告、研报数据
        logger.info("正在并行获取资讯、公告、研报数据...")
        
        with ThreadPoolExecutor(max_workers=3) as executor:
            # 提交三个API任务
            news_future = executor.submit(_get_news_from_api, stock_code)
            notices_future = executor.submit(_get_notices_from_api, stock_code)
            reports_future = executor.submit(_get_reports_from_api, stock_code)
            
            # 获取结果
            news_data = news_future.result()
            notices_data = notices_future.result()
            reports_data = reports_future.result()
            
        logger.info(f"并行获取完成 - 资讯: {len(news_data)}条, 公告: {len(notices_data)}条, 研报: {len(reports_data)}条")
        
        # 合并所有数据
        all_items = []
        
        # 处理资讯
        for news in news_data:
            all_items.append({
                "新闻标题": news['title'],
                "发布日期": news['date'],
                "类型": "资讯",
                "来源": news['source'],
                "链接": news['link']
            })
        
        # 处理公告
        for notice in notices_data:
            all_items.append({
                "新闻标题": notice['title'],
                "发布日期": notice['date'],
                "类型": "公告",
                "来源": notice['columnName'],
                "链接": notice['link']
            })
        
        # 处理研报
        for report in reports_data:
            all_items.append({
                "新闻标题": report['title'],
                "发布日期": report['date'],
                "类型": "研报",
                "来源": f"{report['orgName']} - {report['rating']}",
                "链接": report['link']
            })
        
        # API层面已经进行了日期过滤，无需重复过滤
        logger.info(f"合并后共 {len(all_items)} 条数据")
        
        # 如果不需要爬取内容，直接进行情感分析
        if not crawl_content:
            for item in all_items:
                sentiment_analysis = analyze_news_sentiment(item["新闻标题"], "", "")
                item.update({
                    "情感评分": sentiment_analysis["sentiment_score"],
                    "情感标签": sentiment_analysis["sentiment_label"],
                    "分析摘要": sentiment_analysis["analysis_summary"]
                })
                results.append(item)
        else:
            # 需要爬取内容，使用多线程批量处理
            if len(all_items) > 5:
                # 超过5条数据，使用多线程处理
                logger.info(f"检测到 {len(all_items)} 条数据，超过5条，启用多线程批量处理")
                
                # 分批处理，每批5条
                batch_size = 5
                max_workers = min(5, len(all_items))  # 最多5个线程
                
                for i in range(0, len(all_items), batch_size):
                    batch = all_items[i:i+batch_size]
                    logger.info(f"处理第 {i//batch_size + 1} 批，包含 {len(batch)} 条新闻")
                    
                    # 多线程获取内容
                    batch_with_content = get_news_content_batch(batch, max_workers=max_workers)
                    
                    # 处理这一批的情感分析
                    for item in batch_with_content:
                        if NEWS_SENTIMENT_PROVIDER == "deepseek":
                            # 先占位，稍后批量发送到DeepSeek
                            results.append(item)
                            pending_for_deepseek.append({
                                "idx": len(results)-1,
                                "title": item["新闻标题"],
                                "content": item.get("content", ""),
                            })
                        else:
                            # 关键词规则判定
                            sentiment_analysis = analyze_news_sentiment(
                                item["新闻标题"], 
                                item.get("content", ""), 
                                ""
                            )
                            item.update({
                                "情感评分": sentiment_analysis["sentiment_score"],
                                "情感标签": sentiment_analysis["sentiment_label"],
                                "分析摘要": sentiment_analysis["analysis_summary"]
                            })
                            results.append(item)
                    
                    # 批次间添加短暂延时
                    if i + batch_size < len(all_items):
                        time.sleep(random.uniform(1, 2))
            else:
                # 5条及以下数据，使用原有的串行处理
                logger.info(f"检测到 {len(all_items)} 条数据，使用串行处理")
                for idx, item in enumerate(all_items):
                    logger.info(f"正在处理第 {idx + 1}/{len(all_items)} 条: {item['新闻标题'][:50]}...")
                    
                    if NEWS_SENTIMENT_PROVIDER == "deepseek":
                        # 实际爬取内容
                        content = ""
                        try:
                            content = get_news_content(item["链接"], max_retries=2)
                            logger.debug(f"成功爬取内容，长度: {len(content)} 字符")
                        except Exception as e:
                            logger.warning(f"爬取内容失败: {e}")
                            content = ""
                        
                        # 先占位，稍后批量发送到DeepSeek
                        results.append(item)
                        pending_for_deepseek.append({
                            "idx": len(results)-1,
                            "title": item["新闻标题"],
                            "content": content,
                        })
                    else:
                        # 关键词规则判定
                        sentiment_analysis = analyze_news_sentiment(item["新闻标题"], "", "")
                        item.update({
                            "情感评分": sentiment_analysis["sentiment_score"],
                            "情感标签": sentiment_analysis["sentiment_label"],
                            "分析摘要": sentiment_analysis["analysis_summary"]
                        })
                        results.append(item)
                    
                    # 添加延时避免被反爬
                    time.sleep(random.uniform(1, 2))
            
            # 如果使用DeepSeek，批量判定并写回
            if NEWS_SENTIMENT_PROVIDER == "deepseek" and pending_for_deepseek:
                # 目标名：优先使用上层传入的中文名/别名
                target_to_use = target_name or stock_code
                # 分批（控制token），每批最多6条
                batch_size = 6
                for i in range(0, len(pending_for_deepseek), batch_size):
                    batch = pending_for_deepseek[i:i+batch_size]
                    try:
                        sentiments = classify_news_with_deepseek_batch(
                            target_name=target_to_use,
                            items=[{"title": b["title"], "content": b["content"]} for b in batch],
                            aliases=aliases,
                            context_tags=core_tags,
                        )
                        for b, s in zip(batch, sentiments):
                            idx = b["idx"]
                            label_cn = _map_label_cn(s.get("label"))
                            score = _score_from_label(s.get("label"), s.get("confidence"))
                            analysis_summary = f"{s.get('role','other')} · {s.get('reason','')}".strip()
                            # 写回字段，保持与原结构兼容
                            results[idx].update({
                                "情感评分": score,
                                "情感标签": label_cn,
                                "分析摘要": analysis_summary or "AI情感判定",
                            })
                    except Exception as e:
                        logger.warning(f"DeepSeek批次处理失败，降级为规则判定: {e}")
                        for b in batch:
                            idx = b["idx"]
                            # 回退规则
                            sa = analyze_news_sentiment(b["title"], b.get("content") or "", "")
                            results[idx].update({
                                "情感评分": sa["sentiment_score"],
                                "情感标签": sa["sentiment_label"],
                                "分析摘要": sa["analysis_summary"],
                            })
        
        logger.info(f"成功处理 {len(results)} 条数据")
        
    except Exception as e:
        logger.error(f"获取股票资讯数据失败: {e}")
        # 返回空结果而不是抛出异常，保持接口兼容性
        return json.dumps([], ensure_ascii=False)

    # 移除content字段以减少返回数据大小
    for item in results:
        if "content" in item:
            del item["content"]
    
    # 返回JSON格式数据
    return json.dumps(results, ensure_ascii=False)

if __name__ == "__main__":
    # 解析命令行参数
    stock_code = sys.argv[1] if len(sys.argv) > 1 else "300402"

    # 检查是否需要爬取详细内容（默认开启）
    crawl_content = True
    if len(sys.argv) > 2 and sys.argv[2].lower() in ['false', '0', 'no']:
        crawl_content = False

    # 页数参数（默认1页）
    max_pages = 1
    if len(sys.argv) > 3:
        try:
            max_pages = int(sys.argv[3])
        except ValueError:
            max_pages = 1

    # 目标公司中文名（可选）与别名（可选，逗号分隔）
    target_name = None
    aliases = None
    if len(sys.argv) > 4:
        target_name = sys.argv[4] if sys.argv[4].strip() else None
    if len(sys.argv) > 5 and sys.argv[5].strip():
        aliases = [a.strip() for a in sys.argv[5].split(',') if a.strip()]

    # 环境变量配置（从命令行参数接收）
    if len(sys.argv) > 6:
        deepseek_api_key = sys.argv[6] if sys.argv[6].strip() else None
        if deepseek_api_key:
            os.environ["DEEPSEEK_API_KEY"] = deepseek_api_key
            # 重新读取环境变量
            DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY")
    
    if len(sys.argv) > 7:
        sentiment_provider = sys.argv[7] if sys.argv[7].strip() else "keyword"
        os.environ["NEWS_SENTIMENT_PROVIDER"] = sentiment_provider
        # 重新读取环境变量
        NEWS_SENTIMENT_PROVIDER = os.getenv("NEWS_SENTIMENT_PROVIDER", "keyword").lower()

    logger.info(f"开始获取股票 {stock_code} 的新闻数据...")
    logger.info(f"爬取详细内容: {crawl_content}, 页数: {max_pages}")
    logger.info(f"情感分析提供者: {NEWS_SENTIMENT_PROVIDER}")
    if DEEPSEEK_API_KEY:
        logger.info("DeepSeek API密钥已配置")
    else:
        logger.warning("DeepSeek API密钥未配置，将使用关键词规则分析")

    news_data = get_estock_news(
        stock_code,
        max_pages=max_pages,
        crawl_content=crawl_content,
        target_name=target_name,
        aliases=aliases,
    )
    print(news_data)