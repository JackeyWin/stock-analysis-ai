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

    // 新闻情感分析配置
    @Value("${news.sentiment.provider:keyword}")
    private String newsSentimentProvider;
    
    @Value("${news.sentiment.crawl-content:true}")
    private boolean newsCrawlContent;
    
    @Value("${news.sentiment.max-pages:1}")
    private int newsMaxPages;

    @Value("${deepseek.api.key:sk-2c60a0afb4004678be7c5703e940d360}")
    private String deepseekApiKey;

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
     * 获取行业成分股数据
     * @param sectorName 行业名称
     * @return 行业成分股列表
     */
    public List<Map<String, Object>> getSectorStocks(String sectorName) {
        try {
            log.info("获取行业 {} 的成分股数据", sectorName);
            String result = executePythonScript("EastMoneySectorStocks.py", "--name", sectorName);
            
            if (result == null || result.isEmpty()) {
                log.warn("获取行业 {} 成分股数据失败: 返回结果为空", sectorName);
                return new ArrayList<>();
            }
            
            // 清理JSON字符串
            result = cleanJsonString(result);
            if (result == null || result.isEmpty()) {
                log.warn("获取行业 {} 成分股数据失败: 清理后的结果为空", sectorName);
                return new ArrayList<>();
            }
            
            // 解析JSON
            Map<String, Object> data = objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {});
            
            // 获取股票列表
            Object stocksObj = data.get("stocks");
            if (stocksObj instanceof List) {
                return (List<Map<String, Object>>) stocksObj;
            }
            
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("获取行业 {} 成分股数据失败: {}", sectorName, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取全部行业列表（代码与名称）
     * 调用 EastMoneySectorStocks.py --list
     */
    public List<Map<String, String>> getSectorList() {
        try {
            String result = executePythonScript("EastMoneySectorStocks.py", "--list");
            if (result == null || result.isEmpty()) {
                log.warn("获取行业列表失败: 返回为空");
                return new ArrayList<>();
            }

            // 清理可能的杂质并解析
            result = cleanJsonString(result);
            List<Map<String, String>> sectors = objectMapper.readValue(result, new TypeReference<List<Map<String, String>>>() {});
            return sectors;
        } catch (Exception e) {
            log.warn("获取行业列表失败: {}，返回空列表", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 执行Python脚本
     */
    public String executePythonScript(String scriptName, String... args) throws IOException, InterruptedException {
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

        // 仅记录脚本名，避免泄露敏感参数
        if (log.isDebugEnabled()) {
            log.debug("执行Python脚本: {} ... (参数已省略)", scriptName);
        }

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
        
        // 如有需要可在TRACE级别查看stderr，默认不打印
        if (errorOutput.length() > 0 && log.isTraceEnabled()) {
            log.trace("Python脚本stderr输出: {}", errorOutput.toString());
        }
        
        if (exitCode != 0) {
            String errorMsg = errorOutput.length() > 0 ? errorOutput.toString() : output.toString();
            // 不回显完整错误输出到日志，避免噪音/敏感信息
            log.error("Python脚本执行失败，退出码: {}", exitCode);
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
            log.warn("获取个股K线数据失败: {}，返回空列表", e.getMessage());
            return new ArrayList<>(); // 返回空列表而不是报错
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
            log.warn("获取大盘K线数据失败: {}，返回空列表", e.getMessage());
            return new ArrayList<>(); // 返回空列表而不是报错
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
            log.warn("获取板块K线数据失败: {}，返回空列表", e.getMessage());
            return new ArrayList<>(); // 返回空列表而不是报错
        }
    }

    /**
     * 获取大盘当日分时数据
     */
    public List<Map<String, Object>> getMarketTrendsToday(String stockCode) {
        try {
            String result = executePythonScript("EastMoneyMarketStockTrendsToday.py", stockCode);
            if (result == null || result.trim().isEmpty()) {
                log.warn("获取大盘当日分时数据失败: 脚本返回空结果");
                return new ArrayList<>();
            }

            // 尝试解析JSON，处理可能的包装结构
            ObjectMapper mapper = new ObjectMapper();
            Object parsed = mapper.readValue(result, Object.class);
            
            List<Map<String, Object>> marketData;
            
            if (parsed instanceof List) {
                // 直接是数组
                marketData = (List<Map<String, Object>>) parsed;
            } else if (parsed instanceof Map) {
                // 是包装对象，尝试提取数据
                @SuppressWarnings("unchecked")
                Map<String, Object> wrapper = (Map<String, Object>) parsed;
                
                // 尝试常见的键名
                Object data = wrapper.get("data");
                if (data == null) {
                    data = wrapper.get("result");
                }
                if (data == null) {
                    data = wrapper.get("market_data");
                }
                if (data == null) {
                    data = wrapper.get("trends");
                }
                
                if (data instanceof List) {
                    marketData = (List<Map<String, Object>>) data;
                } else {
                    log.warn("无法从包装对象中提取大盘分时数据，键: {}", wrapper.keySet());
                    return new ArrayList<>();
                }
            } else {
                log.warn("获取大盘当日分时数据失败: 未知的数据类型 {}", parsed.getClass().getSimpleName());
                return new ArrayList<>();
            }

            // 转换字段名为中文
            return convertMarketTrendsToChinese(marketData);
            
        } catch (Exception e) {
            log.error("获取大盘当日分时数据失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取板块当日分时数据
     */
    public List<Map<String, Object>> getBoardTrendsToday(String stockCode) {
        try {
            String result = executePythonScript("EastMoneyBoardStockTrendsToday.py", stockCode);
            if (result == null || result.trim().isEmpty()) {
                log.warn("获取板块当日分时数据失败: 脚本返回空结果");
                return new ArrayList<>();
            }

            // 尝试解析JSON，处理可能的包装结构
            ObjectMapper mapper = new ObjectMapper();
            Object parsed = mapper.readValue(result, Object.class);
            
            List<Map<String, Object>> boardData;
            
            if (parsed instanceof List) {
                // 直接是数组
                boardData = (List<Map<String, Object>>) parsed;
            } else if (parsed instanceof Map) {
                // 是包装对象，尝试提取数据
                @SuppressWarnings("unchecked")
                Map<String, Object> wrapper = (Map<String, Object>) parsed;
                
                // 尝试常见的键名
                Object data = wrapper.get("data");
                if (data == null) {
                    data = wrapper.get("result");
                }
                if (data == null) {
                    data = wrapper.get("board_data");
                }
                if (data == null) {
                    data = wrapper.get("trends");
                }
                
                if (data instanceof List) {
                    boardData = (List<Map<String, Object>>) data;
                } else {
                    log.warn("无法从包装对象中提取板块分时数据，键: {}", wrapper.keySet());
                    return new ArrayList<>();
                }
            } else {
                log.warn("获取板块当日分时数据失败: 未知的数据类型 {}", parsed.getClass().getSimpleName());
                return new ArrayList<>();
            }

            // 转换字段名为中文
            return convertBoardTrendsToChinese(boardData);
            
        } catch (Exception e) {
            log.error("获取板块当日分时数据失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取个股当日分时数据
     */
    public List<Map<String, Object>> getStockTrendsToday(String stockCode) {
        try {
            String result = executePythonScript("EastMoneyStockTrendsToday.py", stockCode);
            if (result == null || result.trim().isEmpty()) {
                log.warn("获取个股当日分时数据失败: 脚本返回空结果");
                return new ArrayList<>();
            }

            // 尝试解析JSON，处理可能的包装结构
            ObjectMapper mapper = new ObjectMapper();
            Object parsed = mapper.readValue(result, Object.class);
            
            List<Map<String, Object>> stockData;
            
            if (parsed instanceof List) {
                // 直接是数组
                stockData = (List<Map<String, Object>>) parsed;
            } else if (parsed instanceof Map) {
                // 是包装对象，尝试提取数据
                @SuppressWarnings("unchecked")
                Map<String, Object> wrapper = (Map<String, Object>) parsed;
                
                // 尝试常见的键名
                Object data = wrapper.get("data");
                if (data == null) {
                    data = wrapper.get("result");
                }
                if (data == null) {
                    data = wrapper.get("stock_data");
                }
                if (data == null) {
                    data = wrapper.get("trends");
                }
                
                if (data instanceof List) {
                    stockData = (List<Map<String, Object>>) data;
                } else {
                    log.warn("无法从包装对象中提取个股分时数据，键: {}", wrapper.keySet());
                    return new ArrayList<>();
                }
            } else {
                log.warn("获取个股当日分时数据失败: 未知的数据类型 {}", parsed.getClass().getSimpleName());
                return new ArrayList<>();
            }

            // 转换字段名为中文
            return convertStockTrendsToChinese(stockData);
            
        } catch (Exception e) {
            log.error("获取个股当日分时数据失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 转换大盘分时数据字段名为中文
     */
    private List<Map<String, Object>> convertMarketTrendsToChinese(List<Map<String, Object>> marketData) {
        if (marketData == null || marketData.isEmpty()) {
            return marketData;
        }
        
        List<Map<String, Object>> converted = new ArrayList<>();
        for (Map<String, Object> item : marketData) {
            Map<String, Object> convertedItem = new HashMap<>();
            
            // 转换字段名
            convertedItem.put("时间", item.get("t"));
            convertedItem.put("开盘价", item.get("o"));
            convertedItem.put("当前价", item.get("p"));
            convertedItem.put("最高价", item.get("h"));
            convertedItem.put("最低价", item.get("l"));
            convertedItem.put("成交量", item.get("v"));
            convertedItem.put("成交额", item.get("tu"));
            convertedItem.put("均价", item.get("avg"));
            
            converted.add(convertedItem);
        }
        
        return converted;
    }

    /**
     * 转换板块分时数据字段名为中文
     */
    private List<Map<String, Object>> convertBoardTrendsToChinese(List<Map<String, Object>> boardData) {
        if (boardData == null || boardData.isEmpty()) {
            return boardData;
        }
        
        List<Map<String, Object>> converted = new ArrayList<>();
        for (Map<String, Object> item : boardData) {
            Map<String, Object> convertedItem = new HashMap<>();
            
            // 转换字段名
            convertedItem.put("时间", item.get("t"));
            convertedItem.put("开盘价", item.get("o"));
            convertedItem.put("当前价", item.get("p"));
            convertedItem.put("最高价", item.get("h"));
            convertedItem.put("最低价", item.get("l"));
            convertedItem.put("成交量", item.get("v"));
            convertedItem.put("成交额", item.get("tu"));
            convertedItem.put("均价", item.get("avg"));
            
            converted.add(convertedItem);
        }
        
        return converted;
    }

    /**
     * 转换个股分时数据字段名为中文
     */
    private List<Map<String, Object>> convertStockTrendsToChinese(List<Map<String, Object>> stockData) {
        if (stockData == null || stockData.isEmpty()) {
            return stockData;
        }
        
        List<Map<String, Object>> converted = new ArrayList<>();
        for (Map<String, Object> item : stockData) {
            Map<String, Object> convertedItem = new HashMap<>();
            
            // 转换字段名
            convertedItem.put("时间", item.get("t"));
            convertedItem.put("开盘价", item.get("o"));
            convertedItem.put("当前价", item.get("p"));
            convertedItem.put("最高价", item.get("h"));
            convertedItem.put("最低价", item.get("l"));
            convertedItem.put("成交量", item.get("v"));
            convertedItem.put("成交额", item.get("tu"));
            convertedItem.put("均价", item.get("avg"));
            
            converted.add(convertedItem);
        }
        
        return converted;
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
     * 直接调用Python脚本计算技术指标（高效方式）
     * 避免通过临时文件传输数据，直接让Python脚本查询数据
     */
    public Map<String, Object> calculateTechnicalIndicatorsDirect(String stockCode) {
        try {
            log.debug("直接调用Python脚本计算股票{}的技术指标", stockCode);
            String result = executePythonScript("TechnicalIndicatorsDirect.py", stockCode);
            return objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("直接计算技术指标失败: {}", e.getMessage(), e);
            throw new RuntimeException("直接计算技术指标失败: " + e.getMessage());
        }
    }

    /**
     * 直接调用Python脚本计算大盘技术指标（高效方式）
     * 避免通过临时文件传输数据，直接让Python脚本查询数据
     */
    public Map<String, Object> calculateMarketTechnicalIndicatorsDirect(String stockCode) {
        try {
            log.debug("直接调用Python脚本计算大盘技术指标");
            String result = executePythonScript("MarketTechnicalIndicatorsDirect.py", stockCode);
            return objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("直接计算大盘技术指标失败: {}", e.getMessage(), e);
            throw new RuntimeException("直接计算大盘技术指标失败: " + e.getMessage());
        }
    }

    /**
     * 直接调用Python脚本计算板块技术指标（高效方式）
     * 避免通过临时文件传输数据，直接让Python脚本查询数据
     */
    public Map<String, Object> calculateBoardTechnicalIndicatorsDirect(String stockCode) {
        try {
            log.debug("直接调用Python脚本计算板块技术指标");
            String result = executePythonScript("BoardTechnicalIndicatorsDirect.py", stockCode);
            return objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("直接计算板块技术指标失败: {}", e.getMessage(), e);
            throw new RuntimeException("直接计算板块技术指标失败: " + e.getMessage());
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
            // 获取基础数据以便传入中文名/别名
            Map<String, Object> basic = getStockBasicData(stockCode);
            String targetName = null;
            String aliases = null;
            if (basic != null) {
                Object n = basic.get("stockName");
                if (n instanceof String && !((String) n).isEmpty()) {
                    targetName = (String) n;
                }
                // 简单提取简称：去除“股份有限公司”等，或留空
                Object companyName = basic.get("companyName");
                if (companyName instanceof String && !((String) companyName).isEmpty()) {
                    String shortAlias = ((String) companyName)
                        .replace("股份有限公司", "")
                        .replace("有限公司", "")
                        .replace("股份", "")
                        .trim();
                    if (!shortAlias.isEmpty() && (targetName == null || !shortAlias.equals(targetName))) {
                        aliases = shortAlias; // 逗号分隔可扩展
                    }
                }
            }

            // 传入：stockCode, crawl_content, max_pages, targetName, aliases, deepseek_api_key, sentiment_provider
            String result = executePythonScript(
                "EasyMoneyNewsData.py",
                stockCode,
                String.valueOf(newsCrawlContent),
                String.valueOf(newsMaxPages),
                targetName == null ? "" : targetName,
                aliases == null ? "" : aliases,
                deepseekApiKey,  // 从配置文件读取的DeepSeek API密钥
                newsSentimentProvider  // 从配置文件读取的情感分析提供者
            );
            
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
            log.warn("获取新闻数据失败: {}，返回空列表", e.getMessage());
            return new ArrayList<>(); // 返回空列表而不是报错
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
            log.warn("获取资金流向数据失败: {}，返回空列表", e.getMessage());
            return new ArrayList<>(); // 返回空列表而不是报错
        }
    }

    /**
     * 获取当日资金流向数据（使用 EastMoneyFundFlowToday.py），并将字段尽量中文化
     */
    public List<Map<String, Object>> getMoneyFlowToday(String stockCode) {
        try {
            String result = executePythonScript("EastMoneyFundFlowToday.py", stockCode);
            if (result == null || result.isEmpty()) {
                return new ArrayList<>();
            }
            result = cleanJsonString(result);
            List<Map<String, Object>> raw = objectMapper.readValue(result, new TypeReference<List<Map<String, Object>>>() {});

            // 尝试中文映射（若脚本已是中文键将保持原样）
            List<Map<String, Object>> mapped = new ArrayList<>();
            for (Map<String, Object> item : raw) {
                Map<String, Object> m = new HashMap<>();
                Object type = item.getOrDefault("type", item.getOrDefault("资金类型", null));
                m.put("资金类型", type);
                Object net = item.getOrDefault("net", item.getOrDefault("净流入额（万元）", null));
                m.put("净流入额（万元）", net);
                Object ratio = item.getOrDefault("ratio", item.getOrDefault("净占比（%）", null));
                m.put("净占比（%）", ratio);
                mapped.add(m);
            }
            return mapped;
        } catch (Exception e) {
            log.warn("获取当日资金流向数据失败: {}，返回空列表", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 获取融资融券数据
     */
    public List<Map<String, Object>> getMarginTradingData(String stockCode) {
        try {
            String result = executePythonScript("EastMoneyRZRQData.py", stockCode);
            
            // 验证JSON格式
            if (result == null || result.trim().isEmpty()) {
                log.warn("股票 {} 融资融券数据脚本返回空结果", stockCode);
                return new ArrayList<>(); // 返回空列表而不是报错
            }
            
            // 清理JSON字符串
            result = cleanJsonString(result);
            
            // 尝试解析为数组
            try {
                return objectMapper.readValue(result, new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                // 如果解析为数组失败，尝试解析为对象，检查是否有错误
                try {
                    Map<String, Object> errorResult = objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {});
                    if (errorResult.containsKey("error")) {
                        log.warn("股票 {} 融资融券数据获取失败: {}，返回空列表", stockCode, errorResult.get("error"));
                        return new ArrayList<>(); // 返回空列表而不是报错
                    }
                } catch (Exception ex) {
                    log.warn("股票 {} 融资融券数据格式异常: {}，返回空列表", stockCode, ex.getMessage());
                }
                return new ArrayList<>(); // 返回空列表而不是报错
            }
            
        } catch (Exception e) {
            log.warn("获取股票 {} 融资融券数据失败: {}，返回空列表", stockCode, e.getMessage());
            return new ArrayList<>(); // 返回空列表而不是报错
        }
    }

    /**
     * 获取分时数据精炼分析
     */
    public Map<String, Object> getIntradayAnalysis(String stockCode) {
        Map<String, Object> analysisResult = new HashMap<>();
        
        try {
            log.debug("开始获取股票 {} 的分时数据分析", stockCode);
            String result = executePythonScript("IntradayAnalysis.py", stockCode);
            
            // 验证JSON格式
            if (result != null && !result.trim().isEmpty()) {
                // 清理JSON字符串
                result = cleanJsonString(result);
                
                try {
                    Map<String, Object> intradayData = objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {});
                    
                    // 检查是否有错误
                    if (intradayData.containsKey("error")) {
                        String errorMsg = (String) intradayData.get("error");
                        log.warn("股票 {} 分时数据分析有错误: {}，但继续获取基础数据", stockCode, errorMsg);
                        // 不抛出异常，继续执行
                    } else {
                        // 分时数据获取成功，合并到结果中
                        analysisResult.putAll(intradayData);
                        log.debug("股票 {} 分时数据获取成功", stockCode);
                    }
                } catch (Exception parseEx) {
                    log.warn("股票 {} 分时数据JSON解析失败: {}，但继续获取基础数据", stockCode, parseEx.getMessage());
                    // 不抛出异常，继续执行
                }
            } else {
                log.warn("股票 {} 没有分时数据，但继续获取基础数据", stockCode);
            }
            
        } catch (Exception e) {
            log.warn("获取股票 {} 分时数据分析失败: {}，但继续获取基础数据", stockCode, e.getMessage());
            // 不抛出异常，继续执行
        }
        
        // 无论分时数据是否成功，都尝试获取基础数据
        try {
            Map<String, Object> basicData = getStockBasicData(stockCode);
            if (basicData != null && !basicData.isEmpty()) {
                analysisResult.put("stockBasic", basicData);
                log.debug("股票 {} 基础数据获取成功", stockCode);
            } else {
                log.warn("股票 {} 基础数据获取失败或为空", stockCode);
            }
        } catch (Exception basicEx) {
            log.warn("获取股票 {} 基础数据时发生异常: {}，但继续返回已获取的数据", stockCode, basicEx.getMessage());
        }
        
        log.debug("分时数据分析完成，股票代码: {}，结果包含 {} 个字段", stockCode, analysisResult.size());
        return analysisResult;
    }
    
    /**
     * 获取股票基础数据
     */
    public Map<String, Object> getStockBasicData(String stockCode) {
        try {
            log.debug("开始获取股票 {} 的基础数据", stockCode);
            String result = executePythonScript("EastMoneyStockBasic.py", stockCode);
            
            // 验证JSON格式
            if (result == null || result.trim().isEmpty()) {
                log.warn("股票 {} 基础数据脚本返回空结果", stockCode);
                return null;
            }
            
            // 清理JSON字符串
            result = cleanJsonString(result);
            
            Map<String, Object> basicData = objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {});
            
            log.debug("股票基础数据获取完成，股票代码: {}", stockCode);
            return basicData;
            
        } catch (Exception e) {
            log.warn("获取股票 {} 基础数据失败: {}，返回null", stockCode, e.getMessage());
            return null;
        }
    }

    /**
     * 获取同行比较数据
     */
    public Map<String, Object> getPeerComparisonData(String stockCode) {
        try {
            log.debug("开始获取股票 {} 的同行比较数据", stockCode);
            String result = executePythonScript("EastMoneyPeerComparison.py", stockCode);
            
            // 验证JSON格式
            if (result == null || result.trim().isEmpty()) {
                log.warn("股票 {} 同行比较数据脚本返回空结果", stockCode);
                return null;
            }
            
            // 清理JSON字符串
            result = cleanJsonString(result);
            
            Map<String, Object> rawResult = objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {});
            
            // 检查新的输出格式
            if (rawResult.containsKey("success")) {
                Boolean success = (Boolean) rawResult.get("success");
                if (success != null && success) {
                    // 成功时，提取data字段
                    Object data = rawResult.get("data");
                    if (data instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> peerData = (Map<String, Object>) data;
                        log.debug("同行比较数据获取完成，股票代码: {}", stockCode);
                        return peerData;
                    } else if (data instanceof List) {
                        // 如果data是数组，包装成Map
                        log.warn("股票 {} 同行比较数据data字段是数组类型，包装成Map", stockCode);
                        Map<String, Object> wrappedData = new HashMap<>();
                        wrappedData.put("listData", data);
                        return wrappedData;
                    } else {
                        log.warn("股票 {} 同行比较数据格式错误，data字段类型: {}", stockCode, data != null ? data.getClass().getSimpleName() : "null");
                        return new HashMap<>();
                    }
                } else {
                    // 失败时，记录错误信息
                    String error = (String) rawResult.get("error");
                    log.warn("股票 {} 同行比较数据获取失败: {}", stockCode, error);
                    return new HashMap<>();
                }
            } else {
                // 兼容旧格式，直接返回整个结果
                log.debug("同行比较数据获取完成（旧格式），股票代码: {}", stockCode);
                return rawResult;
            }
            
        } catch (Exception e) {
            log.warn("获取股票 {} 同行比较数据失败: {}，返回空Map", stockCode, e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 获取财务分析数据
     */
    public Map<String, Object> getFinancialAnalysisData(String stockCode) {
        try {
            log.debug("开始获取股票 {} 的财务分析数据", stockCode);
            String result = executePythonScript("EastMoneyFinancialAnalysis.py", stockCode);
            
            // 验证JSON格式
            if (result == null || result.trim().isEmpty()) {
                log.warn("股票 {} 财务分析数据脚本返回空结果", stockCode);
                return null;
            }
            
            // 清理JSON字符串
            result = cleanJsonString(result);
            
            Map<String, Object> financialData = objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {});
            
            log.debug("财务分析数据获取完成，股票代码: {}", stockCode);
            return financialData;
            
        } catch (Exception e) {
            log.warn("获取股票 {} 财务分析数据失败: {}，返回null", stockCode, e.getMessage());
            return null;
        }
    }

    /**
     * 获取股票核心概念和行业标签数据
     */
    public Map<String, Object> getCoreTagsData(String stockCode) {
        try {
            log.debug("开始获取股票 {} 的核心概念和行业标签数据", stockCode);
            String result = executePythonScript("EastMoneyCoreTags.py", stockCode);
            
            // 验证JSON格式
            if (result == null || result.trim().isEmpty()) {
                log.warn("股票 {} 核心概念和行业标签数据脚本返回空结果", stockCode);
                return null;
            }
            
            // 清理JSON字符串
            result = cleanJsonString(result);
            
            Map<String, Object> coreTagsData = objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {});
            
            log.debug("核心概念和行业标签数据获取完成，股票代码: {}", stockCode);
            return coreTagsData;
            
        } catch (Exception e) {
            log.warn("获取股票 {} 核心概念和行业标签数据失败: {}，返回null", stockCode, e.getMessage());
            return null;
        }
    }
}