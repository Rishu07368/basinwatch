package com.basinwatch;

import com.basinwatch.domain.BasinModel;
import com.basinwatch.domain.BasinSnapshot;
import com.basinwatch.domain.DispatchRequest;
import com.basinwatch.domain.MissionStatus;
import com.basinwatch.domain.MissionType;
import com.basinwatch.domain.ResourceAllocator;
import com.basinwatch.domain.ResourceType;
import com.basinwatch.domain.SensorReading;
import com.basinwatch.domain.SimulationPulse;
import com.basinwatch.engine.BasinEngine;
import com.basinwatch.engine.EventBuffer;
import com.basinwatch.io.AppPaths;
import com.basinwatch.io.SaveCatalog;
import com.basinwatch.io.SensorArchive;
import com.basinwatch.io.SnapshotStore;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class InvariantSuite {
    private int assertions;

    private InvariantSuite() {
    }

    public static void main(String[] arguments) throws Exception {
        new InvariantSuite().run();
    }

    private void run() throws Exception {
        parsesAndValidatesSensorEvents();
        reservesSharedResourcesAtomically();
        coordinatesBoundedEventBuffer();
        advancesAndReleasesResponseMissions();
        savesRestoresAndRejectsUnsafeSnapshots();
        validatesArchiveHeaders();
        resolvesPerUserDataLocations();
        runsACompleteOfflineEngineSession();
        System.out.println("BasinWatch invariant suite passed (" + assertions + " assertions).");
    }

    private void parsesAndValidatesSensorEvents() {
        SensorReading original = new SensorReading(7, "G-MIL", "MIL", 12.5, 1.25);
        SensorReading restored = SensorReading.parse(original.encode());
        check(original.equals(restored), "Sensor wire records round-trip.");
        expect(IllegalArgumentException.class,
                () -> SensorReading.parse("READ|x|G-MIL|MIL|rain|rise"));
        expect(IllegalArgumentException.class,
                () -> new SensorReading(-1, "G-MIL", "MIL", 1, 1));
        expect(IllegalArgumentException.class,
                () -> new SensorReading(1, "G|MIL", "MIL", 1, 1));
    }

    private void reservesSharedResourcesAtomically() throws Exception {
        ResourceAllocator allocator = new ResourceAllocator(Map.of(
                ResourceType.PUMP, 1,
                ResourceType.FIELD_CREW, 1));
        Map<ResourceType, Integer> request = Map.of(
                ResourceType.PUMP, 1,
                ResourceType.FIELD_CREW, 1);
        int contenders = 24;
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(contenders);
        AtomicInteger accepted = new AtomicInteger();
        try {
            for (int index = 0; index < contenders; index++) {
                pool.execute(() -> {
                    ready.countDown();
                    try {
                        if (!start.await(3, TimeUnit.SECONDS)) {
                            throw new AssertionError("Reservation test start timed out.");
                        }
                        if (allocator.tryReserve(request)) {
                            accepted.incrementAndGet();
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("Reservation test was interrupted.", ex);
                    } finally {
                        finished.countDown();
                    }
                });
            }
            check(ready.await(3, TimeUnit.SECONDS), "All resource contenders reach the start gate.");
            start.countDown();
            check(finished.await(5, TimeUnit.SECONDS), "Concurrent resource reservations finish.");
        } finally {
            pool.shutdownNow();
            check(pool.awaitTermination(3, TimeUnit.SECONDS), "Resource test workers terminate.");
        }
        check(accepted.get() == 1, "Only one operation reserves the final shared pump and crew.");
        check(allocator.snapshot().get(ResourceType.PUMP) == 0,
                "The reserved pump is unavailable until release.");
        allocator.release(request);
        check(allocator.snapshot().get(ResourceType.PUMP) == 1,
                "Released pump returns to stock.");
        expect(IllegalStateException.class, () -> allocator.release(request));
        check(allocator.snapshot().get(ResourceType.FIELD_CREW) == 1,
                "Rejected duplicate release does not corrupt crew inventory.");
    }

    private void coordinatesBoundedEventBuffer() throws Exception {
        EventBuffer buffer = new EventBuffer(1);
        CountDownLatch consumerStarted = new CountDownLatch(1);
        AtomicInteger consumed = new AtomicInteger();
        Thread consumer = new Thread(() -> {
            consumerStarted.countDown();
            try {
                if (buffer.take() != null) {
                    consumed.incrementAndGet();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (consumed.get() > 0) {
                    buffer.complete();
                }
            }
        }, "test-event-consumer");
        consumer.start();
        check(consumerStarted.await(1, TimeUnit.SECONDS), "Consumer starts.");
        await(() -> buffer.waitingConsumers() == 1, Duration.ofSeconds(2),
                "An empty consumer waits on the queue condition.");
        check(buffer.put(new SensorReading(1, "G-UPR", "UPR", 5, 1)),
                "Producer wakes a consumer with a real sensor event.");
        consumer.join(2_000);
        check(!consumer.isAlive() && consumed.get() == 1,
                "Consumer receives the queued sensor event.");

        buffer.put(new SimulationPulse(2));
        CountDownLatch producerStarted = new CountDownLatch(1);
        AtomicInteger producerResult = new AtomicInteger();
        Thread producer = new Thread(() -> {
            producerStarted.countDown();
            try {
                if (buffer.put(new SimulationPulse(3))) {
                    producerResult.set(1);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "test-event-producer");
        producer.start();
        check(producerStarted.await(1, TimeUnit.SECONDS), "Producer starts.");
        await(() -> buffer.waitingProducers() == 1, Duration.ofSeconds(2),
                "A full bounded queue applies producer back-pressure.");
        check(buffer.take() instanceof SimulationPulse, "The earlier queued item remains first.");
        buffer.complete();
        producer.join(2_000);
        check(!producer.isAlive() && producerResult.get() == 1,
                "Removing an item releases the waiting producer.");
        check(buffer.take() instanceof SimulationPulse, "The waiting producer's item is delivered.");
        buffer.complete();

        CountDownLatch finalWaiterStarted = new CountDownLatch(1);
        AtomicInteger endOfQueue = new AtomicInteger();
        Thread waiter = new Thread(() -> {
            finalWaiterStarted.countDown();
            try {
                if (buffer.take() == null) {
                    endOfQueue.incrementAndGet();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "test-closing-consumer");
        waiter.start();
        check(finalWaiterStarted.await(1, TimeUnit.SECONDS), "Closing consumer starts.");
        await(() -> buffer.waitingConsumers() == 1, Duration.ofSeconds(2),
                "Consumer waits while no events are available.");
        buffer.close();
        waiter.join(2_000);
        check(!waiter.isAlive() && endOfQueue.get() == 1,
                "Closing the queue wakes waiters with an explicit end-of-stream result.");
    }

    private void advancesAndReleasesResponseMissions() {
        BasinModel model = new BasinModel();
        double initialLevel = model.snapshot().zone("MIL").waterLevelMeters();
        model.process(new DispatchRequest(0, "MIL", MissionType.PUMP_DEPLOYMENT));
        check(model.snapshot().availableResources().get(ResourceType.PUMP) == 2,
                "Dispatch reserves a pump immediately.");
        for (int tick = 1; tick <= MissionType.PUMP_DEPLOYMENT.durationTicks(); tick++) {
            model.process(new SimulationPulse(tick));
        }
        BasinSnapshot completed = model.snapshot();
        check(completed.zone("MIL").waterLevelMeters() < initialLevel,
                "A completed pump mission measurably lowers its zone's water.");
        check(completed.availableResources().get(ResourceType.PUMP) == 3,
                "Completed operations release their equipment.");
        check(completed.missions().get(0).status() == MissionStatus.COMPLETED,
                "The operation reaches a terminal status.");

        BasinSnapshot beforeLevee = model.snapshot();
        model.process(new DispatchRequest(beforeLevee.tick(), "UPR", MissionType.LEVEE_REINFORCEMENT));
        for (int tick = 9; tick <= 18; tick++) {
            model.process(new SimulationPulse(tick));
        }
        check(model.snapshot().zone("UPR").leveeReinforced(),
                "A completed levee operation changes the zone's protective state.");
        model.process(new SensorReading(19, "TEST-UPR", "UPR", 10, 2));
        model.process(new SensorReading(19, "TEST-MAR", "MAR", 10, 2));
        double protectedRise = model.snapshot().zone("UPR").waterLevelMeters();
        double unprotectedRise = model.snapshot().zone("MAR").waterLevelMeters();
        double protectedBase = beforeLevee.zone("UPR").waterLevelMeters();
        check(protectedRise - protectedBase < 0.05 || protectedRise < unprotectedRise,
                "Levee reinforcement reduces subsequent runoff response.");
    }

    private void savesRestoresAndRejectsUnsafeSnapshots() throws Exception {
        Path directory = Files.createTempDirectory("basinwatch-snapshot-test-");
        try {
            BasinModel model = new BasinModel();
            model.process(new SensorReading(1, "G-MIL", "MIL", 17, 3));
            model.process(new DispatchRequest(1, "OLD", MissionType.EVACUATION));
            Path target = directory.resolve("active.bws");
            SnapshotStore store = new SnapshotStore();
            store.save(target, model.snapshot());
            BasinSnapshot restored = store.load(target);
            check(restored.tick() == model.snapshot().tick(), "Saved simulation tick is restored.");
            check(restored.zone("MIL").waterLevelMeters()
                            == model.snapshot().zone("MIL").waterLevelMeters(),
                    "Saved zone measurements round-trip exactly.");
            check(restored.missions().stream().anyMatch(
                            mission -> mission.status() == MissionStatus.ACTIVE),
                    "Active field operations survive session restore.");
            check(restored.availableResources().equals(model.snapshot().availableResources()),
                    "Reserved and available inventory is restored consistently.");
            BasinModel restoredModel = BasinModel.restore(restored);
            check(restoredModel.snapshot().zones().size() == 6,
                    "Restored state passes domain validation.");
            for (int tick = 2; tick <= 7; tick++) {
                restoredModel.process(new SimulationPulse(tick));
            }
            check(restoredModel.snapshot().availableResources()
                            .get(ResourceType.EVAC_VEHICLE) == 2,
                    "Restored active missions release their original reserved capacity on completion.");

            Path corrupt = directory.resolve("corrupt.bws");
            Files.write(corrupt, new byte[]{0, 1, 2, 3, 4});
            expect(IOException.class, () -> store.load(corrupt));
            Path unexpected = directory.resolve("unexpected.bws");
            try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(unexpected))) {
                output.writeObject(new java.util.ArrayList<>());
            }
            expect(IOException.class, () -> store.load(unexpected));
            check(SaveCatalog.list(directory).size() == 3,
                    "Save catalogue uses local file metadata to find session files.");
        } finally {
            deleteTree(directory);
        }
    }

    private void runsACompleteOfflineEngineSession() throws Exception {
        Path root = Files.createTempDirectory("basinwatch-engine-test-");
        try {
            AppPaths paths = AppPaths.create(root);
            BasinEngine engine = new BasinEngine(paths, new BasinModel());
            engine.settings().setIntervalMillis(350);
            engine.start().get(5, TimeUnit.SECONDS);
            await(() -> engine.diagnostics().processedEvents() >= 7, Duration.ofSeconds(5),
                    "Sensor feed, pipe intake, and response workers process real events.");
            Path liveSave = paths.saves().resolve("live-session.bws");
            engine.save(liveSave).get(8, TimeUnit.SECONDS);
            check(Files.isRegularFile(liveSave), "Live save reaches disk after accepted events drain.");
            check(engine.diagnostics().threads().stream()
                            .anyMatch(thread -> thread.name().equals("basin-sensor-feed") && thread.alive()),
                    "A save made during live observations resumes the feed afterwards.");
            engine.pause().get(8, TimeUnit.SECONDS);
            await(() -> engine.diagnostics().queuedEvents() == 0
                            && engine.diagnostics().activeEvents() == 0,
                    Duration.ofSeconds(5), "Paused event pipeline drains accepted observations.");
            check(engine.snapshot().tick() > 0, "Live simulation advances the basin clock.");

            check(engine.dispatch("MIL", MissionType.PUMP_DEPLOYMENT),
                    "Dispatch is accepted into the bounded event queue.");
            await(() -> engine.snapshot().missions().stream()
                            .anyMatch(mission -> mission.status() == MissionStatus.ACTIVE),
                    Duration.ofSeconds(3), "A response worker allocates and starts a requested mission.");
            Path namedSave = paths.saves().resolve("test-session.bws");
            engine.save(namedSave).get(5, TimeUnit.SECONDS);
            long savedTick = engine.snapshot().tick();
            engine.load(namedSave).get(8, TimeUnit.SECONDS);
            check(engine.snapshot().tick() == savedTick, "Engine loads a consistent saved session.");

            Path report = engine.exportReport().get(5, TimeUnit.SECONDS);
            check(Files.size(report) > 100, "Situation report contains readable operational data.");
            engine.closeAsync().get(20, TimeUnit.SECONDS);
            check(Files.isRegularFile(paths.saves().resolve("last-session.bws")),
                    "Clean shutdown automatically saves a resumable session.");
            check(Files.size(paths.logs().resolve("operations.log")) > 0,
                    "Operational events are retained in the text journal.");
            try (DataInputStream archive = new DataInputStream(new BufferedInputStream(
                    Files.newInputStream(paths.archive().resolve("sensors.bin"))))) {
                check(archive.readInt() == 0x42574152, "Binary sensor archive contains its format marker.");
                check(archive.readInt() == 1, "Binary sensor archive records its format version.");
                check(archive.readLong() > 0, "Binary archive contains typed sensor records.");
            }

            check(new SnapshotStore().load(paths.saves().resolve("last-session.bws")).tick()
                            == engine.snapshot().tick(),
                    "Automatically saved state can be restored by the persistence layer.");
        } finally {
            deleteTree(root);
        }
    }

    private void validatesArchiveHeaders() throws Exception {
        Path directory = Files.createTempDirectory("basinwatch-archive-test-");
        try {
            Path valid = directory.resolve("sensors.bin");
            try (SensorArchive archive = new SensorArchive(valid)) {
                archive.append(new SensorReading(1, "G-UPR", "UPR", 7.2, 1.1));
            }

            try (SensorArchive archive = new SensorArchive(valid)) {
                archive.append(new SensorReading(2, "G-UPR", "UPR", 8.4, 1.2));
            }
            Path invalid = directory.resolve("invalid.bin");
            Files.write(invalid, new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
            expect(IOException.class, () -> new SensorArchive(invalid));
        } finally {
            deleteTree(directory);
        }
    }

    private void resolvesPerUserDataLocations() throws Exception {
        Path selected = Files.createTempDirectory("basinwatch-user-data-");
        try {
            check(AppPaths.resolveDataRoot(
                            new String[]{"--data-dir", selected.toString()}).equals(
                            selected.toAbsolutePath().normalize()),
                    "An explicit data directory is normalized and accepted.");
            check(AppPaths.defaultDataRoot().isAbsolute(),
                    "Default application data location is absolute and user-scoped.");
            expect(IllegalArgumentException.class,
                    () -> AppPaths.resolveDataRoot(new String[]{"--data-dir"}));
            expect(IllegalArgumentException.class,
                    () -> AppPaths.resolveDataRoot(new String[]{"--unexpected"}));
        } finally {
            deleteTree(selected);
        }
    }

    private void await(CheckedCondition condition, Duration timeout, String description)
            throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.test()) {
                assertions++;
                return;
            }
            Thread.sleep(8);
        }
        throw new AssertionError("Timed out: " + description);
    }

    private void check(boolean condition, String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    private void expect(Class<? extends Throwable> expected, ThrowingAction action) {
        assertions++;
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                return;
            }
            throw new AssertionError("Expected " + expected.getName() + " but received "
                    + thrown.getClass().getName(), thrown);
        }
        throw new AssertionError("Expected " + expected.getName() + " to be thrown.");
    }

    private static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @FunctionalInterface
    private interface CheckedCondition {
        boolean test() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Throwable;
    }
}
