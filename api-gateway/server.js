const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const axios = require('axios');
const compression = require('compression');
const morgan = require('morgan');
const { createProxyMiddleware: proxy } = require('http-proxy-middleware');

const app = express();
const PORT = process.env.PORT || 3001;

// 后端服务地址
const STOCK_ANALYSIS_SERVICE_URL = process.env.STOCK_ANALYSIS_SERVICE_URL || 'http://localhost:8080';

// 中间件配置
app.use(helmet());
app.use(compression());
app.use(morgan('combined'));

// CORS配置
app.use(cors({
    origin: ['http://localhost:3000', 'http://localhost:19006', 'exp://192.168.*.*:19000'],
    credentials: true
}));

// 请求体解析
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true }));

// 速率限制
const limiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15分钟
    max: 100, // 每个IP最多100个请求
    message: {
        error: '请求过于频繁，请稍后再试',
        code: 'RATE_LIMIT_EXCEEDED'
    }
});
app.use('/api/', limiter);

// 健康检查
app.get('/health', (req, res) => {
    res.json({
        status: 'OK',
        timestamp: new Date().toISOString(),
        service: 'Stock Analysis API Gateway'
    });
});

// 获取用户分析任务列表（根据设备指纹）
app.get('/api/mobile/analysis/tasks', async (req, res) => {
    try {
        const response = await axios.get(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/stocks/analysis/tasks`,
            { 
                params: req.query,
                timeout: 30000
            }
        );
        
        res.json({
            ...response.data,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error('获取用户分析任务代理错误:', error.message);
        res.status((error.response && error.response.status) || 500).json({
            success: false,
            message: (error.response && error.response.data && error.response.data.message) || '获取分析任务失败',
            code: 'GET_TASKS_ERROR'
        });
    }
});

// 获取所有分析任务（包括他人的）
app.get('/api/mobile/analysis/tasks/all', async (req, res) => {
    try {
        const response = await axios.get(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/stocks/analysis/tasks/all`,
            { 
                params: req.query,
                timeout: 30000
            }
        );
        
        res.json({
            ...response.data,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error('获取所有分析任务代理错误:', error.message);
        res.status((error.response && error.response.status) || 500).json({
            success: false,
            message: (error.response && error.response.data && error.response.data.message) || '获取所有分析任务失败',
            code: 'GET_ALL_TASKS_ERROR'
        });
    }
});

// 股票分析相关API代理
app.post('/api/mobile/stock/analyze', async (req, res) => {
    try {
        console.log('代理股票分析请求:', req.body);
        
        const response = await axios.post(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/stocks/analyze`,
            req.body,
            {
                headers: {
                    'Content-Type': 'application/json'
                },
                timeout: 600000  // 增加到10分钟
            }
        );
        
        // 为移动端优化响应格式
        const mobileResponse = {
            ...response.data,
            timestamp: new Date().toISOString(),
            cached: false
        };
        
        res.json(mobileResponse);
    } catch (error) {
        console.error('股票分析代理错误:', error.message);
        
        if (error.response) {
            res.status(error.response.status).json({
                success: false,
                message: (error.response.data && error.response.data.message) || '后端服务错误',
                code: 'BACKEND_ERROR'
            });
        } else if (error.code === 'ECONNREFUSED') {
            res.status(503).json({
                success: false,
                message: '后端服务不可用',
                code: 'SERVICE_UNAVAILABLE'
            });
        } else {
            res.status(500).json({
                success: false,
                message: '网关内部错误',
                code: 'GATEWAY_ERROR'
            });
        }
    }
});

// 快速分析API
app.post('/api/mobile/stock/quick-analyze', async (req, res) => {
    try {
        const response = await axios.post(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/stocks/quick-analyze`,
            req.body,
            {
                headers: {
                    'Content-Type': 'application/json'
                },
                timeout: 300000  // 快速分析5分钟超时
            }
        );
        
        res.json({
            ...response.data,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error('快速分析代理错误:', error.message);
        res.status((error.response && error.response.status) || 500).json({
            success: false,
            message: (error.response && error.response.data && error.response.data.message) || '快速分析失败',
            code: 'QUICK_ANALYSIS_ERROR'
        });
    }
});

// 风险评估API
app.post('/api/mobile/stock/risk-assessment', async (req, res) => {
    try {
        const response = await axios.post(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/stocks/risk-assessment`,
            req.body,
            {
                headers: {
                    'Content-Type': 'application/json'
                },
                timeout: 300000  // 风险评估5分钟超时
            }
        );
        
        res.json({
            ...response.data,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error('风险评估代理错误:', error.message);
        res.status((error.response && error.response.status) || 500).json({
            success: false,
            message: (error.response && error.response.data && error.response.data.message) || '风险评估失败',
            code: 'RISK_ASSESSMENT_ERROR'
        });
    }
});

// AI 详细分析（异步） - 启动任务
app.post('/api/mobile/ai-detailed/:stockCode/start', async (req, res) => {
    try {
        const { stockCode } = req.params;
        const response = await axios.post(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/stocks/ai-detailed/${stockCode}/start`,
            {},
            {
                headers: { 'Content-Type': 'application/json' },
                timeout: 600000
            }
        );
        res.json(response.data);
    } catch (error) {
        console.error('启动AI详细分析代理错误:', error.message);
        res.status((error.response && error.response.status) || 500).json({
            success: false,
            message: (error.response && error.response.data && error.response.data.message) || '启动AI详细分析失败',
            code: 'AI_DETAILED_START_ERROR'
        });
    }
});

// 盯盘：启动
app.post('/api/mobile/monitor/start', async (req, res) => {
    try {
        const { stockCode, intervalMinutes, analysisId } = req.body || {};
        const response = await axios.post(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/stocks/analysis/monitor/start`,
            null,
            {
                params: { stockCode, intervalMinutes, analysisId, machineId: req.headers['x-device-fingerprint'] || 'default' },
                timeout: 60000
            }
        );
        res.json(response.data);
    } catch (error) {
        console.error('启动盯盘代理错误:', error.message);
        res.status((error.response && error.response.status) || 500).json({
            success: false,
            message: (error.response && error.response.data && error.response.data.message) || '启动盯盘失败',
            code: 'MONITOR_START_ERROR'
        });
    }
});

// 盯盘：停止
app.post('/api/mobile/monitor/stop', async (req, res) => {
    try {
        const { jobId } = req.body || {};
        const response = await axios.post(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/stocks/analysis/monitor/stop`,
            null,
            {
                params: { jobId },
                timeout: 30000
            }
        );
        res.json(response.data);
    } catch (error) {
        console.error('停止盯盘代理错误:', error.message);
        res.status((error.response && error.response.status) || 500).json({
            success: false,
            message: (error.response && error.response.data && error.response.data.message) || '停止盯盘失败',
            code: 'MONITOR_STOP_ERROR'
        });
    }
});

// 盯盘：状态
app.get('/api/mobile/monitor/status/:jobId', async (req, res) => {
    try {
        const { jobId } = req.params;
        const response = await axios.get(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/stocks/analysis/monitor/status/${jobId}`,
            { timeout: 30000 }
        );
        res.json(response.data);
    } catch (error) {
        console.error('查询盯盘状态代理错误:', error.message);
        res.status((error.response && error.response.status) || 500).json({
            success: false,
            message: (error.response && error.response.data && error.response.data.message) || '查询盯盘状态失败',
            code: 'MONITOR_STATUS_ERROR'
        });
    }
});

// 盯盘：股票状态
app.get('/api/mobile/monitor/stock-status/:stockCode', async (req, res) => {
    try {
        const { stockCode } = req.params;
        const response = await axios.get(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/stocks/analysis/monitor/stock-status/${stockCode}`,
            { timeout: 30000 }
        );
        res.json(response.data);
    } catch (error) {
        console.error('查询股票盯盘状态代理错误:', error.message);
        res.status((error.response && error.response.status) || 500).json({
            success: false,
            message: (error.response && error.response.data && error.response.data.message) || '查询股票盯盘状态失败',
            code: 'MONITOR_STOCK_STATUS_ERROR'
        });
    }
});

// 盯盘：清理所有任务
app.post('/api/mobile/monitor/cleanup-all', async (req, res) => {
    try {
        const response = await axios.post(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/stocks/analysis/monitor/cleanup-all`,
            {},
            { timeout: 30000 }
        );
        res.json(response.data);
    } catch (error) {
        console.error('清理所有盯盘任务代理错误:', error.message);
        res.status((error.response && error.response.status) || 500).json({
            success: false,
            message: (error.response && error.response.data && error.response.data.message) || '清理盯盘任务失败',
            code: 'MONITOR_CLEANUP_ERROR'
        });
    }
});

// 盯盘：今日记录
app.get('/api/mobile/monitor/records/today/:stockCode', async (req, res) => {
    try {
        const { stockCode } = req.params;
        const response = await axios.get(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/stocks/analysis/monitor/records/today/${stockCode}`,
            { timeout: 30000 }
        );
        res.json(response.data);
    } catch (error) {
        console.error('获取今日盯盘记录代理错误:', error.message);
        res.status((error.response && error.response.status) || 500).json({
            success: false,
            message: (error.response && error.response.data && error.response.data.message) || '获取盯盘记录失败',
            code: 'MONITOR_RECORDS_ERROR'
        });
    }
});

// 盯盘：获取所有盯盘任务
app.get('/api/mobile/monitor/all-jobs', async (req, res) => {
    try {
        const response = await axios.get(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/stocks/monitor/all-jobs`,
            { timeout: 30000 }
        );
        res.json(response.data);
    } catch (error) {
        console.error('获取所有盯盘任务代理错误:', error.message);
        res.status((error.response && error.response.status) || 500).json({
            success: false,
            message: (error.response && error.response.data && error.response.data.message) || '获取盯盘任务失败',
            code: 'MONITOR_ALL_JOBS_ERROR'
        });
    }
});

// AI 详细分析（异步） - 查询状态
app.get('/api/mobile/ai-detailed/status/:taskId', async (req, res) => {
    try {
        const { taskId } = req.params;
        const response = await axios.get(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/stocks/ai-detailed/status/${taskId}`,
            { timeout: 600000 }
        );
        res.json(response.data);
    } catch (error) {
        console.error('查询AI详细分析状态代理错误:', error.message);
        res.status((error.response && error.response.status) || 500).json({
            success: false,
            message: (error.response && error.response.data && error.response.data.message) || '查询AI分析状态失败',
            code: 'AI_DETAILED_STATUS_ERROR'
        });
    }
});

// AI 详细分析（同步获取结果，兼容）
app.get('/api/mobile/ai-detailed/:stockCode', async (req, res) => {
    try {
        const { stockCode } = req.params;
        const response = await axios.get(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/stocks/ai-detailed/${stockCode}`,
            { timeout: 600000 }
        );
        res.json(response.data);
    } catch (error) {
        console.error('获取AI详细分析结果代理错误:', error.message);
        res.status((error.response && error.response.status) || 500).json({
            success: false,
            message: (error.response && error.response.data && error.response.data.message) || '获取AI详细分析失败',
            code: 'AI_DETAILED_RESULT_ERROR'
        });
    }
});

// 简单股票分析API (GET)
app.get('/api/mobile/stock/analyze/:stockCode', async (req, res) => {
    try {
        const { stockCode } = req.params;
        const response = await axios.get(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/stocks/analyze/${stockCode}`,
            { timeout: 600000 }  // 简单分析10分钟超时
        );
        
        res.json({
            ...response.data,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error('简单分析代理错误:', error.message);
        res.status((error.response && error.response.status) || 500).json({
            success: false,
            message: (error.response && error.response.data && error.response.data.message) || '股票分析失败',
            code: 'SIMPLE_ANALYSIS_ERROR'
        });
    }
});

// 异步分析任务存储
const analysisJobs = new Map();

// 启动异步分析任务
app.post('/api/mobile/stock/analyze-async', async (req, res) => {
    try {
        const { stockCode } = req.body;
        const machineId = req.headers['x-device-fingerprint'] || 'default';
        const jobId = `${stockCode}_${Date.now()}`;
        
        // 创建分析任务
        analysisJobs.set(jobId, {
            status: 'processing',
            stockCode,
            startTime: new Date(),
            progress: 0
        });
        
        // 立即返回任务ID
        res.json({
            success: true,
            taskId: jobId,  // 前端期望的字段名
            jobId,          // 保持兼容性
            message: '分析任务已启动',
            estimatedTime: '2-5分钟',
            stockName: '分析中...' // 将在分析完成后更新
        });
        
        // 异步执行分析
        performAsyncAnalysis(jobId, stockCode, machineId);
        
    } catch (error) {
        console.error('启动异步分析失败:', error.message);
        res.status(500).json({
            success: false,
            message: '启动分析任务失败',
            code: 'ASYNC_START_ERROR'
        });
    }
});

// 查询分析任务状态
app.get('/api/mobile/stock/analyze-status/:taskId', (req, res) => {
    const { taskId } = req.params;
    const jobId = taskId;  // 兼容处理
    const job = analysisJobs.get(jobId);
    
    if (!job) {
        return res.status(404).json({
            success: false,
            message: '任务不存在',
            code: 'JOB_NOT_FOUND'
        });
    }
    
    // 确保返回所有必要的字段，包括stockName
    const responseData = {
        success: true,
        ...job,
        duration: Date.now() - job.startTime.getTime()
    };
    
    // 如果任务已完成且有结果，尝试从结果中提取股票名称
    if (job.status === 'completed' && job.result) {
        // 多重保障：从多个可能的字段获取股票名称
        let stockName = job.stockName;
        
        if (!stockName || stockName.trim() === '') {
            if (job.result?.stockBasic?.stockName) {
                stockName = job.result.stockBasic.stockName;
            } else if (job.result?.stockName) {
                stockName = job.result.stockName;
            } else if (job.result?.aiAnalysisResult?.stockName) {
                stockName = job.result.aiAnalysisResult.stockName;
            }
        }
        
        // 如果还是没有，使用默认名称
        if (!stockName || stockName.trim() === '') {
            stockName = `股票 ${job.stockCode}`;
        }
        
        responseData.stockName = stockName;
    }
    
    res.json(responseData);
});

// 异步执行分析的函数
async function performAsyncAnalysis(jobId, stockCode, machineId) {
    let progressInterval;  // 声明在函数顶部
    
    try {
        const job = analysisJobs.get(jobId);
        if (!job) return;
        
        // 更新进度
        job.progress = 10;
        job.message = '正在获取股票数据...';
        
        // 更智能的进度更新
        progressInterval = setInterval(() => {
            const currentJob = analysisJobs.get(jobId);
            if (currentJob && currentJob.status === 'processing') {
                const elapsed = Date.now() - currentJob.startTime.getTime();
                const minutes = elapsed / (1000 * 60);
                
                // 基于时间的进度估算
                if (minutes < 0.5) {
                    currentJob.progress = Math.min(20, currentJob.progress + 5);
                    currentJob.message = '正在获取股票数据...';
                } else if (minutes < 1) {
                    currentJob.progress = Math.min(40, currentJob.progress + 5);
                    currentJob.message = '正在计算技术指标...';
                } else if (minutes < 1.5) {
                    currentJob.progress = Math.min(60, currentJob.progress + 5);
                    currentJob.message = '正在分析基本面...';
                } else if (minutes < 2) {
                    currentJob.progress = Math.min(80, currentJob.progress + 3);
                    currentJob.message = '正在生成AI分析...';
                } else {
                    currentJob.progress = Math.min(90, currentJob.progress + 2);
                    currentJob.message = '正在整理分析结果...';
                }
            }
        }, 3000);  // 每3秒更新一次
        
        // 执行实际分析
        const response = await axios.post(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/stocks/analyze`,
            { stockCode, machineId },
            {
                headers: { 'Content-Type': 'application/json' },
                timeout: 600000  // 10分钟超时
            }
        );
        
        clearInterval(progressInterval);
        
        // 更新任务状态为完成
        // 多重保障：从多个可能的字段获取股票名称
        let stockName = response.data?.stockBasic?.stockName;
        
        if (!stockName || stockName.trim() === '') {
            if (response.data?.stockName) {
                stockName = response.data.stockName;
            } else if (response.data?.aiAnalysisResult?.stockName) {
                stockName = response.data.aiAnalysisResult.stockName;
            }
        }
        
        // 如果还是没有，使用默认名称
        if (!stockName || stockName.trim() === '') {
            stockName = `股票 ${stockCode}`;
        }
        
        analysisJobs.set(jobId, {
            ...job,
            status: 'completed',
            progress: 100,
            message: '分析完成',
            result: response.data,
            completedTime: new Date(),
            stockName: stockName
        });
        
        // 30分钟后清理任务
        setTimeout(() => {
            analysisJobs.delete(jobId);
        }, 30 * 60 * 1000);
        
    } catch (error) {
        console.error('异步分析失败:', error.message);
        
        // 清理进度更新定时器
        if (progressInterval) {
            clearInterval(progressInterval);
        }
        
        const job = analysisJobs.get(jobId);
        if (job) {
            analysisJobs.set(jobId, {
                ...job,
                status: 'failed',
                progress: 0,
                message: '分析失败: ' + (error.response?.data?.message || error.message),
                error: error.response?.data || error.message,
                failedTime: new Date()
            });
            
            // 失败的任务10分钟后清理
            setTimeout(() => {
                analysisJobs.delete(jobId);
            }, 10 * 60 * 1000);
        }
    }
}

// 移动端专用API - 获取股票列表（模拟数据）
app.get('/api/mobile/stocks/popular', (req, res) => {
    const popularStocks = [
        { code: '000001', name: '平安银行', market: 'SZ' },
        { code: '000002', name: '万科A', market: 'SZ' },
        { code: '600000', name: '浦发银行', market: 'SH' },
        { code: '600036', name: '招商银行', market: 'SH' },
        { code: '600519', name: '贵州茅台', market: 'SH' },
        { code: '000858', name: '五粮液', market: 'SZ' },
        { code: '002415', name: '海康威视', market: 'SZ' },
        { code: '600276', name: '恒瑞医药', market: 'SH' }
    ];
    
    res.json({
        success: true,
        data: popularStocks,
        timestamp: new Date().toISOString()
    });
});

// 每日推荐API代理 - 获取推荐摘要
app.get('/api/recommendations/summary', async (req, res) => {
    try {
        const response = await axios.get(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/recommendations/summary`,
            {
                headers: {
                    'Content-Type': 'application/json; charset=utf-8'
                },
                timeout: 30000,  // 30秒超时
                responseType: 'json',
                responseEncoding: 'utf8'
            }
        );
        
        res.json(response.data);
    } catch (error) {
        console.error('获取推荐摘要代理错误:', error.message);
        
        if (error.response) {
            res.status(error.response.status).json(error.response.data);
        } else if (error.code === 'ECONNREFUSED') {
            res.status(503).json({
                success: false,
                message: '后端服务不可用',
                code: 'SERVICE_UNAVAILABLE'
            });
        } else {
            res.status(500).json({
                success: false,
                message: '网关内部错误',
                code: 'GATEWAY_ERROR'
            });
        }
    }
});

// 每日推荐API代理 - 获取今日推荐
app.get('/api/recommendations/today', async (req, res) => {
    try {
        const response = await axios.get(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/recommendations/today`,
            {
                headers: {
                    'Content-Type': 'application/json; charset=utf-8'
                },
                timeout: 30000,  // 30秒超时
                responseType: 'json',
                responseEncoding: 'utf8'
            }
        );
        
        res.json(response.data);
    } catch (error) {
        console.error('获取今日推荐代理错误:', error.message);
        
        if (error.response) {
            res.status(error.response.status).json(error.response.data);
        } else if (error.code === 'ECONNREFUSED') {
            res.status(503).json({
                success: false,
                message: '后端服务不可用',
                code: 'SERVICE_UNAVAILABLE'
            });
        } else {
            res.status(500).json({
                success: false,
                message: '网关内部错误',
                code: 'GATEWAY_ERROR'
            });
        }
    }
});

// 每日推荐API代理 - 获取热门推荐
app.get('/api/recommendations/hot', async (req, res) => {
    try {
        const response = await axios.get(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/recommendations/hot`,
            {
                headers: {
                    'Content-Type': 'application/json; charset=utf-8'
                },
                timeout: 30000,  // 30秒超时
                responseType: 'json',
                responseEncoding: 'utf8'
            }
        );
        
        res.json(response.data);
    } catch (error) {
        console.error('获取热门推荐代理错误:', error.message);
        
        if (error.response) {
            res.status(error.response.status).json(error.response.data);
        } else if (error.code === 'ECONNREFUSED') {
            res.status(503).json({
                success: false,
                message: '后端服务不可用',
                code: 'SERVICE_UNAVAILABLE'
            });
        } else {
            res.status(500).json({
                success: false,
                message: '网关内部错误',
                code: 'GATEWAY_ERROR'
            });
        }
    }
});

// 每日推荐API代理 - 按领域获取推荐
app.get('/api/recommendations/sector/:sector', async (req, res) => {
    try {
        const { sector } = req.params;
        const response = await axios.get(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/recommendations/sector/${sector}`,
            {
                headers: {
                    'Content-Type': 'application/json; charset=utf-8'
                },
                timeout: 30000,  // 30秒超时
                responseType: 'json',
                responseEncoding: 'utf8'
            }
        );
        
        res.json(response.data);
    } catch (error) {
        console.error('按领域获取推荐代理错误:', error.message);
        
        if (error.response) {
            res.status(error.response.status).json(error.response.data);
        } else if (error.code === 'ECONNREFUSED') {
            res.status(503).json({
                success: false,
                message: '后端服务不可用',
                code: 'SERVICE_UNAVAILABLE'
            });
        } else {
            res.status(500).json({
                success: false,
                message: '网关内部错误',
                code: 'GATEWAY_ERROR'
            });
        }
    }
});

// 每日推荐API代理 - 获取推荐详情
app.get('/api/recommendations/detail/:stockCode', async (req, res) => {
    try {
        const { stockCode } = req.params;
        const response = await axios.get(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/recommendations/detail/${stockCode}`,
            {
                headers: {
                    'Content-Type': 'application/json; charset=utf-8'
                },
                timeout: 30000,  // 30秒超时
                responseType: 'json',
                responseEncoding: 'utf8'
            }
        );
        
        res.json(response.data);
    } catch (error) {
        console.error('获取推荐详情代理错误:', error.message);
        
        if (error.response) {
            res.status(error.response.status).json(error.response.data);
        } else if (error.code === 'ECONNREFUSED') {
            res.status(503).json({
                success: false,
                message: '后端服务不可用',
                code: 'SERVICE_UNAVAILABLE'
            });
        } else {
            res.status(500).json({
                success: false,
                message: '网关内部错误',
                code: 'GATEWAY_ERROR'
            });
        }
    }
});

// 每日推荐API代理 - 手动刷新推荐
app.post('/api/recommendations/refresh', async (req, res) => {
    try {
        const response = await axios.post(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/recommendations/refresh`,
            req.body,
            {
                headers: {
                    'Content-Type': 'application/json; charset=utf-8'
                },
                timeout: 30000,  // 30秒超时
                responseType: 'json',
                responseEncoding: 'utf8'
            }
        );
        
        res.json(response.data);
    } catch (error) {
        console.error('手动刷新推荐代理错误:', error.message);
        
        if (error.response) {
            res.status(error.response.status).json(error.response.data);
        } else if (error.code === 'ECONNREFUSED') {
            res.status(503).json({
                success: false,
                message: '后端服务不可用',
                code: 'SERVICE_UNAVAILABLE'
            });
        } else {
            res.status(500).json({
                success: false,
                message: '网关内部错误',
                code: 'GATEWAY_ERROR'
            });
        }
    }
});

// 每日推荐API代理 - 获取推荐状态
app.get('/api/recommendations/status', async (req, res) => {
    try {
        const response = await axios.get(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/recommendations/status`,
            {
                headers: {
                    'Content-Type': 'application/json; charset=utf-8'
                },
                timeout: 30000,  // 30秒超时
                responseType: 'json',
                responseEncoding: 'utf8'
            }
        );
        
        res.json(response.data);
    } catch (error) {
        console.error('获取推荐状态代理错误:', error.message);
        
        if (error.response) {
            res.status(error.response.status).json(error.response.data);
        } else if (error.code === 'ECONNREFUSED') {
            res.status(503).json({
                success: false,
                message: '后端服务不可用',
                code: 'SERVICE_UNAVAILABLE'
            });
        } else {
            res.status(500).json({
                success: false,
                message: '网关内部错误',
                code: 'GATEWAY_ERROR'
            });
        }
    }
});

// 每日推荐API代理 - 获取可用日期列表
app.get('/api/recommendations/dates', async (req, res) => {
    try {
        const response = await axios.get(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/recommendations/dates`,
            {
                headers: {
                    'Content-Type': 'application/json; charset=utf-8'
                },
                timeout: 30000,  // 30秒超时
                responseType: 'json',
                responseEncoding: 'utf8'
            }
        );
        
        res.json(response.data);
    } catch (error) {
        console.error('获取可用日期列表代理错误:', error.message);
        
        if (error.response) {
            res.status(error.response.status).json(error.response.data);
        } else if (error.code === 'ECONNREFUSED') {
            res.status(503).json({
                success: false,
                message: '后端服务不可用',
                code: 'SERVICE_UNAVAILABLE'
            });
        } else {
            res.status(500).json({
                success: false,
                message: '网关内部错误',
                code: 'GATEWAY_ERROR'
            });
        }
    }
});

// 每日推荐API代理 - 根据日期获取推荐数据
app.get('/api/recommendations/by-date/:date', async (req, res) => {
    try {
        const { date } = req.params;
        const response = await axios.get(
            `${STOCK_ANALYSIS_SERVICE_URL}/api/recommendations/by-date/${date}`,
            {
                headers: {
                    'Content-Type': 'application/json; charset=utf-8'
                },
                timeout: 30000,  // 30秒超时
                responseType: 'json',
                responseEncoding: 'utf8'
            }
        );
        
        res.json(response.data);
    } catch (error) {
        console.error('根据日期获取推荐数据代理错误:', error.message);
        
        if (error.response) {
            res.status(error.response.status).json(error.response.data);
        } else if (error.code === 'ECONNREFUSED') {
            res.status(503).json({
                success: false,
                message: '后端服务不可用',
                code: 'SERVICE_UNAVAILABLE'
            });
        } else {
            res.status(500).json({
                success: false,
                message: '网关内部错误',
                code: 'GATEWAY_ERROR'
            });
        }
    }
});

// 股票分析相关接口
app.use('/api/stock-analysis', proxy({
  target: STOCK_ANALYSIS_SERVICE_URL,
  changeOrigin: true,
  pathRewrite: {
    '^/api/stock-analysis': '/api/stock-analysis'
  }
}));

// AI详细分析相关接口
app.use('/api/stocks', proxy({
  target: STOCK_ANALYSIS_SERVICE_URL,
  changeOrigin: true,
  pathRewrite: {
    '^/api/stocks': '/api/stocks'
  }
}));

// 错误处理中间件
app.use((error, req, res, next) => {
    console.error('API Gateway Error:', error);
    res.status(500).json({
        success: false,
        message: '服务器内部错误',
        code: 'INTERNAL_SERVER_ERROR'
    });
});

// 404处理
app.use('*', (req, res) => {
    res.status(404).json({
        success: false,
        message: '接口不存在',
        code: 'NOT_FOUND'
    });
});

app.listen(PORT, () => {
    console.log(`🚀 API Gateway 运行在端口 ${PORT}`);
    console.log(`📡 后端服务地址: ${STOCK_ANALYSIS_SERVICE_URL}`);
    console.log(`🌐 健康检查: http://localhost:${PORT}/health`);
});

module.exports = app;
