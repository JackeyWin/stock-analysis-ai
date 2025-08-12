import json
import time
import random
import sys
import re
import os
import hashlib
from curl_cffi import requests
import requests as pyrequests
import logging
from bs4 import BeautifulSoup
from urllib.parse import urljoin, urlparse

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY")
DEEPSEEK_URL = "https://api.deepseek.com/v1/chat/completions"
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")  # DeepSeek V3
NEWS_SENTIMENT_PROVIDER = os.getenv("NEWS_SENTIMENT_PROVIDER", "keyword").lower()  # keyword | deepseek

_sentiment_cache = {}

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
    if context_tags:
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
            resp = pyrequests.post(DEEPSEEK_URL, headers=headers, json=payload, timeout=30)
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

def _try_get_json(url: str, code: str) -> dict | None:
    try:
        hdrs = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36",
            "Accept": "application/json, text/javascript, */*; q=0.01",
            "Referer": f"https://emweb.securities.eastmoney.com/pc_hsf10/pages/index.html?type=web&code={code}&color=b#/hxtc",
            "X-Requested-With": "XMLHttpRequest",
        }
        r = requests.get(url.format(code=code), headers=hdrs, timeout=10, impersonate="chrome110")
        r.raise_for_status()
        text = r.text.strip()
        # 兼容JSONP
        if text.startswith('{') or text.startswith('['):
            return json.loads(text)
        m = re.search(r"\{[\s\S]*\}$", text)
        if m:
            return json.loads(m.group(0))
        return None
    except Exception as e:
        logger.debug(f"获取核心题材接口失败: {url} - {e}")
        return None

def fetch_core_tags(stock_code: str) -> list:
    """拉取东方财富F10核心题材/所属板块小标签，尽量少依赖页面渲染。"""
    urls = [
        "https://emweb.securities.eastmoney.com/PC_HSF10/CoreConception/CoreConceptionAjax?code={code}",
        "https://emweb.securities.eastmoney.com/PC_HSF10/IndustryConception/IndustryConceptionAjax?code={code}",
        "https://emweb.securities.eastmoney.com/PC_HSF10/Concept/ConceptAjax?code={code}",
    ]
    tags = []
    seen = set()
    for u in urls:
        data = _try_get_json(u, stock_code)
        if not data:
            continue
        # 广谱提取：扫描所有列表中的 name/NAME/gnName 等常见字段
        def collect(obj):
            if isinstance(obj, dict):
                for k, v in obj.items():
                    if isinstance(v, (list, dict)):
                        collect(v)
                    else:
                        # 可能的名字字段
                        if k.lower() in ("name", "conceptname", "gnname", "bk", "plate", "title") and isinstance(v, str):
                            s = v.strip()
                            if s and s not in seen:
                                seen.add(s)
                                tags.append(s)
            elif isinstance(obj, list):
                for it in obj:
                    collect(it)
        collect(data)
    # 去重并裁剪
    return tags[:24]

def crawl_news_content(url, max_retries=2):
    """爬取新闻详细内容"""
    if not url or url == '':
        return ""

    retry_count = 0
    while retry_count < max_retries:
        try:
            headers = {
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
                "Accept-Encoding": "gzip, deflate, br",
                "Connection": "keep-alive",
                "Upgrade-Insecure-Requests": "1"
            }

            response = requests.get(url, headers=headers, timeout=10, impersonate="chrome110")
            response.raise_for_status()

            soup = BeautifulSoup(response.text, 'html.parser')

            # 尝试多种常见的新闻内容选择器
            content_selectors = [
                '.article-content',
                '.news-content',
                '.content',
                '.article-body',
                '.news-body',
                '#content',
                '.post-content',
                '.entry-content',
                'article',
                '.main-content'
            ]

            content = ""
            for selector in content_selectors:
                elements = soup.select(selector)
                if elements:
                    # 提取文本内容
                    for element in elements:
                        # 移除脚本和样式标签
                        for script in element(["script", "style"]):
                            script.decompose()
                        text = element.get_text(strip=True)
                        if len(text) > 100:  # 确保内容足够长
                            content = text
                            break
                    if content:
                        break

            # 如果没有找到特定选择器，尝试提取所有段落
            if not content:
                paragraphs = soup.find_all('p')
                content = ' '.join([p.get_text(strip=True) for p in paragraphs if p.get_text(strip=True)])

            # 清理内容
            content = re.sub(r'\s+', ' ', content)  # 合并多个空白字符
            content = content.strip()

            # 限制内容长度
            if len(content) > 2000:
                content = content[:2000] + "..."

            return content

        except Exception as e:
            logger.warning(f"爬取新闻内容失败 (尝试 {retry_count + 1}/{max_retries}): {url}, 错误: {str(e)}")
            retry_count += 1
            time.sleep(1)

    return ""


