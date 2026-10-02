package com.basinwatch.domain;

final class ResponseMission {
    private final String id;
    private final MissionType type;
    private final String zoneId;
    private final long startedAtTick;
    private int progressTicks;
    private MissionStatus status;

    ResponseMission(String id, MissionType type, String zoneId, long startedAtTick) {
        this(id, type, zoneId, startedAtTick, 0, MissionStatus.ACTIVE);
    }

    ResponseMission(String id, MissionType type, String zoneId, long startedAtTick,
                    int progressTicks, MissionStatus status) {
        this.id = id;
        this.type = type;
        this.zoneId = zoneId;
        this.startedAtTick = startedAtTick;
        this.progressTicks = progressTicks;
        this.status = status;
    }

    boolean advance() {
        if (status != MissionStatus.ACTIVE) {
            return false;
        }
        progressTicks = Math.min(type.durationTicks(), progressTicks + 1);
        if (progressTicks == type.durationTicks()) {
            status = MissionStatus.COMPLETED;
            return true;
        }
        return false;
    }

    MissionSnapshot snapshot() {
        return new MissionSnapshot(id, type, zoneId, progressTicks, type.durationTicks(),
                status, startedAtTick);
    }
}
