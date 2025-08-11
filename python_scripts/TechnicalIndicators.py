import numpy as np
import pandas as pd
import json
import sys
from datetime import datetime, timedelta
import base64
import warnings
warnings.filterwarnings('ignore')


class TechnicalIndicatorCalculator:
    """技术指标计算器"""
    
    def __init__(self, kline_data):
        """
        初始化技术指标计算器
        :param kline_data: K线数据列表，每个元素包含 d, o, c, h, l, v, tu 字段
        """
        self.df = self._prepare_dataframe(kline_data)
        
    def _prepare_dataframe(self, kline_data):
        """准备DataFrame数据"""
        if not kline_data:
            raise ValueError("K线数据为空")
            
        # 限制数据量，避免内存问题
        if len(kline_data) > 500:
            print(f"警告: K线数据量过大({len(kline_data)}条)，只使用最近500条数据进行计算", file=sys.stderr)
            kline_data = kline_data[-500:]
        
        data = []
        for i, item in enumerate(kline_data):
            try:
                # 验证必要字段
                required_fields = ['d', 'o', 'c', 'h', 'l', 'v']
                for field in required_fields:
                    if field not in item:
                        raise ValueError(f"第{i+1}条数据缺少字段: {field}")
                
                # 安全转换数值，处理可能的异常值
                try:
                    open_price = float(item['o'])
                    close_price = float(item['c'])
                    high_price = float(item['h'])
                    low_price = float(item['l'])
                    volume = float(item['v'])
                    
                    # 基本数据验证
                    if any(price <= 0 for price in [open_price, close_price, high_price, low_price]):
                        print(f"警告: 第{i+1}条数据价格异常，跳过", file=sys.stderr)
                        continue
                        
                    if high_price < max(open_price, close_price) or low_price > min(open_price, close_price):
                        print(f"警告: 第{i+1}条数据价格逻辑错误，跳过", file=sys.stderr)
                        continue
                    
                    data.append({
                        'date': item['d'],
                        'open': open_price,
                        'close': close_price,
                        'high': high_price,
                        'low': low_price,
                        'volume': volume * 10000,  # 转换为手
                        'turnover': float(item.get('tu', 0)) * 100000000  # 转换为元，tu字段可能不存在
                    })
                except (ValueError, TypeError) as e:
                    print(f"警告: 第{i+1}条数据转换失败: {str(e)}，跳过", file=sys.stderr)
                    continue
                    
            except Exception as e:
                print(f"警告: 处理第{i+1}条数据时出错: {str(e)}，跳过", file=sys.stderr)
                continue
        
        if not data:
            raise ValueError("没有有效的K线数据")
            
        if len(data) < 20:
            print(f"警告: 有效数据量较少({len(data)}条)，技术指标计算可能不准确", file=sys.stderr)
        
        df = pd.DataFrame(data)
        df['date'] = pd.to_datetime(df['date'])
        df = df.sort_values('date').reset_index(drop=True)
        
        # 移除重复日期，保留最后一条
        df = df.drop_duplicates(subset=['date'], keep='last').reset_index(drop=True)
        
        return df
    
    def calculate_ma(self, period):
        """计算移动平均线"""
        return self.df['close'].rolling(window=period).mean()
    
    def calculate_rsi(self, period=14):
        """计算RSI指标"""
        delta = self.df['close'].diff()
        gain = (delta.where(delta > 0, 0)).rolling(window=period).mean()
        loss = (-delta.where(delta < 0, 0)).rolling(window=period).mean()
        rs = gain / loss
        rsi = 100 - (100 / (1 + rs))
        return rsi
    
    def calculate_macd(self, fast=12, slow=26, signal=9):
        """计算MACD指标"""
        exp1 = self.df['close'].ewm(span=fast).mean()
        exp2 = self.df['close'].ewm(span=slow).mean()
        macd = exp1 - exp2
        signal_line = macd.ewm(span=signal).mean()
        histogram = macd - signal_line
        return macd, signal_line, histogram
    
    def calculate_bollinger_bands(self, period=20, std_dev=2):
        """计算布林带"""
        ma = self.df['close'].rolling(window=period).mean()
        std = self.df['close'].rolling(window=period).std()
        upper = ma + (std * std_dev)
        lower = ma - (std * std_dev)
        return upper, ma, lower
    
    def calculate_kdj(self, period=9, k_period=3, d_period=3):
        """计算KDJ指标"""
        low_min = self.df['low'].rolling(window=period).min()
        high_max = self.df['high'].rolling(window=period).max()
        
        rsv = (self.df['close'] - low_min) / (high_max - low_min) * 100
        k = rsv.ewm(alpha=1/k_period).mean()
        d = k.ewm(alpha=1/d_period).mean()
        j = 3 * k - 2 * d
        
        return k, d, j

    def calculate_true_range(self):
        """计算真实波动范围 TR"""
        prev_close = self.df['close'].shift(1)
        high_low = self.df['high'] - self.df['low']
        high_prev_close = (self.df['high'] - prev_close).abs()
        low_prev_close = (self.df['low'] - prev_close).abs()
        tr = pd.concat([high_low, high_prev_close, low_prev_close], axis=1).max(axis=1)
        return tr

    def calculate_atr(self, period=14):
        """计算 ATR"""
        tr = self.calculate_true_range()
        atr = tr.rolling(window=period).mean()
        return atr

    def calculate_adx(self, period=14):
        """计算 ADX 及 DI+/DI-（简化版平滑）"""
        high = self.df['high']
        low = self.df['low']
        close = self.df['close']

        up_move = high.diff()
        down_move = -low.diff()

        plus_dm = ((up_move > down_move) & (up_move > 0)).astype(float) * up_move.clip(lower=0)
        minus_dm = ((down_move > up_move) & (down_move > 0)).astype(float) * down_move.clip(lower=0)

        tr = self.calculate_true_range()

        # 平滑（使用滚动均值简化）
        atr = tr.rolling(window=period).mean()
        plus_di = 100 * (plus_dm.rolling(window=period).sum() / atr)
        minus_di = 100 * (minus_dm.rolling(window=period).sum() / atr)

        dx = 100 * (plus_di.subtract(minus_di).abs() / (plus_di + minus_di))
        adx = dx.rolling(window=period).mean()

        return adx, plus_di, minus_di

    def calculate_obv(self):
        """计算 OBV"""
        price_change = self.df['close'].diff()
        direction = price_change.apply(lambda x: 1 if x > 0 else (-1 if x < 0 else 0))
        obv = (direction * self.df['volume']).cumsum()
        return obv

    def calculate_mfi(self, period=14):
        """计算 MFI"""
        typical_price = (self.df['high'] + self.df['low'] + self.df['close']) / 3.0
        money_flow = typical_price * self.df['volume']

        tp_diff = typical_price.diff()
        positive_flow = money_flow.where(tp_diff > 0, 0.0)
        negative_flow = money_flow.where(tp_diff < 0, 0.0)

        pos_mf = positive_flow.rolling(window=period).sum()
        neg_mf = negative_flow.rolling(window=period).sum().abs()

        mf_ratio = pos_mf / neg_mf.replace(0, pd.NA)
        mfi = 100 - (100 / (1 + mf_ratio))
        return mfi

    def calculate_cci(self, period=20):
        """计算 CCI"""
        typical_price = (self.df['high'] + self.df['low'] + self.df['close']) / 3.0
        sma_tp = typical_price.rolling(window=period).mean()
        mean_dev = (typical_price - sma_tp).abs().rolling(window=period).mean()
        cci = (typical_price - sma_tp) / (0.015 * mean_dev)
        return cci
    
    def find_support_resistance(self, period=20):
        """计算支撑位和压力位"""
        recent_data = self.df.tail(period)
        support = recent_data['low'].min()
        resistance = recent_data['high'].max()
        return support, resistance
    
    def find_volume_anomalies(self, threshold=2.0):
        """寻找量价异动"""
        volume_ma = self.df['volume'].rolling(window=20).mean()
        volume_ratio = self.df['volume'] / volume_ma
        
        price_change = (self.df['close'] - self.df['close'].shift(1)) / self.df['close'].shift(1) * 100
        
        anomalies = []
        for i in range(len(self.df)):
            if volume_ratio.iloc[i] > threshold and abs(price_change.iloc[i]) > 3:
                anomalies.append({
                    'date': self.df.iloc[i]['date'].strftime('%Y-%m-%d'),
                    'volume': self.df.iloc[i]['volume'],
                    'price_change': price_change.iloc[i],
                    'volume_ratio': volume_ratio.iloc[i]
                })
        
        return anomalies
    
    def find_technical_signals(self):
        """寻找技术信号"""
        signals = []
        
        # 计算指标
        rsi = self.calculate_rsi()
        macd, signal_line, histogram = self.calculate_macd()
        ma5 = self.calculate_ma(5)
        ma10 = self.calculate_ma(10)
        
        # 检查最近20天的信号
        recent_days = min(20, len(self.df))
        start_idx = len(self.df) - recent_days
        
        for i in range(start_idx, len(self.df)):
            date = self.df.iloc[i]['date'].strftime('%Y-%m-%d')
            
            # RSI超买超卖信号
            if i > 0:
                if rsi.iloc[i] > 70 and rsi.iloc[i-1] <= 70:
                    signals.append({
                        'date': date,
                        'type': 'RSI超买',
                        'description': f'RSI(14)超过70，当前值{rsi.iloc[i]:.2f}'
                    })
                elif rsi.iloc[i] < 30 and rsi.iloc[i-1] >= 30:
                    signals.append({
                        'date': date,
                        'type': 'RSI超卖',
                        'description': f'RSI(14)低于30，当前值{rsi.iloc[i]:.2f}'
                    })
            
            # MACD金叉死叉信号
            if i > 0:
                if macd.iloc[i] > signal_line.iloc[i] and macd.iloc[i-1] <= signal_line.iloc[i-1]:
                    signals.append({
                        'date': date,
                        'type': 'MACD金叉',
                        'description': 'DIFF上穿DEA'
                    })
                elif macd.iloc[i] < signal_line.iloc[i] and macd.iloc[i-1] >= signal_line.iloc[i-1]:
                    signals.append({
                        'date': date,
                        'type': 'MACD死叉',
                        'description': 'DIFF下穿DEA'
                    })
            
            # 均线金叉死叉信号
            if i > 0:
                if ma5.iloc[i] > ma10.iloc[i] and ma5.iloc[i-1] <= ma10.iloc[i-1]:
                    signals.append({
                        'date': date,
                        'type': '均线金叉',
                        'description': 'MA5上穿MA10'
                    })
                elif ma5.iloc[i] < ma10.iloc[i] and ma5.iloc[i-1] >= ma10.iloc[i-1]:
                    signals.append({
                        'date': date,
                        'type': '均线死叉',
                        'description': 'MA5下穿MA10'
                    })
        
        return signals
    
    def generate_risk_warnings(self):
        """生成风险提示"""
        warnings = []
        
        # 计算指标
        rsi = self.calculate_rsi()
        volume_ma = self.df['volume'].rolling(window=20).mean()
        
        # 最新数据
        latest_rsi = rsi.iloc[-1]
        latest_volume = self.df['volume'].iloc[-1]
        latest_volume_ma = volume_ma.iloc[-1]
        
        # RSI风险提示
        if latest_rsi > 70:
            warnings.append("RSI接近超买区域，短期可能有调整")
        elif latest_rsi < 30:
            warnings.append("RSI处于超卖区域，可能存在反弹机会")
        
        # 量能风险提示
        if latest_volume < latest_volume_ma * 0.7:
            warnings.append("近期量能萎缩，需警惕回调风险")
        elif latest_volume > latest_volume_ma * 2:
            warnings.append("成交量异常放大，需关注资金动向")
        
        # 价格风险提示
        ma20 = self.calculate_ma(20)
        latest_price = self.df['close'].iloc[-1]
        latest_ma20 = ma20.iloc[-1]
        
        if latest_price < latest_ma20 * 0.95:
            warnings.append("股价跌破20日均线较多，短期趋势偏弱")
        
        return warnings
    
    def calculate_all_indicators(self, recent_days=5):
        """计算所有技术指标的详细数值，返回近期数据"""
        # 计算移动平均线
        ma5 = self.calculate_ma(5)
        ma10 = self.calculate_ma(10)
        ma20 = self.calculate_ma(20)
        ma60 = self.calculate_ma(60)
        
        # 计算RSI
        rsi = self.calculate_rsi()
        
        # 计算MACD
        macd, signal_line, histogram = self.calculate_macd()
        
        # 计算布林带
        bollinger_upper, bollinger_middle, bollinger_lower = self.calculate_bollinger_bands()
        
        # 计算KDJ
        kdj_k, kdj_d, kdj_j = self.calculate_kdj()

        # 计算扩展指标
        atr = self.calculate_atr()
        adx, plus_di, minus_di = self.calculate_adx()
        obv = self.calculate_obv()
        mfi = self.calculate_mfi()
        cci = self.calculate_cci()
        
        # 获取最近N天的数据
        recent_data = self.df.tail(recent_days)
        
        # 构建日期映射的指标数据
        indicators_by_date = []
        
        for i in range(len(recent_data)):
            idx = recent_data.index[i]
            date_str = recent_data.iloc[i]['date'].strftime('%Y-%m-%d %H:%M' if isinstance(recent_data.iloc[i]['date'], pd.Timestamp) and recent_data.iloc[i]['date'].hour != 0 else '%Y-%m-%d')
            
            # 构建当日指标数据
            daily_indicators = {
                "date": date_str,
                "close": round(recent_data.iloc[i]['close'], 2),
                "volume": round(recent_data.iloc[i]['volume'] / 10000, 2),  # 转换为万手
            }
            
            # 添加技术指标（如果有值的话）
            if idx < len(ma5) and not pd.isna(ma5.iloc[idx]):
                daily_indicators["ma5"] = round(ma5.iloc[idx], 2)
            if idx < len(ma10) and not pd.isna(ma10.iloc[idx]):
                daily_indicators["ma10"] = round(ma10.iloc[idx], 2)
            if idx < len(ma20) and not pd.isna(ma20.iloc[idx]):
                daily_indicators["ma20"] = round(ma20.iloc[idx], 2)
            if idx < len(ma60) and not pd.isna(ma60.iloc[idx]):
                daily_indicators["ma60"] = round(ma60.iloc[idx], 2)
            if idx < len(rsi) and not pd.isna(rsi.iloc[idx]):
                daily_indicators["rsi"] = round(rsi.iloc[idx], 2)
            if idx < len(macd) and not pd.isna(macd.iloc[idx]):
                daily_indicators["macd"] = round(macd.iloc[idx], 4)
            if idx < len(signal_line) and not pd.isna(signal_line.iloc[idx]):
                daily_indicators["macd_signal"] = round(signal_line.iloc[idx], 4)
            if idx < len(histogram) and not pd.isna(histogram.iloc[idx]):
                daily_indicators["macd_hist"] = round(histogram.iloc[idx], 4)
            if idx < len(bollinger_upper) and not pd.isna(bollinger_upper.iloc[idx]):
                daily_indicators["bollinger_upper"] = round(bollinger_upper.iloc[idx], 2)
            if idx < len(bollinger_middle) and not pd.isna(bollinger_middle.iloc[idx]):
                daily_indicators["bollinger_middle"] = round(bollinger_middle.iloc[idx], 2)
            if idx < len(bollinger_lower) and not pd.isna(bollinger_lower.iloc[idx]):
                daily_indicators["bollinger_lower"] = round(bollinger_lower.iloc[idx], 2)
            if idx < len(kdj_k) and not pd.isna(kdj_k.iloc[idx]):
                daily_indicators["kdj_k"] = round(kdj_k.iloc[idx], 2)
            if idx < len(kdj_d) and not pd.isna(kdj_d.iloc[idx]):
                daily_indicators["kdj_d"] = round(kdj_d.iloc[idx], 2)
            if idx < len(kdj_j) and not pd.isna(kdj_j.iloc[idx]):
                daily_indicators["kdj_j"] = round(kdj_j.iloc[idx], 2)
            if idx < len(atr) and not pd.isna(atr.iloc[idx]):
                daily_indicators["atr"] = round(atr.iloc[idx], 2)
            if idx < len(adx) and not pd.isna(adx.iloc[idx]):
                daily_indicators["adx"] = round(adx.iloc[idx], 2)
            if idx < len(plus_di) and not pd.isna(plus_di.iloc[idx]):
                daily_indicators["plus_di"] = round(plus_di.iloc[idx], 2)
            if idx < len(minus_di) and not pd.isna(minus_di.iloc[idx]):
                daily_indicators["minus_di"] = round(minus_di.iloc[idx], 2)
            if idx < len(obv) and not pd.isna(obv.iloc[idx]):
                daily_indicators["obv"] = round(obv.iloc[idx], 2)
            if idx < len(mfi) and not pd.isna(mfi.iloc[idx]):
                daily_indicators["mfi"] = round(mfi.iloc[idx], 2)
            if idx < len(cci) and not pd.isna(cci.iloc[idx]):
                daily_indicators["cci"] = round(cci.iloc[idx], 2)
            
            indicators_by_date.append(daily_indicators)
        
        # 同时返回简化的最新值格式（向后兼容）
        latest_idx = len(self.df) - 1
        latest_values = {}
        
        if latest_idx < len(ma5) and not pd.isna(ma5.iloc[latest_idx]):
            latest_values["ma5"] = [round(ma5.iloc[latest_idx], 2)]
        if latest_idx < len(ma10) and not pd.isna(ma10.iloc[latest_idx]):
            latest_values["ma10"] = [round(ma10.iloc[latest_idx], 2)]
        if latest_idx < len(ma20) and not pd.isna(ma20.iloc[latest_idx]):
            latest_values["ma20"] = [round(ma20.iloc[latest_idx], 2)]
        if latest_idx < len(ma60) and not pd.isna(ma60.iloc[latest_idx]):
            latest_values["ma60"] = [round(ma60.iloc[latest_idx], 2)]
        if latest_idx < len(rsi) and not pd.isna(rsi.iloc[latest_idx]):
            latest_values["rsi"] = [round(rsi.iloc[latest_idx], 2)]
        if latest_idx < len(macd) and not pd.isna(macd.iloc[latest_idx]):
            latest_values["macd"] = [round(macd.iloc[latest_idx], 4)]
        if latest_idx < len(signal_line) and not pd.isna(signal_line.iloc[latest_idx]):
            latest_values["macd_signal"] = [round(signal_line.iloc[latest_idx], 4)]
        if latest_idx < len(histogram) and not pd.isna(histogram.iloc[latest_idx]):
            latest_values["macd_hist"] = [round(histogram.iloc[latest_idx], 4)]
        if latest_idx < len(bollinger_upper) and not pd.isna(bollinger_upper.iloc[latest_idx]):
            latest_values["bollinger_upper"] = [round(bollinger_upper.iloc[latest_idx], 2)]
        if latest_idx < len(bollinger_middle) and not pd.isna(bollinger_middle.iloc[latest_idx]):
            latest_values["bollinger_middle"] = [round(bollinger_middle.iloc[latest_idx], 2)]
        if latest_idx < len(bollinger_lower) and not pd.isna(bollinger_lower.iloc[latest_idx]):
            latest_values["bollinger_lower"] = [round(bollinger_lower.iloc[latest_idx], 2)]
        if latest_idx < len(kdj_k) and not pd.isna(kdj_k.iloc[latest_idx]):
            latest_values["kdj_k"] = [round(kdj_k.iloc[latest_idx], 2)]
        if latest_idx < len(kdj_d) and not pd.isna(kdj_d.iloc[latest_idx]):
            latest_values["kdj_d"] = [round(kdj_d.iloc[latest_idx], 2)]
        if latest_idx < len(kdj_j) and not pd.isna(kdj_j.iloc[latest_idx]):
            latest_values["kdj_j"] = [round(kdj_j.iloc[latest_idx], 2)]
        if latest_idx < len(atr) and not pd.isna(atr.iloc[latest_idx]):
            latest_values["atr"] = [round(atr.iloc[latest_idx], 2)]
        if latest_idx < len(adx) and not pd.isna(adx.iloc[latest_idx]):
            latest_values["adx"] = [round(adx.iloc[latest_idx], 2)]
        if latest_idx < len(plus_di) and not pd.isna(plus_di.iloc[latest_idx]):
            latest_values["plus_di"] = [round(plus_di.iloc[latest_idx], 2)]
        if latest_idx < len(minus_di) and not pd.isna(minus_di.iloc[latest_idx]):
            latest_values["minus_di"] = [round(minus_di.iloc[latest_idx], 2)]
        if latest_idx < len(obv) and not pd.isna(obv.iloc[latest_idx]):
            latest_values["obv"] = [round(obv.iloc[latest_idx], 2)]
        if latest_idx < len(mfi) and not pd.isna(mfi.iloc[latest_idx]):
            latest_values["mfi"] = [round(mfi.iloc[latest_idx], 2)]
        if latest_idx < len(cci) and not pd.isna(cci.iloc[latest_idx]):
            latest_values["cci"] = [round(cci.iloc[latest_idx], 2)]
        
        return {
            "indicators_by_date": indicators_by_date,
            "latest_values": latest_values
        }

    def calculate_comprehensive_analysis(self, recent_points: int = 5):
        """计算综合技术分析（精简为AI友好的结构）"""
        # 均线与支撑压力
        ma20 = self.calculate_ma(20)
        ma60 = self.calculate_ma(60)
        support_20, resistance_20 = self.find_support_resistance(20)
        
        # 量价与信号
        volume_anomalies = self.find_volume_anomalies()
        technical_signals = self.find_technical_signals()
        risk_warnings = self.generate_risk_warnings()
        
        # 详细指标
        detailed_indicators = self.calculate_all_indicators(recent_days=recent_points)
        
        latest_close = round(self.df['close'].iloc[-1], 2)
        latest_volume = round(self.df['volume'].iloc[-1] / 10000, 2)
        
        return {
            "latest": {
                "close": latest_close,
                "volume": latest_volume,
                "ma20": round(ma20.iloc[-1], 2) if not pd.isna(ma20.iloc[-1]) else None,
                "ma60": round(ma60.iloc[-1], 2) if not pd.isna(ma60.iloc[-1]) else None,
                **detailed_indicators["latest_values"],
            },
            "supportResistance": {
                "period20": {
                    "support": round(support_20, 2),
                    "resistance": round(resistance_20, 2)
                }
            },
            "signals": technical_signals,
            "riskWarnings": risk_warnings,
            "recent": detailed_indicators["indicators_by_date"]
        }


