package com.stockanalysis.model;

import lombok.Data;

import java.util.List;

/**
 * 资金流向数据模型
 */
@Data
public class MoneyFlowData {
    
    private List<DailyMoneyFlow> dailyFlows;  // 每日资金流向
    
    @Data
    public static class DailyMoneyFlow {
        private String date;                    // 日期
        private Double mainInflow;              // 主力净流入
        private Double retailInflow;            // 散户净流入
        private Double institutionInflow;       // 机构净流入
        private Double totalInflow;             // 总净流入
        private Double inflowRatio;             // 净流入占比
    }
}