def analyze_news_sentiment(title, content, summary=""):
    """分析新闻情感倾向，返回评分和分析结果"""

    # 利好关键词
    positive_keywords = [
        # 业绩相关
        '业绩增长', '净利润增长', '营收增长', '盈利', '超预期', '业绩亮眼', '业绩大增',
        '营收大增', '利润大幅增长', '业绩向好', '业绩改善', '扭亏为盈',

        # 合作与发展
        '合作', '签约', '中标', '获得订单', '战略合作', '合作协议', '框架协议',
        '重大合同', '长期合作', '深度合作',

        # 技术与创新
        '技术突破', '创新', '研发成功', '专利', '技术领先', '核心技术', '自主研发',
        '技术优势', '科技创新', '产品升级',

        # 市场与扩张
        '市场份额', '扩张', '布局', '进军', '开拓市场', '市场领先', '龙头地位',
        '市场占有率', '业务拓展', '海外市场',

        # 政策与支持
        '政策支持', '政府补贴', '税收优惠', '政策利好', '国家支持', '行业政策',
        '扶持政策', '优惠政策',

        # 投资与融资
        '投资', '融资', '增资', '股权激励', '分红', '回购', '增持', '重组',
        '并购', '资产注入', '定增',

        # 其他利好
        '上涨', '看好', '推荐', '买入', '增长潜力', '前景良好', '发展机遇',
        '积极', '乐观', '向好', '改善', '提升', '突破', '成功'
    ]

    # 利空关键词
    negative_keywords = [
        # 业绩相关
        '业绩下滑', '亏损', '净利润下降', '营收下降', '业绩不佳', '业绩预警',
        '业绩大幅下滑', '盈利能力下降', '毛利率下降',

        # 风险与问题
        '风险', '违规', '处罚', '罚款', '调查', '立案', '诉讼', '纠纷',
        '债务', '资金链', '流动性', '偿债能力', '财务风险',

        # 市场与竞争
        '市场萎缩', '竞争激烈', '价格战', '市场份额下降', '失去订单',
        '客户流失', '需求下降',

        # 经营困难
        '停产', '减产', '裁员', '关闭', '退出', '暂停', '延期', '推迟',
        '困难', '挑战', '压力', '不确定性',

        # 监管与合规
        '监管', '合规', '整改', '暂停交易', '特别处理', 'ST', '*ST',
        '退市风险', '警示',

        # 其他利空
        '下跌', '看空', '卖出', '减持', '质押', '解禁', '商誉减值',
        '计提', '坏账', '负面', '悲观', '担忧', '恶化'
    ]

    # 合并所有文本进行分析
    full_text = f"{title} {content} {summary}".lower()

    # 计算关键词得分
    positive_score = 0
    negative_score = 0

    positive_matches = []
    negative_matches = []

    for keyword in positive_keywords:
        count = full_text.count(keyword.lower())
        if count > 0:
            positive_score += count
            positive_matches.append(f"{keyword}({count})")

    for keyword in negative_keywords:
        count = full_text.count(keyword.lower())
        if count > 0:
            negative_score += count
            negative_matches.append(f"{keyword}({count})")

    # 计算最终评分 (-100 到 100)
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


