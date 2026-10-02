package com.basinwatch.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class OperationalEventRepository {
    private static final String INSERT = """
            INSERT INTO operational_events (timestamp, event_type, zone, message)
            VALUES (?, ?, ?, ?)
            """;
    private final DatabaseManager database;

    public OperationalEventRepository(DatabaseManager database) {
        this.database = database;
    }

    public void insert(Instant timestamp, String eventType, String zone, String message)
            throws SQLException {
        try (Connection connection = database.openConnection()) {
            insert(connection, timestamp, eventType, zone, message);
        }
    }

    void insert(Connection connection, Instant timestamp, String eventType, String zone,
                String message) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, timestamp.toString());
            statement.setString(2, eventType);
            statement.setString(3, zone);
            statement.setString(4, message);
            statement.executeUpdate();
        }
    }

    public List<DatabaseHistoryEntry> findRecent(int limit) throws SQLException {
        SensorReadingRepository.validateLimit(limit);
        String sql = """
                SELECT timestamp, event_type, zone, message
                FROM operational_events
                ORDER BY id DESC
                LIMIT ?
                """;
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet results = statement.executeQuery()) {
                List<DatabaseHistoryEntry> entries = new ArrayList<>();
                while (results.next()) {
                    entries.add(new DatabaseHistoryEntry(
                            Instant.parse(results.getString("timestamp")),
                            results.getString("event_type"),
                            results.getString("zone"),
                            results.getString("message")));
                }
                return List.copyOf(entries);
            }
        }
    }
}
