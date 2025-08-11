import json
import time
import random
import sys
import re
from curl_cffi import requests
import logging
from bs4 import BeautifulSoup
from urllib.parse import urljoin, urlparse

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


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


def get_estock_news(stock_code, max_pages=1, max_retries=3, crawl_content=True):
    """获取指定股票的新闻数据，返回可直接解析为List<Map>的JSON数组"""
    results = []

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

                    # 基础新闻信息
                    news_item = {
                        "新闻标题": title,
                        "发布日期": article.get('date', ''),
                        "新闻摘要": summary[:100] + "..." if len(summary) > 100 else summary
                    }

                    # 爬取详细内容和进行情感分析
                    if crawl_content and url:
                        logger.info(f"正在爬取新闻内容: {title[:50]}...")

                        # 爬取新闻详细内容
                        full_content = crawl_news_content(url)

                        # 进行情感分析
                        sentiment_analysis = analyze_news_sentiment(title, full_content, summary)

                        # 添加扩展信息
                        news_item.update({
                            "情感评分": sentiment_analysis["sentiment_score"],
                            "情感标签": sentiment_analysis["sentiment_label"],
                            "利好关键词": sentiment_analysis["positive_keywords"],
                            "利空关键词": sentiment_analysis["negative_keywords"],
                            "分析摘要": sentiment_analysis["analysis_summary"]
                        })

                        # 添加延时避免被反爬
                        time.sleep(random.uniform(1, 2))
                    else:
                        # 如果不爬取内容，仅基于标题和摘要进行简单分析
                        sentiment_analysis = analyze_news_sentiment(title, "", summary)
                        news_item.update({
                            "情感评分": sentiment_analysis["sentiment_score"],
                            "情感标签": sentiment_analysis["sentiment_label"],
                            "利好关键词": sentiment_analysis["positive_keywords"],
                            "利空关键词": sentiment_analysis["negative_keywords"],
                            "分析摘要": sentiment_analysis["analysis_summary"]
                        })

                    results.append(news_item)

                success = True
                time.sleep(random.uniform(0.5, 1.5))

            except Exception as e:
                logger.error(f"第{retry_count + 1}次请求失败: {str(e)}")
                time.sleep(2 ** retry_count)
                retry_count += 1

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

    logger.info(f"开始获取股票 {stock_code} 的新闻数据...")
    logger.info(f"爬取详细内容: {crawl_content}, 页数: {max_pages}")

    news_data = get_estock_news(stock_code, max_pages=max_pages, crawl_content=crawl_content)
    print(news_data)