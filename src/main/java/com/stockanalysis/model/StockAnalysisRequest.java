package com.stockanalysis.model;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;

/**
 * 股票分析请求模型
 */
@Data
public class StockAnalysisRequest {
    
    @NotBlank(message = "股票代码不能为空")
    @Pattern(regexp = "^[0-9]{6}$", message = "股票代码格式不正确，应为6位数字")
    private String stockCode;
    
    private Integer days = 250;  // 默认获取250天数据
    
    private String machineId = "default";  // 机器标识，默认为default

    private LocalDateTime analysisStartTime = LocalDateTime.now();
}