def get_estock_news(stock_code, max_pages=1, max_retries=3, crawl_content=True, target_name=None, aliases=None):
    """获取指定股票的新闻数据，返回可直接解析为List<Map>的JSON数组"""
    results = []
    pending_for_deepseek = []  # 收集待LLM判定的数据 {idx,title,content}

    # 若使用DeepSeek，预先抓一次板块/概念标签，供提示词参考
    core_tags = []
    if NEWS_SENTIMENT_PROVIDER == "deepseek":
        try:
            core_tags = fetch_core_tags(stock_code)
        except Exception as e:
            logger.debug(f"获取核心题材失败，忽略: {e}")

    for page in range(1, max_pages + 1):
        retry_count = 0
        success = False

        while retry_count < max_retries and not success:
            try:
                timestamp = int(time.time() * 1000)
                callback_id = f"jQuery{random.randint(int(1e15), int(1e16) - 1)}_{timestamp}"

                params = {
                    "cb": callback_id,
                    "param": json.dumps({
                        "uid": "",
                        "keyword": stock_code,
                        "type": ["cmsArticleWebOld"],
                        "client": "web",
                        "clientType": "web",
                        "clientVersion": "curr",
                        "param": {
                            "cmsArticleWebOld": {
                                "searchScope": "default",
                                "sort": "default",
                                "pageIndex": page,
                                "pageSize": 10,
                                "preTag": "<em>",
                                "postTag": "</em>"
                            }
                        }
                    }, separators=(',', ':')),
                    "_": timestamp
                }

                headers = {
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Referer": "https://so.eastmoney.com/"
                }

                response = requests.get(
                    "https://search-api-web.eastmoney.com/search/jsonp",
                    params=params,
                    headers=headers,
                    impersonate="chrome110"
                )
                response.raise_for_status()

                if '(' in response.text and ')' in response.text:
                    json_start = response.text.find('(') + 1
                    json_end = response.text.rfind(')')
                    json_data = json.loads(response.text[json_start:json_end])
                else:
                    continue

                if json_data.get('code') != 0:
                    raise ValueError(f"API返回错误代码: {json_data.get('code')}")

                for article in json_data.get('result', {}).get('cmsArticleWebOld', []):
                    # 标准化数据格式，使用中文字段名
                    title = article.get('title', '').replace('<em>', '').replace('</em>', '')
                    summary = article.get('content', '')
                    url = article.get('url', '')

                    # 基础新闻信息（移除新闻摘要，减少字数）
                    news_item = {
                        "新闻标题": title,
                        "发布日期": article.get('date', '')
                    }

                    # 爬取详细内容并准备情感分析
                    if crawl_content and url:
                        logger.info(f"正在爬取新闻内容: {title[:50]}...")

                        # 爬取新闻详细内容
                        full_content = crawl_news_content(url)
                        if NEWS_SENTIMENT_PROVIDER == "deepseek":
                            # 先占位，稍后批量发送到DeepSeek
                            results.append(news_item)
                            pending_for_deepseek.append({
                                "idx": len(results)-1,
                                "title": title,
                                "content": full_content,
                            })
                        else:
                            # 关键词规则判定（默认，不再使用摘要）
                            sentiment_analysis = analyze_news_sentiment(title, full_content, "")
                            news_item.update({
                                "情感评分": sentiment_analysis["sentiment_score"],
                                "情感标签": sentiment_analysis["sentiment_label"],
                                "分析摘要": sentiment_analysis["analysis_summary"]
                            })
                            results.append(news_item)
                            # 添加延时避免被反爬
                            time.sleep(random.uniform(1, 2))
                    else:
                        # 如果不爬取内容，仅基于标题（不再携带摘要，减少token）
                        if NEWS_SENTIMENT_PROVIDER == "deepseek":
                            results.append(news_item)
                            pending_for_deepseek.append({
                                "idx": len(results)-1,
                                "title": title,
                                "content": "",
                            })
                        else:
                            sentiment_analysis = analyze_news_sentiment(title, "", "")
                            news_item.update({
                                "情感评分": sentiment_analysis["sentiment_score"],
                                "情感标签": sentiment_analysis["sentiment_label"],
                                "分析摘要": sentiment_analysis["analysis_summary"]
                            })
                            results.append(news_item)

                success = True
                time.sleep(random.uniform(0.5, 1.5))

            except Exception as e:
                logger.error(f"第{retry_count + 1}次请求失败: {str(e)}")
                time.sleep(2 ** retry_count)
                retry_count += 1

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

    logger.info(f"开始获取股票 {stock_code} 的新闻数据...")
    logger.info(f"爬取详细内容: {crawl_content}, 页数: {max_pages}")

    news_data = get_estock_news(
        stock_code,
        max_pages=max_pages,
        crawl_content=crawl_content,
        target_name=target_name,
        aliases=aliases,
    )
    print(news_data)