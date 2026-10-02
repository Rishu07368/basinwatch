package com.basinwatch.io;

import java.io.Serial;
import java.io.Serializable;

final class SavedZone implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    String id;
    String name;
    int column;
    int row;
    double waterLevel;
    double lastRain;
    int exposedResidents;
    boolean leveeReinforced;
    long lastReadingTick;
    String risk;

    SavedZone() {
    }

    SavedZone(String id, String name, int column, int row, double waterLevel, double lastRain,
              int exposedResidents, boolean leveeReinforced, long lastReadingTick, String risk) {
        this.id = id;
        this.name = name;
        this.column = column;
        this.row = row;
        this.waterLevel = waterLevel;
        this.lastRain = lastRain;
        this.exposedResidents = exposedResidents;
        this.leveeReinforced = leveeReinforced;
        this.lastReadingTick = lastReadingTick;
        this.risk = risk;
    }
}
