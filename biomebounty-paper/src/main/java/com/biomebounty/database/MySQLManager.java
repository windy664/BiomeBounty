package com.biomebounty.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public class MySQLManager implements DatabaseManager {

    private HikariDataSource dataSource;
    private final String host, database, username, password;
    private final int port, maxPoolSize, minIdle;
    private final long maxLifetime, connectionTimeout;

    public MySQLManager(String host, int port, String database, String username, String password,
                        int maxPoolSize, int minIdle, long maxLifetime, long connectionTimeout) {
        this.host = host; this.port = port; this.database = database;
        this.username = username; this.password = password;
        this.maxPoolSize = maxPoolSize; this.minIdle = minIdle;
        this.maxLifetime = maxLifetime; this.connectionTimeout = connectionTimeout;
    }

    @Override
    public void initialize(File dataFolder) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database +
                    "?useSSL=false&autoReconnect=true&characterEncoding=utf8");
            config.setUsername(username); config.setPassword(password);
            config.setMaximumPoolSize(maxPoolSize); config.setMinimumIdle(minIdle);
            config.setMaxLifetime(maxLifetime); config.setConnectionTimeout(connectionTimeout);
            dataSource = new HikariDataSource(config);
            createTables();
        } catch (Exception e) { throw new RuntimeException("MySQL init failed", e); }
    }

    @Override
    public void shutdown() { if (dataSource != null) dataSource.close(); }

    @Override
    public Connection getConnection() throws SQLException { return dataSource.getConnection(); }

    @Override
    public boolean isMySQL() { return true; }

    @Override
    public void createTables() { TableCreator.createTables(this); }
}
