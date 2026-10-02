package com.basinwatch.domain;

import java.util.Locale;

final class BasinZone {
    private final String id;
    private final String name;
    private final int column;
    private final int row;
    private final int initialResidents;
    private double waterLevelMeters;
    private double lastRainMillimeters;
    private int exposedResidents;
    private boolean leveeReinforced;
    private long lastReadingTick = -1;
    private RiskLevel risk;

    BasinZone(String id, String name, int column, int row, double initialWater,
              int initialResidents) {
        this.id = id;
        this.name = name;
        this.column = column;
        this.row = row;
        this.waterLevelMeters = initialWater;
        this.initialResidents = initialResidents;
        this.exposedResidents = initialResidents;
        this.risk = assessRisk(initialWater);
    }

    boolean apply(SensorReading reading) {
        if (reading.tick() <= lastReadingTick) {
            return false;
        }
        lastReadingTick = reading.tick();
        lastRainMillimeters = reading.rainfallMillimeters();
        double runoffFactor = leveeReinforced ? 0.58 : 1.0;
        double rainfallContribution = reading.rainfallMillimeters() * 0.0014;
        double observedRise = reading.observedRiseCentimeters() / 100.0;
        waterLevelMeters = Math.min(8.0,
                Math.max(0.0, waterLevelMeters + (rainfallContribution + observedRise) * runoffFactor));
        risk = assessRisk(waterLevelMeters);
        return true;
    }

    void reinforceLevee() {
        leveeReinforced = true;
    }

    void pump(double amountMeters) {
        waterLevelMeters = Math.max(0.0, waterLevelMeters - amountMeters);
        risk = assessRisk(waterLevelMeters);
    }

    void evacuate() {
        exposedResidents = Math.max(0, (int) Math.ceil(exposedResidents * 0.68));
    }

    ZoneSnapshot snapshot() {
        return new ZoneSnapshot(id, name, column, row, waterLevelMeters, lastRainMillimeters,
                exposedResidents, leveeReinforced, lastReadingTick, risk);
    }

    static BasinZone restore(ZoneSnapshot saved) {
        if (saved.id() == null || saved.id().isBlank() || saved.name() == null
                || saved.column() < 0 || saved.column() > 2 || saved.row() < 0 || saved.row() > 1
                || !Double.isFinite(saved.waterLevelMeters()) || saved.waterLevelMeters() < 0
                || saved.waterLevelMeters() > 8
                || !Double.isFinite(saved.lastRainMillimeters()) || saved.lastRainMillimeters() < 0
                || saved.exposedResidents() < 0 || saved.lastReadingTick() < -1
                || saved.risk() == null || saved.risk() != assessRisk(saved.waterLevelMeters())) {
            throw new IllegalArgumentException("Saved zone state is invalid.");
        }
        BasinZone zone = new BasinZone(saved.id(), saved.name(), saved.column(), saved.row(),
                saved.waterLevelMeters(), saved.exposedResidents());
        zone.waterLevelMeters = saved.waterLevelMeters();
        zone.lastRainMillimeters = saved.lastRainMillimeters();
        zone.exposedResidents = saved.exposedResidents();
        zone.leveeReinforced = saved.leveeReinforced();
        zone.lastReadingTick = saved.lastReadingTick();
        zone.risk = saved.risk();
        return zone;
    }

    static RiskLevel assessRisk(double waterLevelMeters) {
        if (waterLevelMeters >= 3.6) return RiskLevel.CRITICAL;
        if (waterLevelMeters >= 2.6) return RiskLevel.WARNING;
        if (waterLevelMeters >= 1.7) return RiskLevel.WATCH;
        return RiskLevel.NORMAL;
    }

    String id() {
        return id;
    }

    String name() {
        return name;
    }

    String compactStatus() {
        return String.format(Locale.ROOT, "%s · %.2f m · %s",
                name, waterLevelMeters, risk.label());
    }
}
