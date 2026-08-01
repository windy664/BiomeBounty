package com.biomebounty.database.dao;

import com.biomebounty.database.DatabaseManager;

import java.sql.*;
import java.util.*;

/**
 * 玩家群系历史 DAO
 * 记录玩家在各群系的任务历史，用于分配算法的去重和偏好
 */
public class PlayerBiomeHistoryDAO {
    private final DatabaseManager db;
    public PlayerBiomeHistoryDAO(DatabaseManager db) { this.db = db; }

    /**
     * 记录群系分配
     */
    public void recordAssignment(UUID playerId, String biomeId, String date) {
        String sql = db.isMySQL() ?
                "INSERT INTO player_biome_history(player_uuid,biome_id,times_assigned,last_assigned_date) " +
                        "VALUES(?,?,1,?) ON DUPLICATE KEY UPDATE times_assigned=times_assigned+1,last_assigned_date=VALUES(last_assigned_date)" :
                "INSERT INTO player_biome_history(player_uuid,biome_id,times_assigned,last_assigned_date) " +
                        "VALUES(?,?,1,?) ON CONFLICT(player_uuid,biome_id) DO UPDATE SET times_assigned=times_assigned+1,last_assigned_date=excluded.last_assigned_date";
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, playerId.toString()); s.setString(2, biomeId); s.setString(3, date);
            s.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    /**
     * 记录群系完成
     */
    public void recordCompletion(UUID playerId, String biomeId, String date) {
        String sql = db.isMySQL() ?
                "UPDATE player_biome_history SET times_completed=times_completed+1,last_completed_date=? " +
                        "WHERE player_uuid=? AND biome_id=?" :
                "UPDATE player_biome_history SET times_completed=times_completed+1,last_completed_date=? " +
                        "WHERE player_uuid=? AND biome_id=?";
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, date); s.setString(2, playerId.toString()); s.setString(3, biomeId);
            s.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    /**
     * 获取玩家最近 N 天分配过的群系
     */
    public Set<String> getRecentBiomes(UUID playerId, int days, String today) {
        Set<String> set = new LinkedHashSet<>();
        // 在 Java 端算好截止日期，直接传参
        String cutoff = java.time.LocalDate.parse(today).minusDays(days).toString();
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT DISTINCT biome_id FROM player_biome_history " +
                        "WHERE player_uuid=? AND last_assigned_date >= ?")) {
            s.setString(1, playerId.toString());
            s.setString(2, cutoff);
            ResultSet rs = s.executeQuery();
            while (rs.next()) set.add(rs.getString(1));
        } catch (SQLException e) { e.printStackTrace(); }
        return set;
    }

    /**
     * 获取玩家群系偏好（按完成次数排序）
     */
    public Map<String, Integer> getBiomePreferences(UUID playerId) {
        Map<String, Integer> map = new LinkedHashMap<>();
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT biome_id, times_completed FROM player_biome_history WHERE player_uuid=? ORDER BY times_completed DESC")) {
            s.setString(1, playerId.toString());
            ResultSet rs = s.executeQuery();
            while (rs.next()) map.put(rs.getString(1), rs.getInt(2));
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }

    /**
     * 获取连续分配天数
     */
    public int getConsecutiveDays(UUID playerId, String biomeId) {
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT consecutive_days FROM player_biome_history WHERE player_uuid=? AND biome_id=?")) {
            s.setString(1, playerId.toString()); s.setString(2, biomeId);
            ResultSet rs = s.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    /**
     * 更新连续天数
     */
    public void updateConsecutiveDays(UUID playerId, String biomeId, int days) {
        String sql = db.isMySQL() ?
                "INSERT INTO player_biome_history(player_uuid,biome_id,consecutive_days) VALUES(?,?,?) " +
                        "ON DUPLICATE KEY UPDATE consecutive_days=VALUES(consecutive_days)" :
                "INSERT INTO player_biome_history(player_uuid,biome_id,consecutive_days) VALUES(?,?,?) " +
                        "ON CONFLICT(player_uuid,biome_id) DO UPDATE SET consecutive_days=excluded.consecutive_days";
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, playerId.toString()); s.setString(2, biomeId); s.setInt(3, days);
            s.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
}
