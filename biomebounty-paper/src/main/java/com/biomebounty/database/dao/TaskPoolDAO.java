package com.biomebounty.database.dao;

import com.biomebounty.database.DatabaseManager;

import java.sql.*;
import java.util.*;

/**
 * 任务池 DAO
 * 管理全局共享的任务模板（每个群系的可用任务）
 */
public class TaskPoolDAO {
    private final DatabaseManager db;
    public TaskPoolDAO(DatabaseManager db) { this.db = db; }

    /**
     * 保存任务池条目（upsert）
     */
    public void savePoolEntry(String biomeId, String taskType, String target,
                              String description, int minAmount, int maxAmount, int weight) {
        String sql = db.isMySQL() ?
                "INSERT INTO task_pool(biome_id,task_type,target,description,min_amount,max_amount,weight) " +
                        "VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE description=VALUES(description),min_amount=VALUES(min_amount),max_amount=VALUES(max_amount),weight=VALUES(weight)" :
                "INSERT OR REPLACE INTO task_pool(biome_id,task_type,target,description,min_amount,max_amount,weight) VALUES(?,?,?,?,?,?,?)";
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, biomeId); s.setString(2, taskType); s.setString(3, target);
            s.setString(4, description); s.setInt(5, minAmount); s.setInt(6, maxAmount); s.setInt(7, weight);
            s.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    /**
     * 获取指定群系的任务池
     */
    public List<PoolEntry> getPoolByBiome(String biomeId) {
        List<PoolEntry> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT * FROM task_pool WHERE biome_id=? AND enabled=TRUE")) {
            s.setString(1, biomeId);
            ResultSet rs = s.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /**
     * 获取所有任务池（按群系分组）
     */
    public Map<String, List<PoolEntry>> getAllPool() {
        Map<String, List<PoolEntry>> map = new LinkedHashMap<>();
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT * FROM task_pool WHERE enabled=TRUE ORDER BY biome_id")) {
            ResultSet rs = s.executeQuery();
            while (rs.next()) {
                PoolEntry entry = map(rs);
                map.computeIfAbsent(entry.biomeId, k -> new ArrayList<>()).add(entry);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }

    /**
     * 清空任务池（重新生成前调用）
     */
    public void clearAll() {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM task_pool");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    /**
     * 删除指定群系的任务池条目
     */
    public void deleteByBiome(String biomeId) {
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "DELETE FROM task_pool WHERE biome_id=?")) {
            s.setString(1, biomeId);
            s.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    /**
     * 获取所有有任务的群系 ID
     */
    public Set<String> getAllBiomeIds() {
        Set<String> set = new LinkedHashSet<>();
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT DISTINCT biome_id FROM task_pool WHERE enabled=TRUE");
            while (rs.next()) set.add(rs.getString(1));
        } catch (SQLException e) { e.printStackTrace(); }
        return set;
    }

    private PoolEntry map(ResultSet rs) throws SQLException {
        return new PoolEntry(
                rs.getInt("id"), rs.getString("biome_id"),
                rs.getString("task_type"), rs.getString("target"),
                rs.getString("description"),
                rs.getInt("min_amount"), rs.getInt("max_amount"),
                rs.getInt("weight"));
    }

    /**
     * 任务池条目
     */
    public static class PoolEntry {
        public final int id;
        public final String biomeId, taskType, target, description;
        public final int minAmount, maxAmount, weight;

        public PoolEntry(int id, String biomeId, String taskType, String target,
                         String description, int minAmount, int maxAmount, int weight) {
            this.id = id; this.biomeId = biomeId; this.taskType = taskType;
            this.target = target; this.description = description;
            this.minAmount = minAmount; this.maxAmount = maxAmount; this.weight = weight;
        }
    }
}
