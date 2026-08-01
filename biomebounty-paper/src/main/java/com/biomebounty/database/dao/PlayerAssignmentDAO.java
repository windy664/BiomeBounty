package com.biomebounty.database.dao;

import com.biomebounty.database.DatabaseManager;

import java.sql.*;
import java.util.*;

/**
 * 玩家每日任务分配 DAO
 */
public class PlayerAssignmentDAO {
    private final DatabaseManager db;
    public PlayerAssignmentDAO(DatabaseManager db) { this.db = db; }

    /**
     * 分配任务给玩家（挂到任务组下）
     */
    public void assign(UUID playerId, String worldName, int groupId, int poolId, String biomeId,
                       String taskType, String target, int amount, String date) {
        String sql = "INSERT INTO player_assignments(player_uuid,world_name,group_id,pool_id,biome_id,task_type,target,amount,assigned_date) VALUES(?,?,?,?,?,?,?,?,?)";
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, playerId.toString()); s.setString(2, worldName);
            s.setInt(3, groupId); s.setInt(4, poolId);
            s.setString(5, biomeId); s.setString(6, taskType);
            s.setString(7, target); s.setInt(8, amount);
            s.setString(9, date);
            s.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    /**
     * 激活指定任务组下的所有任务（pending → active）
     */
    public void activateByGroup(int groupId) {
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "UPDATE player_assignments SET status='active', accepted_at=CURRENT_TIMESTAMP WHERE group_id=? AND status='pending'")) {
            s.setInt(1, groupId);
            s.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    /**
     * 获取指定任务组下的所有分配
     */
    public List<Assignment> getAssignmentsByGroup(int groupId) {
        List<Assignment> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT * FROM player_assignments WHERE group_id=? ORDER BY id")) {
            s.setInt(1, groupId);
            ResultSet rs = s.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /**
     * 获取玩家今日分配（指定状态）
     */
    public List<Assignment> getAssignments(UUID playerId, String worldName, String date, String status) {
        List<Assignment> list = new ArrayList<>();
        String sql = status != null ?
                "SELECT * FROM player_assignments WHERE player_uuid=? AND world_name=? AND assigned_date=? AND status=?" :
                "SELECT * FROM player_assignments WHERE player_uuid=? AND world_name=? AND assigned_date=?";
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, playerId.toString()); s.setString(2, worldName); s.setString(3, date);
            if (status != null) s.setString(4, status);
            ResultSet rs = s.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /**
     * 获取玩家今日所有分配
     */
    public List<Assignment> getTodayAssignments(UUID playerId, String worldName, String date) {
        return getAssignments(playerId, worldName, date, null);
    }

    /**
     * 领取任务（pending → active）
     */
    public boolean acceptTask(UUID playerId, int assignmentId) {
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "UPDATE player_assignments SET status='active', accepted_at=CURRENT_TIMESTAMP " +
                        "WHERE id=? AND player_uuid=? AND status='pending'")) {
            s.setInt(1, assignmentId); s.setString(2, playerId.toString());
            return s.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    /**
     * 增量更新进度，达到目标自动完成
     * @return 新进度值，-1 表示不存在或非 active
     */
    public int addProgress(UUID playerId, int assignmentId, int delta) {
        try (Connection c = db.getConnection()) {
            // 先读当前进度和目标
            PreparedStatement s = c.prepareStatement(
                    "SELECT progress, amount FROM player_assignments WHERE id=? AND player_uuid=? AND status='active'");
            s.setInt(1, assignmentId); s.setString(2, playerId.toString());
            ResultSet rs = s.executeQuery();
            if (!rs.next()) return -1;
            int oldProgress = rs.getInt("progress");
            int amount = rs.getInt("amount");
            int newProgress = Math.min(oldProgress + delta, amount);

            // 写回新进度
            PreparedStatement u = c.prepareStatement(
                    "UPDATE player_assignments SET progress=? WHERE id=? AND player_uuid=? AND status='active'");
            u.setInt(1, newProgress); u.setInt(2, assignmentId); u.setString(3, playerId.toString());
            u.executeUpdate();

            // 达到目标自动完成
            if (newProgress >= amount) {
                completeTask(playerId, assignmentId);
            }
            return newProgress;
        } catch (SQLException e) { e.printStackTrace(); return -1; }
    }

    /**
     * 根据任务类型+目标+群系查找玩家今日 active 的分配
     */
    public List<Assignment> findActiveByTarget(UUID playerId, String worldName, String date,
                                                String taskType, String biomeId) {
        List<Assignment> list = new ArrayList<>();
        String sql = "SELECT * FROM player_assignments WHERE player_uuid=? AND world_name=? " +
                "AND assigned_date=? AND status='active' AND task_type=? AND biome_id=?";
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, playerId.toString()); s.setString(2, worldName);
            s.setString(3, date); s.setString(4, taskType); s.setString(5, biomeId);
            ResultSet rs = s.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /**
     * 获取玩家指定日期之后的分配（用于难度校准）
     */
    public List<Assignment> getAssignmentsSince(UUID playerId, String sinceDate) {
        List<Assignment> list = new ArrayList<>();
        String sql = "SELECT * FROM player_assignments WHERE player_uuid=? AND assigned_date >= ? ORDER BY assigned_date DESC";
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, playerId.toString()); s.setString(2, sinceDate);
            ResultSet rs = s.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /**
     * 清理指定日期之前的分配
     */
    public int cleanupBefore(String date) {
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "DELETE FROM player_assignments WHERE assigned_date < ?")) {
            s.setString(1, date);
            return s.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); return 0; }
    }

    /**
     * 重置所有为 pending（每日重置用）
     */
    public int resetAllAssignments() {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            return s.executeUpdate("DELETE FROM player_assignments");
        } catch (SQLException e) { e.printStackTrace(); return 0; }
    }

    /**
     * 完成任务（active → completed）
     */
    public void completeTask(UUID playerId, int assignmentId) {
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "UPDATE player_assignments SET status='completed', completed_at=CURRENT_TIMESTAMP " +
                        "WHERE id=? AND player_uuid=?")) {
            s.setInt(1, assignmentId); s.setString(2, playerId.toString());
            s.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    /**
     * 领取奖励（completed → claimed）
     */
    public boolean claimReward(UUID playerId, int assignmentId) {
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "UPDATE player_assignments SET status='claimed' WHERE id=? AND player_uuid=? AND status='completed'")) {
            s.setInt(1, assignmentId); s.setString(2, playerId.toString());
            return s.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    /**
     * 放弃任务
     */
    public void abandonTask(UUID playerId, int assignmentId) {
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "UPDATE player_assignments SET status='abandoned' WHERE id=? AND player_uuid=? AND status='active'")) {
            s.setInt(1, assignmentId); s.setString(2, playerId.toString());
            s.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private Assignment map(ResultSet rs) throws SQLException {
        int groupId = rs.getInt("group_id");
        return new Assignment(
                rs.getInt("id"), UUID.fromString(rs.getString("player_uuid")),
                rs.getString("world_name"), groupId, rs.getInt("pool_id"),
                rs.getString("biome_id"), rs.getString("task_type"),
                rs.getString("target"), rs.getInt("amount"),
                rs.getInt("progress"), rs.getString("status"),
                rs.getString("assigned_date"));
    }

    /**
     * 玩家任务分配
     */
    public static class Assignment {
        public final int id;
        public final UUID playerId;
        public final String worldName;
        public final int groupId, poolId;
        public final String biomeId, taskType, target, status, assignedDate;
        public final int amount, progress;

        public Assignment(int id, UUID playerId, String worldName, int groupId, int poolId,
                          String biomeId, String taskType, String target,
                          int amount, int progress, String status, String assignedDate) {
            this.id = id; this.playerId = playerId; this.worldName = worldName;
            this.groupId = groupId; this.poolId = poolId;
            this.biomeId = biomeId; this.taskType = taskType;
            this.target = target; this.amount = amount; this.progress = progress;
            this.status = status; this.assignedDate = assignedDate;
        }

        public boolean isPending() { return "pending".equals(status); }
        public boolean isActive() { return "active".equals(status); }
        public boolean isCompleted() { return "completed".equals(status); }
        public boolean isClaimed() { return "claimed".equals(status); }
        public boolean isDone() { return isCompleted() || isClaimed(); }
        public String getProgressString() { return progress + "/" + amount; }
    }
}
