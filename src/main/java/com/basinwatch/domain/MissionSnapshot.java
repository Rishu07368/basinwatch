package com.basinwatch.domain;

public record MissionSnapshot(
        String id,
        MissionType type,
        String zoneId,
        int progressTicks,
        int durationTicks,
        MissionStatus status,
        long startedAtTick) {
    public int progressPercent() {
        return Math.min(100, progressTicks * 100 / durationTicks);
    }
}
