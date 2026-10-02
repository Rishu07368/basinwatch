package com.basinwatch.db;

import com.basinwatch.domain.ResourceType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;

public final class ResourceUsageRepository {
    private static final String INSERT = """
            INSERT INTO resource_usage (resource_type, amount, mission_id, timestamp)
            VALUES (?, ?, ?, ?)
            """;
    private final DatabaseManager database;

    public ResourceUsageRepository(DatabaseManager database) {
        this.database = database;
    }

    void insert(Connection connection, String missionId, Map<ResourceType, Integer> usage,
                Instant timestamp) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            for (Map.Entry<ResourceType, Integer> entry : usage.entrySet()) {
                statement.setString(1, entry.getKey().name());
                statement.setInt(2, entry.getValue());
                statement.setString(3, missionId);
                statement.setString(4, timestamp.toString());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public java.util.List<DatabaseHistoryEntry> findRecent(int limit) throws SQLException {
        SensorReadingRepository.validateLimit(limit);
        String sql = """
                SELECT ru.timestamp, ru.resource_type, ru.amount, ru.mission_id, m.zone
                FROM resource_usage ru
                JOIN missions m ON m.id = ru.mission_id
                ORDER BY ru.id DESC
                LIMIT ?
                """;
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (java.sql.ResultSet results = statement.executeQuery()) {
                java.util.List<DatabaseHistoryEntry> entries = new java.util.ArrayList<>();
                while (results.next()) {
                    entries.add(new DatabaseHistoryEntry(
                            Instant.parse(results.getString("timestamp")),
                            results.getString("resource_type"),
                            results.getString("zone"),
                            results.getInt("amount") + " unit(s) assigned to "
                                    + results.getString("mission_id")));
                }
                return java.util.List.copyOf(entries);
            }
        }
    }
}
