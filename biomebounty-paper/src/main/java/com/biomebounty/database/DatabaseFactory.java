package com.biomebounty.database;

import org.bukkit.configuration.file.FileConfiguration;
import java.io.File;

public class DatabaseFactory {
    public static DatabaseManager create(FileConfiguration config, File dataFolder) {
        String type = config.getString("database.type", "sqlite").toLowerCase();
        if ("mysql".equals(type)) {
            return new MySQLManager(
                    config.getString("database.mysql.host", "localhost"),
                    config.getInt("database.mysql.port", 3306),
                    config.getString("database.mysql.database", "biomebounty"),
                    config.getString("database.mysql.username", "root"),
                    config.getString("database.mysql.password", ""),
                    config.getInt("database.mysql.pool.max-size", 10),
                    config.getInt("database.mysql.pool.min-idle", 2),
                    config.getLong("database.mysql.pool.max-lifetime", 1800000),
                    config.getLong("database.mysql.pool.connection-timeout", 30000));
        }
        return new SQLiteManager(new File(dataFolder, config.getString("database.sqlite.file", "data.db")));
    }
}
