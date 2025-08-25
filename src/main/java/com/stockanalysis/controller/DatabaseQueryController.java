package com.stockanalysis.controller;

import com.stockanalysis.service.DatabaseQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库查询API控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/database")
@CrossOrigin(origins = "*")
public class DatabaseQueryController {

    private final DatabaseQueryService databaseQueryService;

    @Autowired
    public DatabaseQueryController(DatabaseQueryService databaseQueryService) {
        this.databaseQueryService = databaseQueryService;
    }

    /**
     * 获取所有表名
     */
    @GetMapping("/tables")
    public ResponseEntity<Map<String, Object>> getAllTables() {
        try {
            log.info("获取所有表名");
            List<String> tables = databaseQueryService.getAllTables();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", tables);
            response.put("message", "获取表名成功");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取表名失败: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取表名失败: " + e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 获取表结构
     */
    @GetMapping("/table/{tableName}/structure")
    public ResponseEntity<Map<String, Object>> getTableStructure(@PathVariable String tableName) {
        try {
            log.info("获取表结构: {}", tableName);
            List<Map<String, Object>> structure = databaseQueryService.getTableStructure(tableName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", structure);
            response.put("message", "获取表结构成功");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取表结构失败: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取表结构失败: " + e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 执行自定义SQL查询
     */
    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> executeQuery(@RequestBody Map<String, String> request) {
        try {
            String sql = request.get("sql");
            if (sql == null || sql.trim().isEmpty()) {
                throw new IllegalArgumentException("SQL语句不能为空");
            }
            
            log.info("执行SQL查询: {}", sql);
            List<Map<String, Object>> results = databaseQueryService.executeQuery(sql);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", results);
            response.put("message", "查询执行成功");
            response.put("count", results.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("执行查询失败: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "执行查询失败: " + e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 获取表数据（分页）
     */
    @GetMapping("/table/{tableName}/data")
    public ResponseEntity<Map<String, Object>> getTableData(
            @PathVariable String tableName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            log.info("获取表数据: {}, 页码: {}, 大小: {}", tableName, page, size);
            Map<String, Object> data = databaseQueryService.getTableData(tableName, page, size);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            response.put("message", "获取表数据成功");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取表数据失败: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取表数据失败: " + e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 获取数据库统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDatabaseStats() {
        try {
            log.info("获取数据库统计信息");
            Map<String, Object> stats = databaseQueryService.getDatabaseStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", stats);
            response.put("message", "获取统计信息成功");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取统计信息失败: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取统计信息失败: " + e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 删除所有DailyRecommendation数据
     */
    @DeleteMapping("/daily-recommendation/clear")
    public ResponseEntity<Map<String, Object>> clearDailyRecommendationData() {
        try {
            log.info("开始删除所有DailyRecommendation数据");
            int deletedCount = databaseQueryService.clearDailyRecommendationData();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "成功删除所有DailyRecommendation数据");
            response.put("deletedCount", deletedCount);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("删除DailyRecommendation数据失败: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "删除DailyRecommendation数据失败: " + e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }
}
