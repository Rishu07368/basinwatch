package com.basinwatch.engine;

public final class SimulationSettings {
    private volatile int intervalMillis = 1_500;
    private volatile double stormIntensity = 1.0;
    private volatile boolean lowPriorityArchive = true;

    public int intervalMillis() {
        return intervalMillis;
    }

    public void setIntervalMillis(int intervalMillis) {
        if (intervalMillis < 350 || intervalMillis > 8_000) {
            throw new IllegalArgumentException("Simulation interval must be 350–8000 ms.");
        }
        this.intervalMillis = intervalMillis;
    }

    public double stormIntensity() {
        return stormIntensity;
    }

    public void setStormIntensity(double stormIntensity) {
        if (!Double.isFinite(stormIntensity) || stormIntensity < 0.2 || stormIntensity > 2.5) {
            throw new IllegalArgumentException("Storm intensity must be between 0.2 and 2.5.");
        }
        this.stormIntensity = stormIntensity;
    }

    public boolean lowPriorityArchive() {
        return lowPriorityArchive;
    }

    public void setLowPriorityArchive(boolean lowPriorityArchive) {
        this.lowPriorityArchive = lowPriorityArchive;
    }
}
