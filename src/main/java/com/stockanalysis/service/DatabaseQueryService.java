package com.stockanalysis.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;

/**
 * 数据库查询服务
 */
@Slf4j
@Service
public class DatabaseQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Autowired
    public DatabaseQueryService(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    /**
     * 获取所有表名
     */
    public List<String> getAllTables() {
        List<String> tables = new ArrayList<>();
        try {
            DatabaseMetaData metaData = dataSource.getConnection().getMetaData();
            ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"});
            
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                tables.add(tableName);
            }
            rs.close();
        } catch (Exception e) {
            log.error("获取表名失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取表名失败", e);
        }
        return tables;
    }

    /**
     * 获取表结构
     */
    public List<Map<String, Object>> getTableStructure(String tableName) {
        List<Map<String, Object>> structure = new ArrayList<>();
        try {
            DatabaseMetaData metaData = dataSource.getConnection().getMetaData();
            ResultSet rs = metaData.getColumns(null, null, tableName, "%");
            
            while (rs.next()) {
                Map<String, Object> column = new HashMap<>();
                column.put("columnName", rs.getString("COLUMN_NAME"));
                column.put("dataType", rs.getString("TYPE_NAME"));
                column.put("columnSize", rs.getInt("COLUMN_SIZE"));
                column.put("nullable", rs.getInt("NULLABLE"));
                column.put("columnDefault", rs.getString("COLUMN_DEF"));
                column.put("remarks", rs.getString("REMARKS"));
                structure.add(column);
            }
            rs.close();
        } catch (Exception e) {
            log.error("获取表结构失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取表结构失败", e);
        }
        return structure;
    }

    /**
     * 执行自定义SQL查询
     */
    public List<Map<String, Object>> executeQuery(String sql) {
        try {
            // 安全检查：只允许SELECT语句
            String trimmedSql = sql.trim().toLowerCase();
            if (!trimmedSql.startsWith("select")) {
                throw new IllegalArgumentException("只允许执行SELECT查询语句");
            }
            
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.error("执行查询失败: {}", e.getMessage(), e);
            throw new RuntimeException("执行查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取表数据（分页）
     */
    public Map<String, Object> getTableData(String tableName, int page, int size) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 获取总记录数
            String countSql = "SELECT COUNT(*) FROM " + tableName;
            int total = jdbcTemplate.queryForObject(countSql, Integer.class);
            
            // 计算分页参数
            int offset = (page - 1) * size;
            String dataSql = "SELECT * FROM " + tableName + " LIMIT " + size + " OFFSET " + offset;
            List<Map<String, Object>> data = jdbcTemplate.queryForList(dataSql);
            
            result.put("data", data);
            result.put("total", total);
            result.put("page", page);
            result.put("size", size);
            result.put("totalPages", (int) Math.ceil((double) total / size));
            
        } catch (Exception e) {
            log.error("获取表数据失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取表数据失败: " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * 获取数据库统计信息
     */
    public Map<String, Object> getDatabaseStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            List<String> tables = getAllTables();
            stats.put("totalTables", tables.size());
            stats.put("tables", tables);
            
            // 获取每个表的记录数
            Map<String, Integer> tableCounts = new HashMap<>();
            for (String table : tables) {
                try {
                    String countSql = "SELECT COUNT(*) FROM " + table;
                    int count = jdbcTemplate.queryForObject(countSql, Integer.class);
                    tableCounts.put(table, count);
                } catch (Exception e) {
                    log.warn("获取表 {} 记录数失败: {}", table, e.getMessage());
                    tableCounts.put(table, -1);
                }
            }
            stats.put("tableCounts", tableCounts);
            
            // 获取数据库版本信息
            try {
                String version = jdbcTemplate.queryForObject("SELECT version()", String.class);
                stats.put("databaseVersion", version);
            } catch (Exception e) {
                stats.put("databaseVersion", "未知");
            }
            
        } catch (Exception e) {
            log.error("获取数据库统计信息失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库统计信息失败", e);
        }
        return stats;
    }

    /**
     * 获取表的列信息
     */
    public List<String> getTableColumns(String tableName) {
        List<String> columns = new ArrayList<>();
        try {
            String sql = "SELECT * FROM " + tableName + " LIMIT 1";
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            
            if (!result.isEmpty()) {
                columns.addAll(result.get(0).keySet());
            }
        } catch (Exception e) {
            log.error("获取表列信息失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取表列信息失败", e);
        }
        return columns;
    }

    /**
     * 删除所有DailyRecommendation数据
     */
    public int clearDailyRecommendationData() {
        try {
            log.info("开始删除所有DailyRecommendation相关数据");
            
            // 先删除股票推荐详情表数据（因为有外键约束）
            String deleteStockDetailsSql = "DELETE FROM stock_recommendation_detail";
            int deletedStockDetails = jdbcTemplate.update(deleteStockDetailsSql);
            log.info("删除了 {} 条股票推荐详情记录", deletedStockDetails);
            
            // 再删除每日推荐表数据
            String deleteDailyRecommendationSql = "DELETE FROM daily_recommendation";
            int deletedDailyRecommendations = jdbcTemplate.update(deleteDailyRecommendationSql);
            log.info("删除了 {} 条每日推荐记录", deletedDailyRecommendations);
            
            int totalDeleted = deletedStockDetails + deletedDailyRecommendations;
            log.info("总共删除了 {} 条记录", totalDeleted);
            
            return totalDeleted;
        } catch (Exception e) {
            log.error("删除DailyRecommendation数据失败: {}", e.getMessage(), e);
            throw new RuntimeException("删除DailyRecommendation数据失败", e);
        }
    }
}
