package com.biomebounty.database.dao;

import com.biomebounty.database.DatabaseManager;

import java.sql.*;
import java.util.*;

public class TaskGroupDAO {
    private final DatabaseManager db;
    public TaskGroupDAO(DatabaseManager db) { this.db = db; }

    /**
     * 创建任务组
     * @return 生成的 group id
     */
    public int createGroup(UUID playerId, String worldName, String biomeId,
                           String title, int rewardGold, String date) {
        String sql = db.isMySQL() ?
                "INSERT INTO task_groups(player_uuid,world_name,biome_id,title,reward_gold,assigned_date) VALUES(?,?,?,?,?,?)" :
                "INSERT INTO task_groups(player_uuid,world_name,biome_id,title,reward_gold,assigned_date) VALUES(?,?,?,?,?,?)";
        try (Connection c = db.getConnection();
             PreparedStatement s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1, playerId.toString()); s.setString(2, worldName);
            s.setString(3, biomeId); s.setString(4, title);
            s.setInt(5, rewardGold); s.setString(6, date);
            s.executeUpdate();
            ResultSet keys = s.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }

    /**
     * 获取玩家今日所有任务组
     */
    public List<TaskGroup> getTodayGroups(UUID playerId, String worldName, String date) {
        List<TaskGroup> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT * FROM task_groups WHERE player_uuid=? AND world_name=? AND assigned_date=? ORDER BY id")) {
            s.setString(1, playerId.toString()); s.setString(2, worldName); s.setString(3, date);
            ResultSet rs = s.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /**
     * 更新任务组状态
     */
    public void updateStatus(int groupId, String status) {
        String sql = "claimed".equals(status) ?
                "UPDATE task_groups SET status=?, claimed_at=CURRENT_TIMESTAMP WHERE id=?" :
                "UPDATE task_groups SET status=? WHERE id=?";
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, status); s.setInt(2, groupId);
            s.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    /**
     * 删除指定日期之前的任务组
     */
    public int cleanupBefore(String date) {
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "DELETE FROM task_groups WHERE assigned_date < ?")) {
            s.setString(1, date);
            return s.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); return 0; }
    }

    /**
     * 重置所有
     */
    public int resetAll() {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            return s.executeUpdate("DELETE FROM task_groups");
        } catch (SQLException e) { e.printStackTrace(); return 0; }
    }

    private TaskGroup map(ResultSet rs) throws SQLException {
        return new TaskGroup(
                rs.getInt("id"), UUID.fromString(rs.getString("player_uuid")),
                rs.getString("world_name"), rs.getString("biome_id"),
                rs.getString("title"), rs.getInt("reward_gold"),
                rs.getString("status"), rs.getString("assigned_date"));
    }

    public static class TaskGroup {
        public final int id;
        public final UUID playerId;
        public final String worldName, biomeId, title, status, assignedDate;
        public final int rewardGold;

        public TaskGroup(int id, UUID playerId, String worldName, String biomeId,
                         String title, int rewardGold, String status, String assignedDate) {
            this.id = id; this.playerId = playerId; this.worldName = worldName;
            this.biomeId = biomeId; this.title = title; this.rewardGold = rewardGold;
            this.status = status; this.assignedDate = assignedDate;
        }

        public boolean isPending() { return "pending".equals(status); }
        public boolean isActive() { return "active".equals(status); }
        public boolean isCompleted() { return "completed".equals(status); }
        public boolean isClaimed() { return "claimed".equals(status); }
    }
}
