package com.basinwatch.domain;

public record ZoneSnapshot(
        String id,
        String name,
        int column,
        int row,
        double waterLevelMeters,
        double lastRainMillimeters,
        int exposedResidents,
        boolean leveeReinforced,
        long lastReadingTick,
        RiskLevel risk) {
}
