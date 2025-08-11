#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import sys
import json
import subprocess
import tempfile
import os
from typing import Any, Dict, List


ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def run_cmd(args: List[str]) -> subprocess.CompletedProcess:
    return subprocess.run(args, capture_output=True, text=True, encoding='utf-8', errors='ignore')


def fetch_kline(stock_code: str, period: str) -> List[Dict[str, Any]]:
    script = os.path.join(ROOT_DIR, 'python_scripts', 'EastMoneyTickHistoryKline.py')
    if period == 'day':
        proc = run_cmd([sys.executable, script, stock_code])
    else:
        proc = run_cmd([sys.executable, script, stock_code, period])

    if proc.returncode != 0:
        raise RuntimeError(f'获取 {period} K线失败: {proc.stderr.strip()}')

    try:
        data = json.loads(proc.stdout)
        if not isinstance(data, list):
            raise ValueError('返回数据不是列表')
        return data
    except Exception as e:
        raise RuntimeError(f'解析 {period} K线JSON失败: {e}\n原始输出片段: {proc.stdout[:200]}')


def compute_indicators(multi_tf_obj: Dict[str, Any]) -> Dict[str, Any]:
    script = os.path.join(ROOT_DIR, 'python_scripts', 'TechnicalIndicators.py')
    # 写入临时文件，避免命令行过长
    with tempfile.NamedTemporaryFile(delete=False, suffix='.json', mode='w', encoding='utf-8') as tf:
        json.dump(multi_tf_obj, tf, ensure_ascii=False)
        temp_path = tf.name

    try:
        proc = run_cmd([sys.executable, script, temp_path])
        if proc.returncode != 0:
            raise RuntimeError(f'技术指标计算失败: {proc.stderr.strip()}')
        return json.loads(proc.stdout)
    finally:
        try:
            os.unlink(temp_path)
        except Exception:
            pass


def main():
    stock_code = sys.argv[1] if len(sys.argv) > 1 else '688333'
    print(f'>>> 开始测试技术指标计算，股票: {stock_code}')

    # 抓取多周期K线
    day = fetch_kline(stock_code, 'day')
    tf60 = fetch_kline(stock_code, '60m')
    tf5 = fetch_kline(stock_code, '5m')

    multi = {
        'day': day,
        '60m': tf60,
        '5m': tf5,
    }

    result = compute_indicators(multi)

    # 摘要打印
    tfs = result.get('timeframes', {})
    print('>>> 可用周期:', list(tfs.keys()))

    def pick_keys_case_insensitive(obj, names):
        out = {}
        lower_map = {k.lower(): k for k in obj.keys()}
        for name in names:
            for k_lower, k_orig in lower_map.items():
                if name in k_lower:
                    out[k_orig] = obj[k_orig]
        return out

    def print_tf_summary(tf_name):
        tf = tfs.get(tf_name, {})
        latest = tf.get('latest', {})
        sr = tf.get('supportResistance', {})
        print(f'>>> {tf_name} 最新快照(核心):', json.dumps({
            'close': latest.get('close'),
            'volume': latest.get('volume'),
            'ma5': latest.get('ma5'),
            'ma10': latest.get('ma10'),
            'ma20': latest.get('ma20'),
            'ma60': latest.get('ma60'),
            'rsi': latest.get('rsi'),
            'macd': latest.get('macd'),
            'macd_signal': latest.get('macd_signal'),
            'macd_hist': latest.get('macd_hist'),
        }, ensure_ascii=False))

        # 扩展：ATR/ADX/OBV/MFI/CCI
        wanted = ['atr', 'adx', 'di+', 'di-', 'plus_di', 'minus_di', 'obv', 'mfi', 'cci']
        extra_ind = pick_keys_case_insensitive(latest, wanted)
        if not extra_ind:
            # 有些实现可能把这些指标存在 'latest' 的嵌套或 'signals'，尝试在 signals 中抓取数值
            signals = tf.get('signals', {}) if isinstance(tf.get('signals'), dict) else {}
            extra_ind = pick_keys_case_insensitive(signals, wanted)
        print(f'>>> {tf_name} 最新快照(ATR/ADX/OBV/MFI/CCI):', json.dumps(extra_ind, ensure_ascii=False))

        if sr:
            print(f'>>> {tf_name} 支撑压力:', json.dumps(sr, ensure_ascii=False))

        # 最近样本仅挑选目标指标，便于观察
        recent = tf.get('recent', [])
        if recent:
            print(f'>>> {tf_name} 最近3条指标(含 ATR/ADX/OBV/MFI/CCI):')
            for item in recent[-3:]:
                subset = {'date': item.get('date')}
                subset.update(pick_keys_case_insensitive(item, wanted))
                print('  -', json.dumps(subset, ensure_ascii=False))

    for tf_name in ['day', '60m', '5m']:
        if tf_name in tfs:
            print_tf_summary(tf_name)

    # 保存完整结果到文件
    out_path = os.path.join(ROOT_DIR, 'python_scripts', '_test_output_indicators.json')
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print(f'>>> 完整结果已保存: {out_path}')


if __name__ == '__main__':
    main()


