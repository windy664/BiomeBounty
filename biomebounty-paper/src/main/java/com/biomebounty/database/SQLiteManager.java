package com.biomebounty.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteManager implements DatabaseManager {

    private Connection connection;
    private final File dbFile;

    public SQLiteManager(File dbFile) {
        this.dbFile = dbFile;
    }

    @Override
    public void initialize(File dataFolder) {
        try {
            Class.forName("org.sqlite.JDBC");
            if (!dbFile.getParentFile().exists()) dbFile.getParentFile().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
            }
            createTables();
        } catch (Exception e) {
            throw new RuntimeException("SQLite init failed", e);
        }
    }

    @Override
    public void shutdown() {
        try { if (connection != null && !connection.isClosed()) connection.close(); }
        catch (SQLException e) { /* ignore */ }
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        }
        return connection;
    }

    @Override
    public boolean isMySQL() { return false; }

    @Override
    public void createTables() { TableCreator.createTables(this); }
}
