package com.basinwatch.engine;

import com.basinwatch.domain.ActivityEntry;
import com.basinwatch.domain.BasinEvent;
import com.basinwatch.domain.BasinModel;
import com.basinwatch.domain.BasinSnapshot;
import com.basinwatch.domain.DispatchRequest;
import com.basinwatch.domain.MissionType;
import com.basinwatch.domain.SensorReading;
import com.basinwatch.domain.SimulationPulse;
import com.basinwatch.io.AppPaths;
import com.basinwatch.io.OperationalJournal;
import com.basinwatch.io.ReportWriter;
import com.basinwatch.io.SensorArchive;
import com.basinwatch.io.SnapshotStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class BasinEngine implements AutoCloseable {
    private static final int WORKER_COUNT = 3;
    private static final long JOIN_TIMEOUT_MILLIS = 8_000;

    private final AppPaths paths;
    private final EventBuffer eventBuffer = new EventBuffer(96);
    private final SimulationSettings settings = new SimulationSettings();
    private final SnapshotStore snapshotStore = new SnapshotStore();
    private final OperationalJournal journal;
    private final AtomicLong tickSequence;
    private final CopyOnWriteArrayList<Consumer<BasinSnapshot>> snapshotListeners =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<String>> statusListeners =
            new CopyOnWriteArrayList<>();
    private final ExecutorService controls;
    private final List<Thread> responseThreads = new ArrayList<>();
    private final AtomicLong processedEvents = new AtomicLong();
    private volatile BasinModel model;
    private volatile SensorFeed sensorFeed;
    private volatile Thread intakeThread;
    private volatile PipedWriter feedWriter;
    private volatile ArchiveWorker archiveWorker;
    private volatile boolean started;
    private volatile boolean closed;
    private volatile String status = "Ready · observations paused";
    private volatile String lastError = "";

    public BasinEngine(AppPaths paths, BasinModel model) throws IOException {
        this.paths = paths;
        this.model = model;
        this.tickSequence = new AtomicLong(model.currentTick());
        this.journal = new OperationalJournal(paths.logs().resolve("operations.log"));
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "basin-control");
            thread.setDaemon(true);
            return thread;
        };
        this.controls = Executors.newSingleThreadExecutor(factory);
        appendJournal("SYSTEM", "BasinWatch opened");
    }

    public SimulationSettings settings() {
        return settings;
    }

    public BasinSnapshot snapshot() {
        return model.snapshot();
    }

    public void addSnapshotListener(Consumer<BasinSnapshot> listener) {
        snapshotListeners.add(listener);
    }

    public void addStatusListener(Consumer<String> listener) {
        statusListeners.add(listener);
    }

    public CompletableFuture<Void> start() {
        return submitControl(() -> {
            ensureOpen();
            if (!started) {
                SensorArchive archive = new SensorArchive(paths.archive().resolve("sensors.bin"));
                archiveWorker = new ArchiveWorker(archive, settings.lowPriorityArchive(),
                        this::reportFailure);
                archiveWorker.start();
                synchronized (responseThreads) {
                    for (int index = 1; index <= WORKER_COUNT; index++) {
                        Thread worker = new Thread(new ResponseWorker(),
                                "basin-response-" + index);
                        responseThreads.add(worker);
                        worker.start();
                    }
                }
                started = true;
            }
            startFeed();
        });
    }

    public CompletableFuture<Void> pause() {
        return submitControl(() -> {
            if (!closed) {
                stopFeed();
                setStatus("Observations paused · active missions remain assigned");
            }
        });
    }

    public boolean dispatch(String zoneId, MissionType missionType) {
        ensureOpen();
        return eventBuffer.offerAll(List.of(
                new DispatchRequest(model.currentTick(), zoneId, missionType)));
    }

    public boolean injectStorm(String zoneId) {
        ensureOpen();
        long tick = tickSequence.incrementAndGet();
        SensorReading reading = new SensorReading(tick, "OPERATOR-STORM-" + zoneId,
                zoneId, 46.0, 11.0);
        return eventBuffer.offerAll(List.of(reading));
    }

    public boolean step() {
        ensureOpen();
        long tick = tickSequence.incrementAndGet();
        List<BasinEvent> events = new ArrayList<>();
        String[] zones = {"UPR", "MIL", "NOR", "OLD", "MAR", "SOU"};
        for (int index = 0; index < zones.length; index++) {
            double rainfall = (4.0 + index * 2.3) * settings.stormIntensity();
            events.add(new SensorReading(tick, "MANUAL-" + zones[index], zones[index],
                    rainfall, 0.7 + index * 0.18));
        }
        events.add(new SimulationPulse(tick));
        return eventBuffer.offerAll(events);
    }

    public CompletableFuture<Path> exportReport() {
        return supplyControl(() -> ReportWriter.write(paths.reports(), model.snapshot()));
    }

    public CompletableFuture<Void> save(Path path) {
        return submitControl(() -> {
            ensureOpen();
            boolean resumeFeed = sensorFeed != null && sensorFeed.isAlive();
            if (resumeFeed) {
                setStatus("Pausing new observations while accepted events finish…");
                stopFeed();
            }
            try {
                awaitQuiescent();
                snapshotStore.save(path, model.snapshot());
                model.recordSystemEvent("SAVE", "Session saved · " + path.getFileName());
                appendJournal("SAVE", "Session saved · " + path.toAbsolutePath());
                publishSnapshot();
                setStatus("Session saved · " + path.getFileName());
            } finally {
                if (resumeFeed && !closed) {
                    startFeed();
                }
            }
        });
    }

    public CompletableFuture<Void> load(Path path) {
        return submitControl(() -> {
            ensureOpen();
            stopFeed();
            awaitQuiescent();
            BasinSnapshot restored = snapshotStore.load(path);
            model = BasinModel.restore(restored);
            tickSequence.set(restored.tick());
            model.recordSystemEvent("RESTORE", "Session restored · " + path.getFileName());
            appendJournal("RESTORE", "Session restored · " + path.toAbsolutePath());
            publishSnapshot();
            setStatus("Session restored · " + path.getFileName());
        });
    }

    public EngineDiagnostics diagnostics() {
        List<ThreadDiagnostic> threads = new ArrayList<>();
        SensorFeed feed = sensorFeed;
        Thread intake = intakeThread;
        if (feed != null) addDiagnostic(threads, feed);
        if (intake != null) addDiagnostic(threads, intake);
        synchronized (responseThreads) {
            responseThreads.forEach(thread -> addDiagnostic(threads, thread));
        }
        ArchiveWorker archive = archiveWorker;
        if (archive != null) addDiagnostic(threads, archive);
        return new EngineDiagnostics(eventBuffer.size(), eventBuffer.capacity(),
                eventBuffer.waitingConsumers(), eventBuffer.waitingProducers(),
                eventBuffer.activeConsumers(),
                archive == null ? 0 : archive.pendingRecords(), processedEvents.get(),
                status, threads);
    }

    public String lastError() {
        return lastError;
    }

    public CompletableFuture<Void> setArchivePriority(boolean lowPriority) {
        return submitControl(() -> {
            settings.setLowPriorityArchive(lowPriority);
            ArchiveWorker worker = archiveWorker;
            if (worker != null && worker.isAlive()) {
                worker.setPriority(lowPriority ? Thread.MIN_PRIORITY : Thread.NORM_PRIORITY);
            }
            setStatus("Archive worker priority set to "
                    + (lowPriority ? "low (best-effort hint)" : "normal"));
        });
    }

    public CompletableFuture<Void> closeAsync() {
        if (closed) {
            return CompletableFuture.completedFuture(null);
        }
        return submitControl(this::closeEngine);
    }

    @Override
    public void close() throws IOException {
        try {
            closeAsync().get(JOIN_TIMEOUT_MILLIS * 4, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException ex) {
            throw new IOException("BasinWatch could not close cleanly.", ex.getCause());
        } catch (java.util.concurrent.TimeoutException ex) {
            throw new IOException("BasinWatch shutdown exceeded its time limit.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while closing BasinWatch.", ex);
        }
    }

    private void startFeed() throws IOException {
        if (sensorFeed != null && sensorFeed.isAlive()) {
            return;
        }
        PipedWriter writer = new PipedWriter();
        PipedReader reader;
        try {
            reader = new PipedReader(writer, 8_192);
        } catch (IOException ex) {
            writer.close();
            throw ex;
        }
        feedWriter = writer;
        intakeThread = new Thread(new SensorIntake(
                new BufferedReader(reader), eventBuffer, this::reportFailure,
                ignored -> publishDiagnosticsStatus()), "basin-sensor-intake");
        sensorFeed = new SensorFeed(writer, List.of("UPR", "MIL", "NOR", "OLD", "MAR", "SOU"),
                settings, tickSequence, this::reportFailure);
        intakeThread.start();
        sensorFeed.start();
        setStatus("Live observations · " + settings.intervalMillis() + " ms interval");
        model.recordSystemEvent("SYSTEM", "Sensor network connected");
        appendJournal("SYSTEM", "Sensor network connected");
        publishSnapshot();
    }

    private void stopFeed() throws IOException {
        SensorFeed feed = sensorFeed;
        Thread intake = intakeThread;
        if (feed == null && intake == null) {
            return;
        }
        if (feed != null && feed.isAlive()) {
            try {
                feed.requestStop();
            } catch (IOException ex) {
                reportFailure("Could not close the sensor feed cleanly: " + ex.getMessage());
            }
        }
        try {
            if (feed != null && feed.isAlive()) {
                join(feed, "sensor feed");
            }
            if (intake != null) {
                join(intake, "sensor intake");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while pausing the sensor network.", ex);
        }
        if ((feed != null && feed.isAlive()) || (intake != null && intake.isAlive())) {
            throw new IOException("Sensor threads did not stop; the session remains open.");
        }
        sensorFeed = null;
        intakeThread = null;
        feedWriter = null;
        model.recordSystemEvent("SYSTEM", "Sensor network paused");
        appendJournal("SYSTEM", "Sensor network paused");
        publishSnapshot();
    }

    private void closeEngine() throws IOException {
        if (closed) {
            return;
        }
        IOException failure = null;
        try {
            stopFeed();
        } catch (IOException ex) {
            failure = ex;
        }
        eventBuffer.close();
        List<Thread> workers;
        synchronized (responseThreads) {
            workers = List.copyOf(responseThreads);
        }
        for (Thread worker : workers) {
            try {
                join(worker, "response worker");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                failure = combine(failure,
                        new IOException("Interrupted while stopping response workers.", ex));
                break;
            } catch (IOException ex) {
                failure = combine(failure, ex);
            }
        }
        if (failure != null || workers.stream().anyMatch(Thread::isAlive)
                || (sensorFeed != null && sensorFeed.isAlive())
                || (intakeThread != null && intakeThread.isAlive())) {
            if (failure == null) {
                failure = new IOException("Background work is still active; operational files remain open.");
            }
            throw failure;
        }
        synchronized (responseThreads) {
            responseThreads.clear();
        }
        ArchiveWorker archive = archiveWorker;
        if (archive != null && archive.isAlive()) {
            try {
                archive.finish();
                join(archive, "sensor archive");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                failure = combine(failure, new IOException("Interrupted while flushing sensor archive.", ex));
            } catch (IOException ex) {
                failure = combine(failure, ex);
            } catch (IllegalStateException ex) {
                failure = combine(failure, new IOException(ex.getMessage(), ex));
            }
        }
        if (failure != null || (archive != null && archive.isAlive())) {
            if (failure == null) {
                failure = new IOException("Sensor archive is still active; operational files remain open.");
            }
            throw failure;
        }
        if (failure == null) {
            try {
                snapshotStore.save(paths.saves().resolve("last-session.bws"), model.snapshot());
                model.recordSystemEvent("SAVE", "Last session saved automatically");
                appendJournal("SAVE", "Last session saved automatically");
            } catch (IOException ex) {
                failure = ex;
            }
        }
        journal.close();
        closed = true;
        setStatus("Session closed cleanly");
        controls.shutdown();
    }

    private void awaitQuiescent() throws IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (eventBuffer.size() > 0 || eventBuffer.activeConsumers() > 0) {
            if (System.nanoTime() >= deadline) {
                throw new IOException("Pending basin work did not finish; the prior state was kept.");
            }
            try {
                Thread.sleep(15);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for the current basin events.", ex);
            }
        }
    }

    private void join(Thread thread, String description) throws IOException, InterruptedException {
        thread.join(JOIN_TIMEOUT_MILLIS);
        if (thread.isAlive()) {
            throw new IOException("Timed out waiting for " + description + " to stop.");
        }
    }

    private void process(BasinEvent event) throws IOException, InterruptedException {
        List<String> messages = model.process(event);
        IOException journalFailure = null;
        for (String message : messages) {
            ActivityEntry entry = new ActivityEntry(Instant.now(), event.category(), message);
            try {
                journal.append(entry);
            } catch (IOException ex) {
                if (journalFailure == null) {
                    journalFailure = ex;
                } else {
                    journalFailure.addSuppressed(ex);
                }
            }
        }
        if (event instanceof SensorReading reading && archiveWorker != null) {
            archiveWorker.submit(reading);
        }
        processedEvents.incrementAndGet();
        publishSnapshot();
        if (journalFailure != null) {
            throw journalFailure;
        }
    }

    private void publishSnapshot() {
        BasinSnapshot current = model.snapshot();
        snapshotListeners.forEach(listener -> listener.accept(current));
    }

    private void publishDiagnosticsStatus() {
        statusListeners.forEach(listener -> listener.accept(status));
    }

    private void appendJournal(String category, String message) {
        try {
            journal.append(new ActivityEntry(Instant.now(), category, message));
        } catch (IOException ex) {
            reportFailure("Operational log write failed: " + ex.getMessage());
        }
    }

    private void reportFailure(String message) {
        lastError = message;
        status = "Attention · " + message;
        statusListeners.forEach(listener -> listener.accept(status));
    }

    private void setStatus(String message) {
        status = message;
        statusListeners.forEach(listener -> listener.accept(message));
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("BasinWatch is already closed.");
        }
    }

    private CompletableFuture<Void> submitControl(IoAction action) {
        return CompletableFuture.runAsync(() -> {
            try {
                action.run();
            } catch (IOException ex) {
                reportFailure(ex.getMessage());
                throw new java.util.concurrent.CompletionException(ex);
            }
        }, controls);
    }

    private <T> CompletableFuture<T> supplyControl(IoSupplier<T> action) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ensureOpen();
                return action.get();
            } catch (IOException ex) {
                reportFailure(ex.getMessage());
                throw new java.util.concurrent.CompletionException(ex);
            }
        }, controls);
    }

    private static IOException combine(IOException prior, IOException next) {
        if (prior == null) {
            return next;
        }
        prior.addSuppressed(next);
        return prior;
    }

    private static void addDiagnostic(List<ThreadDiagnostic> target, Thread thread) {
        target.add(new ThreadDiagnostic(thread.getName(), thread.getState().name(),
                thread.getPriority(), thread.isAlive()));
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }

    private final class ResponseWorker implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                BasinEvent event;
                try {
                    event = eventBuffer.take();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (event == null) {
                    return;
                }
                try {
                    process(event);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (IOException | RuntimeException ex) {
                    reportFailure("Event " + event.category() + " could not be fully processed: "
                            + ex.getClass().getSimpleName() + " · " + ex.getMessage());
                    model.recordSystemEvent("PROCESSING WARNING", event.describe());
                    publishSnapshot();
                } finally {
                    eventBuffer.complete();
                }
            }
        }
    }
}
