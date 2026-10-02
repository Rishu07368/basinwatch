package com.basinwatch.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseInitializer {
    private static final String[] SCHEMA = {
            """
            CREATE TABLE IF NOT EXISTS sensor_readings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT NOT NULL,
                zone TEXT NOT NULL,
                water_level REAL NOT NULL,
                rainfall REAL NOT NULL,
                risk_level TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS missions (
                id TEXT PRIMARY KEY,
                mission_type TEXT NOT NULL,
                zone TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                completed_at TEXT
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS resource_usage (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                resource_type TEXT NOT NULL,
                amount INTEGER NOT NULL CHECK (amount > 0),
                mission_id TEXT NOT NULL REFERENCES missions(id),
                timestamp TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS operational_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT NOT NULL,
                event_type TEXT NOT NULL,
                zone TEXT,
                message TEXT NOT NULL
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_sensor_readings_time ON sensor_readings(timestamp DESC)",
            "CREATE INDEX IF NOT EXISTS idx_missions_created ON missions(created_at DESC)",
            "CREATE INDEX IF NOT EXISTS idx_events_time ON operational_events(timestamp DESC)",
            "CREATE INDEX IF NOT EXISTS idx_resource_usage_time ON resource_usage(timestamp DESC)"
    };

    private DatabaseInitializer() {
    }

    public static void initialize(DatabaseManager manager) throws SQLException {
        try (Connection connection = manager.openConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : SCHEMA) {
                statement.executeUpdate(sql);
            }
        }
    }
}
