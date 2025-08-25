import React from 'react';
import { render } from '@testing-library/react-native';
import AnalysisResultView from '../AnalysisResultView';

// Mock the MermaidChart component
jest.mock('../MermaidChart', () => {
  return function MockMermaidChart({ code, title }) {
    return null; // Mock component returns null for testing
  };
});

describe('AnalysisResultView - 盘面分析格式解析', () => {
  const mockResult = {
    aiAnalysisResult: {
      intradayOperations: `- [H] 盘前作战指南
- [S] 隔夜信号：美股消费电子板块+3.2%，北向盘前溢价0.8%
- [S] 关键点位：支撑￥38.50（BOLL中轨）/压力￥40.36（前高）
- [S] 操作预案：高开＞1%量比＞1.5 ▶ 追涨5%仓位；低开破￥38.50观察30分钟反抽

- [H] 盘中动态操作
- [S] 动态策略：突破￥39.60量比＞1.2 ▶ 加仓10%；跌破￥38.80放量 ▶ 减仓20%
- [S] 主力监测：10:00-10:30出现3笔万手买单 ▶ 跟涨（成功率82%）
- [S] 特情警报：14:30大宗解禁500万股（冲击系数0.8）

- [H] 盘后总结
- [S] 今日复盘：主力净流出6.3亿，融资逆势增1.3亿，多空分歧明显
- [S] 明日计划：重点观察￥39.00支撑，守稳则 ▶ 加仓至60%
- [S] 仓位上限：基础仓位30%×情绪系数1.2×技术系数1.0=36%`
    }
  };

  it('应该正确解析盘面分析的主标题格式', () => {
    const { getByText } = render(
      <AnalysisResultView 
        result={mockResult} 
        stockCode="000001" 
        onClose={() => {}} 
      />
    );

    // 检查主标题是否正确显示
    expect(getByText('盘前作战指南')).toBeTruthy();
    expect(getByText('盘中动态操作')).toBeTruthy();
    expect(getByText('盘后总结')).toBeTruthy();
  });

  it('应该正确解析盘面分析的子标题格式', () => {
    const { getByText } = render(
      <AnalysisResultView 
        result={mockResult} 
        stockCode="000001" 
        onClose={() => {}} 
      />
    );

    // 检查子标题是否正确显示
    expect(getByText('隔夜信号：美股消费电子板块+3.2%，北向盘前溢价0.8%')).toBeTruthy();
    expect(getByText('关键点位：支撑￥38.50（BOLL中轨）/压力￥40.36（前高）')).toBeTruthy();
    expect(getByText('操作预案：高开＞1%量比＞1.5 ▶ 追涨5%仓位；低开破￥38.50观察30分钟反抽')).toBeTruthy();
  });

  it('应该正确解析操作指令（▶符号）', () => {
    const { getByText } = render(
      <AnalysisResultView 
        result={mockResult} 
        stockCode="000001" 
        onClose={() => {}} 
      />
    );

    // 检查操作指令是否正确显示
    expect(getByText('高开＞1%量比＞1.5 ▶ 追涨5%仓位；低开破￥38.50观察30分钟反抽')).toBeTruthy();
    expect(getByText('突破￥39.60量比＞1.2 ▶ 加仓10%；跌破￥38.80放量 ▶ 减仓20%')).toBeTruthy();
  });

  it('应该正确解析价格信息（￥符号）', () => {
    const { getByText } = render(
      <AnalysisResultView 
        result={mockResult} 
        stockCode="000001" 
        onClose={() => {}} 
      />
    );

    // 检查价格信息是否正确显示
    expect(getByText('支撑￥38.50（BOLL中轨）/压力￥40.36（前高）')).toBeTruthy();
    expect(getByText('突破￥39.60量比＞1.2 ▶ 加仓10%')).toBeTruthy();
    expect(getByText('重点观察￥39.00支撑，守稳则 ▶ 加仓至60%')).toBeTruthy();
  });

  it('应该正确解析时间信息（XX:XX格式）', () => {
    const { getByText } = render(
      <AnalysisResultView 
        result={mockResult} 
        stockCode="000001" 
        onClose={() => {}} 
      />
    );

    // 检查时间信息是否正确显示
    expect(getByText('10:00-10:30出现3笔万手买单 ▶ 跟涨（成功率82%）')).toBeTruthy();
    expect(getByText('14:30大宗解禁500万股（冲击系数0.8）')).toBeTruthy();
  });

  it('应该正确解析成功率信息', () => {
    const { getByText } = render(
      <AnalysisResultView 
        result={mockResult} 
        stockCode="000001" 
        onClose={() => {}} 
      />
    );

    // 检查成功率信息是否正确显示
    expect(getByText('10:00-10:30出现3笔万手买单 ▶ 跟涨（成功率82%）')).toBeTruthy();
  });

  it('应该正确解析仓位计算公式', () => {
    const { getByText } = render(
      <AnalysisResultView 
        result={mockResult} 
        stockCode="000001" 
        onClose={() => {}} 
      />
    );

    // 检查仓位计算公式是否正确显示
    expect(getByText('基础仓位30%×情绪系数1.2×技术系数1.0=36%')).toBeTruthy();
  });

  it('应该正确解析百分比信息', () => {
    const { getByText } = render(
      <AnalysisResultView 
        result={mockResult} 
        stockCode="000001" 
        onClose={() => {}} 
      />
    );

    // 检查百分比信息是否正确显示
    expect(getByText('美股消费电子板块+3.2%，北向盘前溢价0.8%')).toBeTruthy();
    expect(getByText('高开＞1%量比＞1.5 ▶ 追涨5%仓位')).toBeTruthy();
    expect(getByText('突破￥39.60量比＞1.2 ▶ 加仓10%')).toBeTruthy();
  });

  it('应该正确显示多路径决策树', () => {
    const mockResultWithMermaid = {
      aiAnalysisResult: {
        intradayOperations: `- [H] 盘中动态操作
- [S] 多路径决策树（实时更新）
\`\`\`mermaid
graph TB
    A[价格突破￥39.60？] -->|是| B[量能验证]
    A -->|否| C[是否回踩￥38.80？]
    B -->|量比＞1.2| D[▶ 加仓10% \u2192 目标￥40.50]
    B -->|量比＜1.0| E[▶ 减仓20% \u2192 观察二次攻击]
    C -->|缩量至70%| F[▶ 金字塔补仓]
    C -->|放量破位| G[▶ 止损]
\`\`\`
- [S] 动态策略：突破￥39.60量比＞1.2 ▶ 加仓10%`
      }
    };

    const { getByText } = render(
      <AnalysisResultView 
        result={mockResultWithMermaid} 
        stockCode="000001" 
        onClose={() => {}} 
      />
    );

    // 检查多路径决策树标题是否正确显示
    expect(getByText('多路径决策树（实时更新）')).toBeTruthy();
    expect(getByText('盘中动态操作')).toBeTruthy();
  });
});
