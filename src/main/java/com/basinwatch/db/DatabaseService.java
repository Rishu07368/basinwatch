package com.basinwatch.db;

import com.basinwatch.domain.MissionSnapshot;
import com.basinwatch.domain.ResourceType;
import com.basinwatch.domain.SensorReading;
import com.basinwatch.domain.ZoneSnapshot;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class DatabaseService implements AutoCloseable {
    private static final int QUEUE_CAPACITY = 2_048;
    private static final int HISTORY_LIMIT = 200;
    private static final Duration SENSOR_RETENTION = Duration.ofDays(30);
    private static final long RETRY_DELAY_MILLIS = 10_000;

    private final Path databasePath;
    private final DatabaseManager manager;
    private final SensorReadingRepository sensors;
    private final ResourceUsageRepository resourceUsage;
    private final OperationalEventRepository operationalEvents;
    private final MissionRepository missions;
    private final ThreadPoolExecutor executor;
    private final Consumer<String> failureReporter;
    private final Consumer<String> statusListener;
    private volatile boolean initialized;
    private volatile boolean closed;
    private volatile long lastInitializationAttempt;
    private volatile String status = "Connecting to local history database…";

    public DatabaseService(Path databasePath, Consumer<String> failureReporter,
                          Consumer<String> statusListener) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
        this.manager = new DatabaseManager(this.databasePath);
        this.sensors = new SensorReadingRepository(manager);
        this.resourceUsage = new ResourceUsageRepository(manager);
        this.operationalEvents = new OperationalEventRepository(manager);
        this.missions = new MissionRepository(manager, resourceUsage, operationalEvents);
        this.failureReporter = failureReporter;
        this.statusListener = statusListener;
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "basin-database");
            thread.setDaemon(true);
            return thread;
        };
        this.executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
        enqueue(this::initializeAndPrune);
    }

    public Path databasePath() {
        return databasePath;
    }

    public String status() {
        return status;
    }

    public int queuedOperations() {
        return executor.getQueue().size();
    }

    public void recordSensor(SensorReading reading, ZoneSnapshot zone, Instant timestamp,
                             boolean riskEscalated) {
        enqueue(() -> {
            sensors.insert(reading, zone, timestamp);
            if (riskEscalated) {
                operationalEvents.insert(timestamp, "RISK ESCALATION", zone.id(),
                        "Risk increased to " + zone.risk().name()
                                + " at " + zone.name());
            }
        });
    }

    public void recordDispatch(MissionSnapshot mission, Instant timestamp, String message) {
        Map<ResourceType, Integer> requestedResources =
                new EnumMap<>(mission.type().requirements());
        enqueue(() -> missions.recordDispatch(mission, timestamp, requestedResources, message));
    }

    public void recordCompletion(MissionSnapshot mission, Instant timestamp, String message) {
        enqueue(() -> missions.updateStatus(mission, timestamp, message));
    }

    public CompletableFuture<List<DatabaseHistoryEntry>> loadHistory(DatabaseHistoryType type) {
        if (type == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Choose a database history category."));
        }
        return query(() -> switch (type) {
            case SENSORS -> sensors.findRecent(HISTORY_LIMIT);
            case MISSIONS -> missions.findRecent(HISTORY_LIMIT);
            case EVENTS -> operationalEvents.findRecent(HISTORY_LIMIT);
            case RESOURCES -> resourceUsage.findRecent(HISTORY_LIMIT);
        });
    }

    public CompletableFuture<Integer> pruneSensorHistory(Instant olderThan) {
        return query(() -> sensors.deleteBefore(olderThan));
    }

    public void awaitReady(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (!status.startsWith("Connecting")) {
                return;
            }
            Thread.sleep(5);
        }
        throw new IllegalStateException("Timed out initializing the BasinWatch database.");
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    throw new IOException("Database writer did not stop within its shutdown time limit.");
                }
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while stopping the database writer.", ex);
        }
    }

    private void initializeAndPrune() {
        try {
            initialize();
            int removed = sensors.deleteBefore(Instant.now().minus(SENSOR_RETENTION));
            if (removed > 0) {
                updateStatus("SQLite history ready · pruned " + removed + " old sensor readings");
            }
        } catch (SQLException ex) {
            initialized = false;
            databaseFailed("SQLite initialization or sensor-history cleanup failed", ex);
        }
    }

    private boolean ensureInitialized() {
        if (initialized) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastInitializationAttempt < RETRY_DELAY_MILLIS) {
            return false;
        }
        try {
            initialize();
            return true;
        } catch (SQLException ex) {
            databaseFailed("SQLite remains unavailable", ex);
            return false;
        }
    }

    private void initialize() throws SQLException {
        lastInitializationAttempt = System.currentTimeMillis();
        DatabaseInitializer.initialize(manager);
        initialized = true;
        updateStatus("SQLite history ready · " + databasePath.getFileName());
    }

    private void enqueue(SqlAction action) {
        if (closed) {
            reportQueueFailure("A database write was requested after the database writer closed.");
            return;
        }
        try {
            executor.execute(() -> {
                if (!ensureInitialized()) {
                    return;
                }
                try {
                    action.run();
                } catch (SQLException ex) {
                    initialized = false;
                    databaseFailed("SQLite write failed; the simulation will continue", ex);
                }
            });
        } catch (RejectedExecutionException ex) {
            reportQueueFailure("SQLite history queue is full; this database update was not accepted.");
        }
    }

    private <T> CompletableFuture<T> query(SqlSupplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (closed) {
            result.completeExceptionally(new SQLException("The BasinWatch database writer is closed."));
            return result;
        }
        try {
            executor.execute(() -> {
                if (!ensureInitialized()) {
                    result.completeExceptionally(
                            new SQLException("SQLite history is unavailable. " + status));
                    return;
                }
                try {
                    result.complete(action.run());
                } catch (SQLException ex) {
                    initialized = false;
                    databaseFailed("SQLite history query failed", ex);
                    result.completeExceptionally(ex);
                } catch (RuntimeException ex) {
                    result.completeExceptionally(ex);
                }
            });
        } catch (RejectedExecutionException ex) {
            result.completeExceptionally(
                    new SQLException("SQLite history queue is full; try refreshing again.", ex));
        }
        return result;
    }

    private void databaseFailed(String message, SQLException error) {
        String detail = message + ": " + error.getMessage();
        updateStatus("Database unavailable · " + error.getMessage());
        failureReporter.accept(detail);
    }

    private void reportQueueFailure(String message) {
        updateStatus(message);
        failureReporter.accept(message);
    }

    private void updateStatus(String next) {
        status = next;
        statusListener.accept(next);
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T run() throws SQLException;
    }
}
