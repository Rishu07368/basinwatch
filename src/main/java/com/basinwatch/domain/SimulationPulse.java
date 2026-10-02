package com.basinwatch.domain;

public record SimulationPulse(long tick) implements BasinEvent {
    public SimulationPulse {
        if (tick < 0) {
            throw new IllegalArgumentException("Tick cannot be negative.");
        }
    }

    @Override
    public String zoneId() {
        return "";
    }

    @Override
    public String category() {
        return "SYSTEM";
    }

    @Override
    public String describe() {
        return "Simulation interval advanced";
    }
}
