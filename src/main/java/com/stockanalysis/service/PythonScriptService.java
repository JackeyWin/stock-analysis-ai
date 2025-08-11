package com.stockanalysis.service;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Python脚本调用服务
 */
@Slf4j
@Service
public class PythonScriptService {

    @Value("${python.scripts.path:python_scripts/}")
    private String scriptsPath;

    @Value("${python.scripts.interpreter:python}")
    private String pythonInterpreter;
    
    @Value("${technical.indicators.max.days:250}")
    private int maxDaysForTechnicalIndicators;
    
    @Value("${technical.indicators.max.json.size:50000}")
    private int maxJsonSizeForTechnicalIndicators;

    private final ObjectMapper objectMapper;

    public PythonScriptService(ObjectMapper objectMapper) {
//        objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        this.objectMapper = objectMapper;
    }

    /**
     * 清理字符串中的无效字符
     */
    private String cleanInvalidCharacters(String input) {
        if (input == null) {
            return null;
        }

        // 移除替换字符 (U+FFFD) 和其他控制字符
        return input.replaceAll("[\uFFFD\u0000-\u001F\u007F-\u009F]", "")
                   .replaceAll("�", "")  // 移除乱码字符
                   .trim();
    }

    /**
     * Unicode字符转译和清理
     */
    private String processUnicodeString(String input) {
        if (input == null) {
            return null;
        }

        try {
            // 1. 处理可能的Unicode转义序列
            input = unescapeUnicode(input);

            // 2. 重新编码为UTF-8
            byte[] bytes = input.getBytes("UTF-8");
            input = new String(bytes, "UTF-8");

            // 3. 移除无效字符
            input = input.replaceAll("[\uFFFD\u0000-\u001F\u007F-\u009F]", "")
                        .replaceAll("�", "")
                        .trim();

            return input;
        } catch (Exception e) {
            log.warn("Unicode字符处理失败: {}", e.getMessage());
            return cleanInvalidCharacters(input);
        }
    }

