package com.subasta.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {

    private static final Logger logger = Logger.getLogger(DatabaseManager.class.getName());

    private static final AtomicReference<DatabaseManager> instance = new AtomicReference<>();
    private final HikariDataSource dataSource;

    private DatabaseManager(String jdbcUrl, String dbUser, String dbPassword) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(dbUser);
        config.setPassword(dbPassword);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);
        this.dataSource = new HikariDataSource(config);
        logger.log(Level.INFO, () -> "DatabaseManager initialized with pool for: " + jdbcUrl);
    }

    public static DatabaseManager getInstance(String jdbcUrl, String dbUser, String dbPassword) {
        if (instance.get() == null) {
            synchronized (DatabaseManager.class) {
                if (instance.get() == null) {
                    instance.set(new DatabaseManager(jdbcUrl, dbUser, dbPassword));
                }
            }
        }
        return instance.get();
    }

    public static DatabaseManager getInstance() {
        if (instance.get() == null) {
            throw new IllegalStateException("DatabaseManager not initialized. Call getInstance(url, user, pass) first.");
        }
        return instance.get();
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("DatabaseManager pool closed");
        }
    }
}
