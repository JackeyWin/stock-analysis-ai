#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
东方财富 F10 核心题材/所属板块 小标签爬取脚本

用法:
  python python_scripts/EastMoneyCoreTags.py SZ002466

输出(JSON):
  {
    "stockCode": "SZ002466",
    "concepts": [ ... ],
    "industries": [ ... ],
    "tags": [ ... ]  # 合并去重
  }

说明:
- 首选调用 F10 的 Ajax 接口, 设置 Referer 与 X-Requested-With, 避免返回占位页
- 结构不稳定时做广谱提取, 尽量从返回体中抓取 name/gnName 等字段
"""

import json
import re
import sys
from typing import Dict, List, Any, Tuple

from curl_cffi import requests

# 可选渲染回退
try:
    from playwright.sync_api import sync_playwright
    HAS_PLAYWRIGHT = True
except Exception:
    HAS_PLAYWRIGHT = False


HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36",
    "Accept": "application/json, text/javascript, */*; q=0.01",
    "X-Requested-With": "XMLHttpRequest",
}


def _get(url: str, code: str) -> Any:
    headers = dict(HEADERS)
    headers["Referer"] = f"https://emweb.securities.eastmoney.com/pc_hsf10/pages/index.html?type=web&code={code}&color=b#/hxtc"
    resp = requests.get(url.format(code=code), headers=headers, timeout=10, impersonate="chrome110")
    resp.raise_for_status()
    text = resp.text.strip()
    if text.startswith("{") or text.startswith("["):
        return json.loads(text)
    m = re.search(r"\{[\s\S]*\}$", text)
    if m:
        return json.loads(m.group(0))
    return None


def _collect_strings(obj: Any, keys: List[str]) -> List[str]:
    out: List[str] = []
    def walk(x: Any):
        if isinstance(x, dict):
            for k, v in x.items():
                if isinstance(v, (dict, list)):
                    walk(v)
                else:
                    if isinstance(v, str) and k.lower() in keys:
                        s = v.strip()
                        if s:
                            out.append(s)
        elif isinstance(x, list):
            for i in x:
                walk(i)
    walk(obj)
    return out


def _clean_chip_text(text: str) -> str:
    if not text:
        return ""
    s = re.sub(r"\s+", " ", text).strip()
    # 去掉末尾涨跌幅，如 "锂矿概念 -0.84%"
    s = re.sub(r"\s*[+-]?\d+(?:\.\d+)?%$", "", s)
    # 去掉无关字样
    s = s.replace("行业", "").replace("地区", "").strip()
    return s

def fetch_by_render(stock_code: str, max_retries: int = 3) -> Tuple[List[str], List[str]]:
    if not HAS_PLAYWRIGHT:
        return [], []
    url = f"https://emweb.securities.eastmoney.com/pc_hsf10/pages/index.html?type=web&code={stock_code}&color=b#/hxtc"
    
    for attempt in range(max_retries):
        try:
            with sync_playwright() as p:
                browser = p.chromium.launch(headless=True)
                page = browser.new_page(user_agent=HEADERS["User-Agent"])
                page.goto(url, wait_until="networkidle", timeout=45000)
                try:
                    page.wait_for_selector(".section.gntc .gntc_content ul.board li", timeout=4000)
                except Exception:
                    pass
                concepts_raw = page.eval_on_selector_all(
                    ".section.gntc .gntc_content ul.board li",
                    "els => els.map(e => (e.textContent||'').trim())"
                ) or []
                industries_raw = page.eval_on_selector_all(
                    ".section.tcxq .hxtccontent ul.board li.boardName",
                    "els => els.map(e => (e.textContent||'').trim())"
                ) or []
                def clean_list(lst: List[str]) -> List[str]:
                    cleaned = []
                    seen = set()
                    for t in lst:
                        ct = _clean_chip_text(t)
                        if ct and ct not in seen and re.search(r"[\u4e00-\u9fa5A-Za-z0-9]", ct):
                            seen.add(ct)
                            cleaned.append(ct)
                    return cleaned
                concepts = clean_list(concepts_raw)
                industries = clean_list(industries_raw)
                browser.close()
                return concepts[:50], industries[:50]
        except Exception as e:
            # 如果不是最后一次尝试，打印重试信息
            if attempt < max_retries - 1:
                print(f"Warning: Playwright rendering attempt {attempt + 1} failed for {stock_code}: {str(e)}", file=sys.stderr)
                continue
            else:
                # 最后一次尝试失败，返回空列表而不是让程序崩溃
                print(f"Warning: Playwright rendering failed for {stock_code} after {max_retries} attempts: {str(e)}", file=sys.stderr)
                return [], []
    
    # 如果所有重试都失败了，返回空列表
    return [], []


def fetch_core_tags(stock_code: str) -> Dict[str, List[str]]:
    urls = {
        # 概念/题材相关
        "concepts": [
            "https://emweb.securities.eastmoney.com/PC_HSF10/CoreConception/CoreConceptionAjax?code={code}",
            "https://emweb.securities.eastmoney.com/PC_HSF10/Concept/ConceptAjax?code={code}",
        ],
        # 所属板块相关
        "industries": [
            "https://emweb.securities.eastmoney.com/PC_HSF10/IndustryConception/IndustryConceptionAjax?code={code}",
        ],
    }

    result = {"concepts": [], "industries": []}
    seen_con, seen_ind = set(), set()

    keys_candidates = [
        "name", "conceptname", "gnname", "bk", "plate", "title", "zsName", "f14"
    ]

    for bucket, url_list in urls.items():
        for u in url_list:
            try:
                data = _get(u, stock_code)
                if not data:
                    continue
                items = _collect_strings(data, keys_candidates)
                for s in items:
                    if bucket == "concepts":
                        if s not in seen_con:
                            seen_con.add(s)
                            result["concepts"].append(s)
                    else:
                        if s not in seen_ind:
                            seen_ind.add(s)
                            result["industries"].append(s)
            except Exception:
                continue

    # 若API抓取为空，尝试渲染回退
    if not result["concepts"] and not result["industries"]:
        c2, i2 = fetch_by_render(stock_code)
        result["concepts"], result["industries"] = c2, i2

    # 裁剪
    result["concepts"] = result["concepts"][:50]
    result["industries"] = result["industries"][:50]
    return result


def main():
    code = sys.argv[1] if len(sys.argv) > 1 else "SZ002466"
    data = fetch_core_tags(code)
    concepts = data.get("concepts", [])
    industries_first = data.get("industries", [])[:1]
    out = {
        "stockCode": code,
        "concepts": concepts,
        "industries": industries_first,
    }
    print(json.dumps(out, ensure_ascii=False))


if __name__ == "__main__":
    main()