    /**
     * 解码Unicode转义序列
     */
    private String unescapeUnicode(String input) {
        if (input == null || !input.contains("\\u")) {
            return input;
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            if (i < input.length() - 5 && input.charAt(i) == '\\' && input.charAt(i + 1) == 'u') {
                try {
                    // 解析Unicode转义序列
                    String hex = input.substring(i + 2, i + 6);
                    int codePoint = Integer.parseInt(hex, 16);
                    result.append((char) codePoint);
                    i += 6;
                } catch (NumberFormatException e) {
                    // 如果不是有效的Unicode转义，保持原样
                    result.append(input.charAt(i));
                    i++;
                }
            } else {
                result.append(input.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    /**
     * 清理JSON字符串
     */
    private String cleanJsonString(String jsonString) {
        if (jsonString == null) {
            return null;
        }

        // 1. Unicode处理
        jsonString = processUnicodeString(jsonString);

        // 2. 移除BOM标记
        if (jsonString.startsWith("\uFEFF")) {
            jsonString = jsonString.substring(1);
        }

        // 3. 移除可能的前缀文本（如Python的print输出）
        jsonString = jsonString.trim();

        // 4. 找到JSON开始位置
        int jsonStart = -1;
        for (int i = 0; i < jsonString.length(); i++) {
            char c = jsonString.charAt(i);
            if (c == '[' || c == '{') {
                jsonStart = i;
                break;
            }
        }

        if (jsonStart > 0) {
            jsonString = jsonString.substring(jsonStart);
        }

        // 5. 找到JSON结束位置
        int jsonEnd = -1;
        for (int i = jsonString.length() - 1; i >= 0; i--) {
            char c = jsonString.charAt(i);
            if (c == ']' || c == '}') {
                jsonEnd = i + 1;
                break;
            }
        }

        if (jsonEnd > 0 && jsonEnd < jsonString.length()) {
            jsonString = jsonString.substring(0, jsonEnd);
        }

        return jsonString;
    }

    /**
     * 执行Python脚本
     */
    private String executePythonScript(String scriptName, String... args) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();

        // 构建命令
        String[] command = new String[args.length + 2];
        command[0] = pythonInterpreter;
        command[1] = scriptsPath + scriptName;
        System.arraycopy(args, 0, command, 2, args.length);

        processBuilder.command(command);
        // 不重定向错误流，保持stdout和stderr分离
        // processBuilder.redirectErrorStream(true);

        // 设置环境变量确保Python输出UTF-8编码
        processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
        processBuilder.environment().put("LANG", "zh_CN.UTF-8");
        processBuilder.environment().put("LC_ALL", "zh_CN.UTF-8");

        log.debug("执行Python脚本: {}", String.join(" ", command));

        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();

        // 分别处理标准输出和错误输出
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
             BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), "UTF-8"))) {

            // 使用线程并行读取stdout和stderr，避免阻塞
            Thread outputThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 处理Unicode字符和清理乱码
                        line = processUnicodeString(line);
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    log.warn("读取标准输出时出错: {}", e.getMessage());
                }
            });

            Thread errorThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                } catch (IOException e) {
                    log.warn("读取错误输出时出错: {}", e.getMessage());
                }
            });

            outputThread.start();
            errorThread.start();

            // 等待两个线程完成
            outputThread.join();
            errorThread.join();
        }

        boolean finished = process.waitFor(60, TimeUnit.SECONDS); // 增加超时时间
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Python脚本执行超时");
        }

        int exitCode = process.exitValue();
        
        // 记录stderr输出（通常是日志信息）
        if (errorOutput.length() > 0) {
            log.debug("Python脚本stderr输出: {}", errorOutput.toString());
        }
        
        if (exitCode != 0) {
            String errorMsg = errorOutput.length() > 0 ? errorOutput.toString() : output.toString();
            log.error("Python脚本执行失败，退出码: {}, 错误输出: {}", exitCode, errorMsg);
            throw new RuntimeException("Python脚本执行失败: " + errorMsg);
        }

        String result = output.toString().trim();
        log.debug("Python脚本执行成功，输出长度: {}", result.length());

        return result;
    }

    /**
     * 获取个股K线数据
     */
    public List<Map<String, Object>> getStockKlineData(String stockCode) {
        try {
            String result = executePythonScript("EastMoneyTickHistoryKline.py", stockCode);

            // 验证JSON格式
            if (result == null || result.trim().isEmpty()) {
                throw new RuntimeException("Python脚本返回空结果");
            }

            // 进一步清理JSON字符串
            result = cleanJsonString(result);

            log.debug("清理后的JSON长度: {}, 前100字符: {}", result.length(),
                     result.length() > 100 ? result.substring(0, 100) : result);

            return objectMapper.readValue(result, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("获取个股K线数据失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取个股K线数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取大盘K线数据
     */
    public List<Map<String, Object>> getMarketKlineData(String stockCode) {
        try {
            String result = executePythonScript("EastMoneyMarketHistoryKline.py", stockCode);
            return objectMapper.readValue(result, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("获取大盘K线数据失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取大盘K线数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取板块K线数据
     */
    public List<Map<String, Object>> getBoardKlineData(String stockCode) {
        try {
            String result = executePythonScript("EastMoneyBoardHistoryKline.py", stockCode);
            List<Map<String, Object>> response = objectMapper.readValue(result, new TypeReference<List<Map<String, Object>>>() {});
            return response;
        } catch (Exception e) {
            log.error("获取板块K线数据失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取板块K线数据失败: " + e.getMessage());
        }
    }

    /**
     * 计算技术指标
     */
    public Map<String, Object> calculateTechnicalIndicators(List<Map<String, Object>> klineData) {
        Path tempFile = null;
        try {
            // 限制数据量，只使用最近的数据进行计算
            List<Map<String, Object>> limitedData = limitKlineDataSize(klineData);
            
            log.debug("原始K线数据量: {}, 限制后数据量: {}", klineData.size(), limitedData.size());
            
            String klineJson = objectMapper.writeValueAsString(limitedData);
            
            // 检查JSON大小，如果过大则进一步缩减
            if (klineJson.length() > maxJsonSizeForTechnicalIndicators) {
                log.warn("K线数据过大 ({}字符)，进一步缩减数据量", klineJson.length());
                limitedData = limitKlineDataSize(limitedData, 120); // 进一步限制为120天
                klineJson = objectMapper.writeValueAsString(limitedData);
            }
            
            // 使用临时文件传递数据，避免命令行参数过长的问题
            tempFile = createTempFileWithData(klineJson);
            log.debug("创建临时文件: {}, 大小: {} 字符", tempFile.toString(), klineJson.length());
            
            String result = executePythonScript("TechnicalIndicators.py", tempFile.toString());
            return objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("计算技术指标失败: {}", e.getMessage(), e);
            throw new RuntimeException("计算技术指标失败: " + e.getMessage());
        } finally {
            // 清理临时文件
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    log.debug("删除临时文件: {}", tempFile.toString());
                } catch (IOException e) {
                    log.warn("删除临时文件失败: {}", e.getMessage());
                }
            }
        }
    }
    
    /**
     * 创建临时文件并写入数据
     */
    private Path createTempFileWithData(String data) throws IOException {
        Path tempFile = Files.createTempFile("kline_data_", ".json");
        Files.write(tempFile, data.getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE);
        return tempFile;
    }
    
    /**
     * 限制K线数据大小，只保留最近的数据
     */
    private List<Map<String, Object>> limitKlineDataSize(List<Map<String, Object>> klineData) {
        return limitKlineDataSize(klineData, maxDaysForTechnicalIndicators);
    }
    
    /**
     * 限制K线数据大小，只保留最近的数据
     */
    private List<Map<String, Object>> limitKlineDataSize(List<Map<String, Object>> klineData, int maxDays) {
        if (klineData == null || klineData.size() <= maxDays) {
            return klineData;
        }
        
        // 按日期排序（假设数据已经按日期排序）
        // 取最后maxDays条数据
        int startIndex = klineData.size() - maxDays;
        List<Map<String, Object>> limitedData = new ArrayList<>(klineData.subList(startIndex, klineData.size()));
        
        log.info("K线数据从 {} 条限制为 {} 条", klineData.size(), limitedData.size());
        return limitedData;
    }

    /**
     * 获取新闻数据
     */
    public List<Map<String, Object>> getNewsData(String stockCode) {
        try {
            String result = executePythonScript("EasyMoneyNewsData.py", stockCode);
            
            // 验证JSON格式
            if (result == null || result.trim().isEmpty()) {
                throw new RuntimeException("Python脚本返回空结果");
            }
            
            // 清理JSON字符串，移除可能的日志输出
            result = cleanJsonString(result);
            
            log.debug("清理后的新闻JSON长度: {}, 前100字符: {}", result.length(),
                     result.length() > 100 ? result.substring(0, 100) : result);
            
            return objectMapper.readValue(result, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("获取新闻数据失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取新闻数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取资金流向数据
     */
    public List<Map<String, Object>> getMoneyFlowData(String stockCode) {
        try {
            String result = executePythonScript("EastMoneyFundFlow.py", stockCode);
            return objectMapper.readValue(result, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("获取资金流向数据失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取资金流向数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取融资融券数据
     */
    public List<Map<String, Object>> getMarginTradingData(String stockCode) {
        try {
            String result = executePythonScript("EastMoneyRZRQData.py", stockCode);
            return objectMapper.readValue(result, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("获取融资融券数据失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取融资融券数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取分时数据精炼分析
     */
    public Map<String, Object> getIntradayAnalysis(String stockCode) {
        try {
            log.debug("开始获取股票 {} 的分时数据分析", stockCode);
            String result = executePythonScript("IntradayAnalysis.py", stockCode);
            
            // 验证JSON格式
            if (result == null || result.trim().isEmpty()) {
                log.warn("股票 {} 没有分时数据，返回空结果", stockCode);
                return new HashMap<>(); // 返回空结果而不是报错
            }
            
            // 清理JSON字符串
            result = cleanJsonString(result);
            
            Map<String, Object> analysisResult = objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {});
            
            // 检查是否有错误
            if (analysisResult.containsKey("error")) {
                log.warn("股票 {} 分时数据分析有错误: {}，返回空结果", stockCode, analysisResult.get("error"));
                return new HashMap<>(); // 返回空结果而不是报错
            }
            
            log.debug("分时数据分析完成，股票代码: {}", stockCode);
            return analysisResult;
            
        } catch (Exception e) {
            log.warn("获取股票 {} 分时数据分析失败: {}，返回空结果", stockCode, e.getMessage());
            return new HashMap<>(); // 返回空结果而不是报错
        }
    }
}