package com.basinwatch.io;

import java.io.Serial;
import java.io.Serializable;

final class SavedMission implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    String id;
    String type;
    String zoneId;
    int progressTicks;
    int durationTicks;
    String status;
    long startedAtTick;

    SavedMission() {
    }

    SavedMission(String id, String type, String zoneId, int progressTicks,
                 int durationTicks, String status, long startedAtTick) {
        this.id = id;
        this.type = type;
        this.zoneId = zoneId;
        this.progressTicks = progressTicks;
        this.durationTicks = durationTicks;
        this.status = status;
        this.startedAtTick = startedAtTick;
    }
}
