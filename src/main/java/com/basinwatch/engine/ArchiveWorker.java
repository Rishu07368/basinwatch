package com.basinwatch.engine;

import com.basinwatch.domain.SensorReading;
import com.basinwatch.io.SensorArchive;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

public final class ArchiveWorker extends Thread {
    private static final int CAPACITY = 512;
    private final BlockingQueue<ArchiveCommand> queue = new ArrayBlockingQueue<>(CAPACITY);
    private final SensorArchive archive;
    private final Consumer<String> failureReporter;
    private volatile boolean accepting = true;

    public ArchiveWorker(SensorArchive archive, boolean lowPriority,
                         Consumer<String> failureReporter) {
        super("basin-sensor-archive");
        this.archive = archive;
        this.failureReporter = failureReporter;
        setPriority(lowPriority ? Thread.MIN_PRIORITY : Thread.NORM_PRIORITY);
    }

    public void submit(SensorReading reading) throws InterruptedException {
        if (!accepting || !isAlive()) {
            throw new IllegalStateException("Sensor archive is closing.");
        }
        queue.put(new ArchiveCommand(reading, false));
    }

    public int pendingRecords() {
        return queue.size();
    }

    public void finish() throws InterruptedException {
        if (!accepting) {
            return;
        }
        ArchiveCommand end = new ArchiveCommand(null, true);
        while (!queue.offer(end, 100, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            if (!isAlive()) {
                throw new IllegalStateException("Sensor archive stopped before its queue was drained.");
            }
        }
        accepting = false;
    }

    @Override
    public void run() {
        try {
            while (true) {
                ArchiveCommand command = queue.take();
                if (command.finish()) {
                    break;
                }
                try {
                    archive.append(command.reading());
                } catch (IOException ex) {
                    failureReporter.accept("Sensor archive write failed: " + ex.getMessage());
                }
            }
        } catch (InterruptedException ex) {
            interrupt();
            failureReporter.accept("Sensor archive worker was interrupted before its queue drained.");
        } finally {
            try {
                archive.close();
            } catch (IOException ex) {
                failureReporter.accept("Could not close sensor archive: " + ex.getMessage());
            }
        }
    }

    private record ArchiveCommand(SensorReading reading, boolean finish) {
    }
}
