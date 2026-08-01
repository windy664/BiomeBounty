package com.biomebounty.database;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public interface DatabaseManager {
    void initialize(File dataFolder);
    void shutdown();
    Connection getConnection() throws SQLException;
    boolean isMySQL();
    void createTables();
}
