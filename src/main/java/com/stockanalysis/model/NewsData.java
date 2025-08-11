package com.stockanalysis.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 新闻数据模型
 */
@Data
public class NewsData {
    
    private String title;           // 新闻标题
    private String content;         // 新闻内容
    private String summary;         // 新闻摘要
    private String source;          // 新闻来源
    private LocalDateTime publishTime; // 发布时间
    private String url;             // 新闻链接
    
    // 情感分析相关字段
    private String sentiment;       // 情感倾向（利好/利空/中性）
    private Double sentimentScore;  // 情感评分 (-100 到 100)
    private List<String> positiveKeywords; // 利好关键词
    private List<String> negativeKeywords; // 利空关键词
    private String analysisSummary; // 分析摘要
}