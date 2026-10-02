package com.basinwatch.db;

import com.basinwatch.domain.MissionSnapshot;
import com.basinwatch.domain.ResourceType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MissionRepository {
    private static final String INSERT = """
            INSERT INTO missions (id, mission_type, zone, status, created_at, completed_at)
            VALUES (?, ?, ?, ?, ?, NULL)
            """;
    private final DatabaseManager database;
    private final ResourceUsageRepository resourceUsage;
    private final OperationalEventRepository operationalEvents;

    public MissionRepository(DatabaseManager database, ResourceUsageRepository resourceUsage,
                             OperationalEventRepository operationalEvents) {
        this.database = database;
        this.resourceUsage = resourceUsage;
        this.operationalEvents = operationalEvents;
    }

    public void recordDispatch(MissionSnapshot mission, Instant createdAt,
                              Map<ResourceType, Integer> resources, String message)
            throws SQLException {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                    statement.setString(1, mission.id());
                    statement.setString(2, mission.type().name());
                    statement.setString(3, mission.zoneId());
                    statement.setString(4, mission.status().name());
                    statement.setString(5, createdAt.toString());
                    statement.executeUpdate();
                }
                resourceUsage.insert(connection, mission.id(), resources, createdAt);
                operationalEvents.insert(connection, createdAt, "MISSION DISPATCH",
                        mission.zoneId(), message);
                connection.commit();
            } catch (SQLException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
    }

    public void updateStatus(MissionSnapshot mission, Instant completedAt, String message)
            throws SQLException {
        String sql = """
                UPDATE missions
                SET status = ?, completed_at = ?
                WHERE id = ? AND status = 'ACTIVE'
                """;
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, mission.status().name());
                    statement.setString(2, completedAt.toString());
                    statement.setString(3, mission.id());
                    changed = statement.executeUpdate();
                }
                if (changed != 1) {
                    throw new SQLException("The active mission record was not found: " + mission.id());
                }
                operationalEvents.insert(connection, completedAt, "MISSION COMPLETE",
                        mission.zoneId(), message);
                connection.commit();
            } catch (SQLException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
    }

    public List<DatabaseHistoryEntry> findRecent(int limit) throws SQLException {
        SensorReadingRepository.validateLimit(limit);
        String sql = """
                SELECT id, mission_type, zone, status, created_at, completed_at
                FROM missions
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """;
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet results = statement.executeQuery()) {
                List<DatabaseHistoryEntry> entries = new ArrayList<>();
                while (results.next()) {
                    String completedAt = results.getString("completed_at");
                    String details = results.getString("mission_type") + " · "
                            + results.getString("status")
                            + (completedAt == null ? "" : " · completed " + completedAt);
                    entries.add(new DatabaseHistoryEntry(
                            Instant.parse(results.getString("created_at")),
                            "MISSION " + results.getString("status"),
                            results.getString("zone"),
                            results.getString("id") + " · " + details));
                }
                return List.copyOf(entries);
            }
        }
    }

    private static void rollback(Connection connection, SQLException original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }
}
