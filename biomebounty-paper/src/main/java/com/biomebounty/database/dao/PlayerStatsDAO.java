package com.biomebounty.database.dao;

import com.biomebounty.database.DatabaseManager;
import java.sql.*;
import java.util.*;

public class PlayerStatsDAO {
    private final DatabaseManager db;
    public PlayerStatsDAO(DatabaseManager db) { this.db = db; }

    public void incrementCompleted(UUID playerId, String name) {
        String sql = db.isMySQL() ?
                "INSERT INTO player_stats(player_uuid,player_name,total_completed,last_reset_date) VALUES(?,?,1,CURDATE()) ON DUPLICATE KEY UPDATE total_completed=total_completed+1,player_name=VALUES(player_name)" :
                "INSERT INTO player_stats(player_uuid,player_name,total_completed,last_reset_date) VALUES(?,?,1,date('now')) ON CONFLICT(player_uuid) DO UPDATE SET total_completed=total_completed+1";
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, playerId.toString()); s.setString(2, name); s.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void incrementAbandoned(UUID playerId) {
        String sql = db.isMySQL() ?
                "INSERT INTO player_stats(player_uuid,total_abandoned) VALUES(?,1) ON DUPLICATE KEY UPDATE total_abandoned=total_abandoned+1" :
                "INSERT INTO player_stats(player_uuid,total_abandoned) VALUES(?,1) ON CONFLICT(player_uuid) DO UPDATE SET total_abandoned=total_abandoned+1";
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, playerId.toString()); s.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void incrementGold(UUID playerId, int amount) {
        String sql = db.isMySQL() ?
                "INSERT INTO player_stats(player_uuid,total_gold_earned) VALUES(?,?) ON DUPLICATE KEY UPDATE total_gold_earned=total_gold_earned+?" :
                "INSERT INTO player_stats(player_uuid,total_gold_earned) VALUES(?,?) ON CONFLICT(player_uuid) DO UPDATE SET total_gold_earned=total_gold_earned+?";
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, playerId.toString()); s.setInt(2, amount); s.setInt(3, amount); s.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public int getTotalCompleted(UUID playerId) {
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement("SELECT total_completed FROM player_stats WHERE player_uuid=?")) {
            s.setString(1, playerId.toString()); ResultSet rs = s.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public int getCurrentStreak(UUID playerId) {
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement("SELECT current_streak FROM player_stats WHERE player_uuid=?")) {
            s.setString(1, playerId.toString()); ResultSet rs = s.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public int getBestStreak(UUID playerId) {
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement("SELECT best_streak FROM player_stats WHERE player_uuid=?")) {
            s.setString(1, playerId.toString()); ResultSet rs = s.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public int getRank(UUID playerId) {
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT COUNT(*)+1 FROM player_stats WHERE total_completed>(SELECT total_completed FROM player_stats WHERE player_uuid=?)")) {
            s.setString(1, playerId.toString()); ResultSet rs = s.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public List<Map.Entry<UUID, Integer>> getTopPlayers(int limit) {
        List<Map.Entry<UUID, Integer>> top = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT player_uuid,total_completed FROM player_stats ORDER BY total_completed DESC LIMIT ?")) {
            s.setInt(1, limit); ResultSet rs = s.executeQuery();
            while (rs.next()) top.add(new AbstractMap.SimpleEntry<>(UUID.fromString(rs.getString(1)), rs.getInt(2)));
        } catch (SQLException e) { e.printStackTrace(); }
        return top;
    }

    /**
     * 获取玩家最近N天的每日收益（从 task_groups 的已领取奖励统计）
     * 返回列表，index 0 = 最早一天，index N-1 = 最近一天
     */
    /**
     * 获取今日全服活跃玩家数（已领取过奖励的）
     */
    public int getTodayActivePlayerCount() {
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT COUNT(DISTINCT player_uuid) FROM task_groups WHERE assigned_date=date('now') AND status IN ('active','completed','claimed')")) {
            ResultSet rs = s.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public List<Integer> getRecentDailyEarnings(UUID playerId, int days) {
        List<Integer> earnings = new ArrayList<>();
        String sql = db.isMySQL() ?
                "SELECT assigned_date, SUM(reward_gold) as daily_total FROM task_groups " +
                        "WHERE player_uuid=? AND status='claimed' AND assigned_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
                        "GROUP BY assigned_date ORDER BY assigned_date" :
                "SELECT assigned_date, SUM(reward_gold) as daily_total FROM task_groups " +
                        "WHERE player_uuid=? AND status='claimed' AND assigned_date >= date('now', '-' || ? || ' days') " +
                        "GROUP BY assigned_date ORDER BY assigned_date";
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, playerId.toString()); s.setInt(2, days);
            ResultSet rs = s.executeQuery();
            while (rs.next()) earnings.add(rs.getInt("daily_total"));
        } catch (SQLException e) { e.printStackTrace(); }
        return earnings;
    }

    public void updateStreak(UUID playerId, boolean completed) {
        int current = completed ? getCurrentStreak(playerId) + 1 : 0;
        int best = Math.max(current, getBestStreak(playerId));
        String sql = db.isMySQL() ?
                "INSERT INTO player_stats(player_uuid,current_streak,best_streak) VALUES(?,?,?) ON DUPLICATE KEY UPDATE current_streak=VALUES(current_streak),best_streak=GREATEST(best_streak,VALUES(best_streak))" :
                "INSERT INTO player_stats(player_uuid,current_streak,best_streak) VALUES(?,?,?) ON CONFLICT(player_uuid) DO UPDATE SET current_streak=excluded.current_streak,best_streak=MAX(best_streak,excluded.best_streak)";
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, playerId.toString()); s.setInt(2, current); s.setInt(3, best); s.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
}