def main():
    """主函数"""
    kline_json = None
    kline_data = None
    
    try:
        # 获取命令行参数
        if len(sys.argv) < 2:
            raise ValueError("缺少K线数据参数")

        input_param = sys.argv[1]
        
        # 判断输入参数是文件路径还是Base64数据
        if input_param.endswith('.json') and len(input_param) < 500:  # 可能是文件路径
            try:
                # 尝试作为文件路径读取
                import os
                if os.path.exists(input_param):
                    # 调试信息输出到stderr，避免污染stdout的JSON输出
                    print(f"从文件读取K线数据: {input_param}", file=sys.stderr)
                    with open(input_param, 'r', encoding='utf-8') as f:
                        kline_json = f.read()
                else:
                    raise FileNotFoundError(f"文件不存在: {input_param}")
            except Exception as e:
                raise ValueError(f"读取文件失败: {str(e)}")
        else:
            # 作为Base64数据处理
            print("从Base64参数读取K线数据", file=sys.stderr)
            
            # 检查Base64数据大小
            if len(input_param) > 200000:  # 200KB限制
                raise ValueError(f"输入数据过大: {len(input_param)} 字符，请减少数据量")

            # 解码Base64
            try:
                decoded_bytes = base64.b64decode(input_param)
                kline_json = decoded_bytes.decode('utf-8')
            except Exception as e:
                raise ValueError(f"Base64解码失败: {str(e)}")
        
        # 检查JSON大小
        if len(kline_json) > 2000000:  # 2MB限制
            raise ValueError(f"JSON数据过大: {len(kline_json)} 字符，请减少数据量")

        # 解析JSON
        try:
            parsed = json.loads(kline_json)
        except Exception as e:
            raise ValueError(f"JSON解析失败: {str(e)}")

        # 接受两种输入：
        # 1) 单一时间框架：数组形式的K线
        # 2) 多时间框架：对象形式，包含 day / 60m / 5m 键
        output = { "timeframes": {} }

        def compute_for_series(series, label: str):
            if not isinstance(series, list) or len(series) == 0:
                return
            # 裁剪过大数据
            data_series = series[-1000:] if len(series) > 1000 else series
            calc = TechnicalIndicatorCalculator(data_series)
            tf_result = calc.calculate_comprehensive_analysis(recent_points=5 if label == 'day' else 20 if label == '60m' else 48)
            output["timeframes"][label] = tf_result

        if isinstance(parsed, dict):
            for key in ["day", "60m", "5m"]:
                if key in parsed:
                    compute_for_series(parsed[key], key)
        elif isinstance(parsed, list):
            compute_for_series(parsed, "day")
        else:
            raise ValueError("输入数据格式不支持，应为数组或包含 day/60m/5m 的对象")

        # 向后兼容：若存在 day，则在顶层补充旧字段
        if "day" in output["timeframes"]:
            day_tf = output["timeframes"]["day"]
            print("技术指标计算完成(day)", file=sys.stderr)
            # 顶层镜像
            print(json.dumps({
                "timeframes": output["timeframes"],
                "详细指标": day_tf.get("latest", {}),
                "近5日指标": day_tf.get("recent", [])
            }, ensure_ascii=False))
            return

        # 若无 day，直接输出 timeframes
        print("技术指标计算完成", file=sys.stderr)
        print(json.dumps(output, ensure_ascii=False))

    except Exception as e:
        # 构建错误信息，但不包含大量数据避免输出过长
        error_result = {
            "error": "技术指标计算失败",
            "message": str(e),
            "data_info": {
                "kline_json_length": len(kline_json) if kline_json else 0,
                "kline_data_length": len(kline_data) if kline_data and isinstance(kline_data, list) else 0,
                "input_param_length": len(sys.argv[1]) if len(sys.argv) > 1 else 0,
                "input_type": "file" if len(sys.argv) > 1 and sys.argv[1].endswith('.json') else "base64"
            }
        }
        print(json.dumps(error_result, ensure_ascii=False))


if __name__ == "__main__":
    main()