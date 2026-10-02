package com.basinwatch.db;

import com.basinwatch.domain.SensorReading;
import com.basinwatch.domain.ZoneSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SensorReadingRepository {
    private static final String INSERT = """
            INSERT INTO sensor_readings
                (timestamp, zone, water_level, rainfall, risk_level)
            VALUES (?, ?, ?, ?, ?)
            """;
    private final DatabaseManager database;

    public SensorReadingRepository(DatabaseManager database) {
        this.database = database;
    }

    public void insert(SensorReading reading, ZoneSnapshot zone, Instant timestamp)
            throws SQLException {
        try (Connection connection = database.openConnection()) {
            insert(connection, reading, zone, timestamp);
        }
    }

    void insert(Connection connection, SensorReading reading, ZoneSnapshot zone,
                Instant timestamp) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, timestamp.toString());
            statement.setString(2, zone.id());
            statement.setDouble(3, zone.waterLevelMeters());
            statement.setDouble(4, reading.rainfallMillimeters());
            statement.setString(5, zone.risk().name());
            statement.executeUpdate();
        }
    }

    public List<DatabaseHistoryEntry> findRecent(int limit) throws SQLException {
        validateLimit(limit);
        String sql = """
                SELECT timestamp, zone, water_level, rainfall, risk_level
                FROM sensor_readings
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
                            "SENSOR",
                            results.getString("zone"),
                            String.format(java.util.Locale.ROOT, "%.2f m water · %.1f mm rain · %s",
                                    results.getDouble("water_level"),
                                    results.getDouble("rainfall"),
                                    results.getString("risk_level"))));
                }
                return List.copyOf(entries);
            }
        }
    }

    public int deleteBefore(Instant cutoff) throws SQLException {
        String sql = "DELETE FROM sensor_readings WHERE julianday(timestamp) < julianday(?)";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, cutoff.toString());
            return statement.executeUpdate();
        }
    }

    static void validateLimit(int limit) {
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("History limit must be between 1 and 500.");
        }
    }
}
