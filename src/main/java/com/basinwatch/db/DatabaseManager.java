package com.basinwatch.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {
    private final Path databasePath;
    private final String jdbcUrl;

    public DatabaseManager(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
        this.jdbcUrl = "jdbc:sqlite:" + this.databasePath;
    }

    public Path databasePath() {
        return databasePath;
    }

    public Connection openConnection() throws SQLException {
        Path parent = databasePath.getParent();
        if (parent == null) {
            throw new SQLException("SQLite database needs a parent directory.");
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException ex) {
            throw new SQLException("Cannot create the application database directory.", ex);
        }
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException ex) {
            try {
                connection.close();
            } catch (SQLException closeError) {
                ex.addSuppressed(closeError);
            }
            throw ex;
        }
        return connection;
    }
}
