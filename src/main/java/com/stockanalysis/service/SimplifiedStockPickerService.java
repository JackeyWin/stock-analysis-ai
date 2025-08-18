package com.stockanalysis.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 简化版选股服务
 */
@Slf4j
@Service
public class SimplifiedStockPickerService {

    private final PolicyHotspotService policyHotspotService;
    private final PythonScriptService pythonScriptService;
    private final StockScreeningService stockScreeningService;

    public SimplifiedStockPickerService(PolicyHotspotService policyHotspotService, 
                                     PythonScriptService pythonScriptService,
                                     StockScreeningService stockScreeningService) {
        this.policyHotspotService = policyHotspotService;
        this.pythonScriptService = pythonScriptService;
        this.stockScreeningService = stockScreeningService;
    }

    public PolicyHotspotService getPolicyHotspotService() {
        return policyHotspotService;
    }

    /**
     * 简化版选股流程
     */
    public CompletableFuture<Map<String, Object>> pickStocks() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("开始简化版选股流程");
                
                Map<String, Object> result = new HashMap<>();
                
                // // 1. 获取最新政策面信息（一周之内）
                // Map<String, String> hotspots = policyHotspotService.getPolicyAndIndustryHotspots().get();
                // result.put("hotspots", hotspots);
                
                // // 2. 匹配政策受益行业
                // Map<String, String> sectorBenefits = matchPolicyBenefitSectors(hotspots);
                // result.put("sectorBenefits", sectorBenefits);
                
                // // 3. 获取受益行业的股票池
                // List<Map<String, Object>> stockPool = getBenefitSectorStocks(sectorBenefits);
                // result.put("stockPool", stockPool);
                
                // // 4. 使用现有筛选逻辑筛选股票
                // List<Map<String, Object>> screenedStocks = stockPool.stream()
                //         .filter(Objects::nonNull)
                //         .filter(stock -> {
                //             Object scoreObj = stock.get("score");
                //             if (scoreObj instanceof Number) {
                //                 return ((Number) scoreObj).doubleValue() >= 7.0;
                //             }
                //             return false;
                //         })
                //         .sorted((a, b) -> {
                //             Double scoreA = getDoubleValue(a.get("score"));
                //             Double scoreB = getDoubleValue(b.get("score"));
                //             return Double.compare(scoreB != null ? scoreB : 0.0, scoreA != null ? scoreA : 0.0);
                //         })
                //         .limit(50)
                //         .collect(Collectors.toList());
                
                // result.put("screenedStocks", screenedStocks);
                
                // log.info("简化版选股流程完成，共筛选出{}只股票", screenedStocks.size());
                return result;
                
            } catch (Exception e) {
                log.error("简化版选股流程失败: {}", e.getMessage(), e);
                throw new RuntimeException("选股失败: " + e.getMessage());
            }
        });
    }

    /**
     * 匹配政策受益行业
     * 输出一个map，key是行业名，value是利好因素，支持多因素叠加
     */
    private Map<String, String> matchPolicyBenefitSectors(Map<String, Object> hotspots) {
        try {
            // 获取所有行业列表
            List<String> allSectors = getAllSectors();
            
            // 从热点信息中提取政策热点
            String policyHotspots = (String) hotspots.get("policyHotspots");
            String industryHotspots = (String) hotspots.get("industryHotspots");
            String marketHotspots = (String) hotspots.get("marketHotspots");
            
            Map<String, String> sectorBenefits = new HashMap<>();
            
            // 匹配政策受益行业
            for (String sector : allSectors) {
                List<String> benefits = new ArrayList<>();
                
                // 检查是否与政策热点相关
                if (policyHotspots != null && policyHotspots.contains(sector)) {
                    benefits.add("政策支持");
                }
                
                // 检查是否与行业热点相关
                if (industryHotspots != null && industryHotspots.contains(sector)) {
                    benefits.add("行业景气度提升");
                }
                
                // 检查是否与市场热点相关
                if (marketHotspots != null && marketHotspots.contains(sector)) {
                    benefits.add("市场关注度高");
                }
                
                // 如果有匹配的利好因素，则加入结果
                if (!benefits.isEmpty()) {
                    sectorBenefits.put(sector, String.join(", ", benefits));
                }
            }
            
            log.info("匹配到{}个政策受益行业", sectorBenefits.size());
            return sectorBenefits;
            
        } catch (Exception e) {
            log.error("匹配政策受益行业失败: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * 获取所有行业列表
     */
    private List<String> getAllSectors() {
        try {
            // 调用Python脚本获取行业列表
            String result = pythonScriptService.executePythonScript("EastMoneySectorStocks.py", "--list");
            
            // 简单解析结果，按行分割
            if (result != null && !result.isEmpty()) {
                return Arrays.asList(result.split("\n"));
            }
            
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("获取行业列表失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取受益行业的股票池
     */
    private List<Map<String, Object>> getBenefitSectorStocks(Map<String, String> sectorBenefits) {
        try {
            List<Map<String, Object>> allStocks = new ArrayList<>();
            
            // 遍历受益行业，获取股票池
            for (Map.Entry<String, String> entry : sectorBenefits.entrySet()) {
                String sector = entry.getKey();
                
                try {
                    // 调用Python脚本获取行业成分股
                    List<Map<String, Object>> stocks = pythonScriptService.getSectorStocks(sector);
                    
                    // 为每只股票添加行业和利好因素信息
                    for (Map<String, Object> stock : stocks) {
                        stock.put("sector", sector);
                        stock.put("benefits", entry.getValue());
                        
                        // 评估股票
                        Map<String, Object> evaluatedStock = evaluateStock((String) stock.get("code"));
                        if (evaluatedStock != null) {
                            stock.putAll(evaluatedStock);
                        }
                        
                        allStocks.add(stock);
                    }
                } catch (Exception e) {
                    log.warn("获取行业{}成分股失败: {}", sector, e.getMessage());
                }
            }
            
            log.info("共获取到{}只受益行业股票", allStocks.size());
            return allStocks;
            
        } catch (Exception e) {
            log.error("获取受益行业股票池失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 评估股票
     */
    private Map<String, Object> evaluateStock(String stockCode) {
        return stockScreeningService.evaluateStock(stockCode);
    }

    /**
     * 安全地获取Double值
     */
    private Double getDoubleValue(Object value) {
        if (value == null) {
            return null;
        }
        
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        return null;
    }
}