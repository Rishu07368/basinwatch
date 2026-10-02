package com.basinwatch.engine;

import com.basinwatch.domain.BasinEvent;
import com.basinwatch.domain.SensorReading;
import com.basinwatch.domain.SimulationPulse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.function.Consumer;

public final class SensorIntake implements Runnable {
    private final BufferedReader source;
    private final EventBuffer destination;
    private final Consumer<String> warningReporter;
    private final Consumer<String> acceptedReporter;

    public SensorIntake(BufferedReader source, EventBuffer destination,
                        Consumer<String> warningReporter, Consumer<String> acceptedReporter) {
        this.source = source;
        this.destination = destination;
        this.warningReporter = warningReporter;
        this.acceptedReporter = acceptedReporter;
    }

    @Override
    public void run() {
        try (source) {
            String line;
            while ((line = source.readLine()) != null) {
                BasinEvent event;
                try {
                    if (line.startsWith("PULSE|")) {
                        event = new SimulationPulse(Long.parseLong(line.substring(6)));
                    } else {
                        event = SensorReading.parse(line);
                    }
                } catch (IllegalArgumentException ex) {
                    warningReporter.accept("Discarded malformed sensor record: " + ex.getMessage());
                    continue;
                }
                try {
                    if (!destination.put(event)) {
                        return;
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
                acceptedReporter.accept(event.category());
            }
        } catch (IOException ex) {
            warningReporter.accept("Sensor intake ended with an I/O error: " + ex.getMessage());
        }
    }
}
