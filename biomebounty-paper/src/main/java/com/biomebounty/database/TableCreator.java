package com.biomebounty.database;

import java.sql.*;
import java.util.*;

public class TableCreator {

    /** 当前 schema 版本，新增 migration 时 +1 */
    private static final int CURRENT_VERSION = 1;

    public static void createTables(DatabaseManager db) {
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement()) {

            // —— 1. 建 schema_version 表（不存在才建） ——
            stmt.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");

            // 读当前版本
            int oldVersion = 0;
            try (ResultSet rs = stmt.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
                if (rs.next()) oldVersion = rs.getInt(1);
            }

            // —— 2. 建业务表（IF NOT EXISTS，不 DROP） ——
            createIfNotExists(conn, db.isMySQL());

            // —— 3. 跑 migration ——
            if (oldVersion < CURRENT_VERSION) {
                runMigrations(conn, db.isMySQL(), oldVersion);
                // 写入新版本
                try (Statement s = conn.createStatement()) {
                    s.executeUpdate("DELETE FROM schema_version");
                    s.executeUpdate("INSERT INTO schema_version VALUES(" + CURRENT_VERSION + ")");
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Table creation failed", e);
        }
    }

    // ==================== 建表 ====================

    private static void createIfNotExists(Connection conn, boolean mysql) throws SQLException {
        String auto = mysql ? "AUTO_INCREMENT" : "AUTOINCREMENT";
        String dateType = mysql ? "DATE" : "TEXT";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS player_stats (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(64), " +
                    "total_completed INTEGER DEFAULT 0, total_abandoned INTEGER DEFAULT 0, " +
                    "total_gold_earned INTEGER DEFAULT 0, total_exp_earned INTEGER DEFAULT 0, " +
                    "last_reset_date " + dateType + ", " +
                    "current_streak INTEGER DEFAULT 0, best_streak INTEGER DEFAULT 0)");

            stmt.execute("CREATE TABLE IF NOT EXISTS task_pool (" +
                    "id INTEGER PRIMARY KEY " + auto + ", " +
                    "biome_id VARCHAR(64) NOT NULL, task_type VARCHAR(32) NOT NULL, " +
                    "target VARCHAR(64) NOT NULL, description VARCHAR(128), " +
                    "min_amount INTEGER NOT NULL, max_amount INTEGER NOT NULL, " +
                    "weight INTEGER DEFAULT 10, enabled BOOLEAN DEFAULT TRUE, " +
                    "UNIQUE(biome_id, task_type, target))");

            stmt.execute("CREATE TABLE IF NOT EXISTS task_groups (" +
                    "id INTEGER PRIMARY KEY " + auto + ", " +
                    "player_uuid VARCHAR(36) NOT NULL, world_name VARCHAR(64) NOT NULL, " +
                    "biome_id VARCHAR(64) NOT NULL, title VARCHAR(128), " +
                    "reward_gold INTEGER DEFAULT 0, " +
                    "status VARCHAR(16) DEFAULT 'pending', " +
                    "assigned_date " + dateType + " NOT NULL, " +
                    "claimed_at TIMESTAMP)");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_assignments (" +
                    "id INTEGER PRIMARY KEY " + auto + ", " +
                    "player_uuid VARCHAR(36) NOT NULL, world_name VARCHAR(64) NOT NULL, " +
                    "group_id INTEGER, pool_id INTEGER NOT NULL, biome_id VARCHAR(64) NOT NULL, " +
                    "task_type VARCHAR(32) NOT NULL, target VARCHAR(64) NOT NULL, " +
                    "amount INTEGER NOT NULL, progress INTEGER DEFAULT 0, " +
                    "status VARCHAR(16) DEFAULT 'pending', " +
                    "assigned_date " + dateType + " NOT NULL, " +
                    "accepted_at TIMESTAMP, completed_at TIMESTAMP)");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_biome_history (" +
                    "player_uuid VARCHAR(36) NOT NULL, biome_id VARCHAR(64) NOT NULL, " +
                    "times_assigned INTEGER DEFAULT 0, times_completed INTEGER DEFAULT 0, " +
                    "last_assigned_date " + dateType + ", " +
                    "last_completed_date " + dateType + ", " +
                    "consecutive_days INTEGER DEFAULT 0, " +
                    "PRIMARY KEY(player_uuid, biome_id))");
        }
    }

    // ==================== Migration ====================

    /**
     * 逐版本迁移，每步只跑一次
     * 示例：
     *   if (from < 1) { migrateV0toV1(conn, mysql); }
     *   if (from < 2) { migrateV1toV2(conn, mysql); }
     */
    private static void runMigrations(Connection conn, boolean mysql, int from) throws SQLException {
        // if (from < 1) { migrateV0toV1(conn, mysql); }
        // if (from < 2) { migrateV1toV2(conn, mysql); }
        // 当前 v0→v1 无结构变更，只是从"每次都 DROP"迁移到"保数据"，不需要改列
    }

    // ==================== 工具方法 ====================

    /**
     * 检查某张表是否有某列（migration 加列时用）
     */
    public static boolean columnExists(Connection conn, boolean mysql, String table, String column) throws SQLException {
        if (mysql) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?")) {
                ps.setString(1, table);
                ps.setString(2, column);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } else {
            // SQLite: PRAGMA table_info
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
                while (rs.next()) {
                    if (column.equalsIgnoreCase(rs.getString("name"))) return true;
                }
            }
        }
        return false;
    }

    /**
     * 安全加列（已存在则跳过）
     */
    public static void addColumnIfMissing(Connection conn, boolean mysql, String table, String columnDef) throws SQLException {
        // columnDef 格式："column_name TYPE DEFAULT ..."
        String colName = columnDef.trim().split("\\s+")[0];
        if (!columnExists(conn, mysql, table, colName)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + columnDef);
            }
        }
    }
}
