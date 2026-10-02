package com.basinwatch.engine;

import com.basinwatch.domain.SensorReading;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class SensorFeed extends Thread {
    private final Writer destination;
    private final List<String> zoneIds;
    private final SimulationSettings settings;
    private final AtomicLong tickSequence;
    private final Consumer<String> failureReporter;
    private final Random random = new Random(System.nanoTime());
    private volatile boolean running = true;

    public SensorFeed(Writer destination, List<String> zoneIds, SimulationSettings settings,
                      AtomicLong tickSequence, Consumer<String> failureReporter) {
        super("basin-sensor-feed");
        this.destination = destination;
        this.zoneIds = List.copyOf(zoneIds);
        this.settings = settings;
        this.tickSequence = tickSequence;
        this.failureReporter = failureReporter;
        setPriority(Thread.NORM_PRIORITY);
    }

    @Override
    public void run() {
        try {
            while (running && !isInterrupted()) {
                long tick = tickSequence.incrementAndGet();
                for (int index = 0; index < zoneIds.size() && running; index++) {
                    double rainfall = (2.0 + random.nextDouble() * 14.0) * settings.stormIntensity();
                    double rise = (0.3 + random.nextDouble() * 1.6) * settings.stormIntensity();
                    SensorReading reading = new SensorReading(tick, "G-" + zoneIds.get(index),
                            zoneIds.get(index), rainfall, rise);
                    destination.write(reading.encode());
                    destination.write('\n');
                }
                destination.write("PULSE|" + tick + '\n');
                destination.flush();
                Thread.sleep(settings.intervalMillis());
            }
        } catch (InterruptedException ex) {
            interrupt();
        } catch (IOException ex) {
            if (running) {
                failureReporter.accept("Sensor channel stopped unexpectedly: " + ex.getMessage());
            }
        } finally {
            try {
                destination.close();
            } catch (IOException ex) {
                if (running) {
                    failureReporter.accept("Could not close sensor channel: " + ex.getMessage());
                }
            }
        }
    }

    public void requestStop() throws IOException {
        running = false;
        interrupt();
        destination.close();
    }
}
