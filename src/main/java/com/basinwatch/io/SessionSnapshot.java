package com.basinwatch.io;

import com.basinwatch.domain.ActivityEntry;
import com.basinwatch.domain.BasinModel;
import com.basinwatch.domain.BasinSnapshot;
import com.basinwatch.domain.MissionSnapshot;
import com.basinwatch.domain.MissionStatus;
import com.basinwatch.domain.MissionType;
import com.basinwatch.domain.ResourceType;
import com.basinwatch.domain.RiskLevel;
import com.basinwatch.domain.ZoneSnapshot;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class SessionSnapshot implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int CURRENT_VERSION = 1;

    private int version;
    private long tick;
    private SavedZone[] zones;
    private String[] resourceNames;
    private int[] resourceCounts;
    private SavedMission[] missions;
    private SavedActivity[] activity;
    private transient String sourcePathHint;

    private SessionSnapshot() {
    }

    static SessionSnapshot from(BasinSnapshot snapshot) {
        BasinModel.restore(snapshot);
        SessionSnapshot saved = new SessionSnapshot();
        saved.version = CURRENT_VERSION;
        saved.tick = snapshot.tick();
        saved.zones = snapshot.zones().stream()
                .map(zone -> new SavedZone(zone.id(), zone.name(), zone.column(), zone.row(),
                        zone.waterLevelMeters(), zone.lastRainMillimeters(), zone.exposedResidents(),
                        zone.leveeReinforced(), zone.lastReadingTick(), zone.risk().name()))
                .toArray(SavedZone[]::new);
        ResourceType[] types = ResourceType.values();
        saved.resourceNames = new String[types.length];
        saved.resourceCounts = new int[types.length];
        for (int index = 0; index < types.length; index++) {
            saved.resourceNames[index] = types[index].name();
            saved.resourceCounts[index] = snapshot.availableResources().get(types[index]);
        }
        saved.missions = snapshot.missions().stream()
                .map(mission -> new SavedMission(mission.id(), mission.type().name(),
                        mission.zoneId(), mission.progressTicks(), mission.durationTicks(),
                        mission.status().name(), mission.startedAtTick()))
                .toArray(SavedMission[]::new);
        saved.activity = snapshot.activity().stream()
                .map(entry -> new SavedActivity(entry.at().toEpochMilli(), entry.category(), entry.message()))
                .toArray(SavedActivity[]::new);
        return saved;
    }

    BasinSnapshot toBasinSnapshot() throws java.io.IOException {
        if (version != CURRENT_VERSION) {
            throw new java.io.IOException("This session save uses an unsupported format version: " + version);
        }
        if (tick < 0 || zones == null || zones.length != 6
                || resourceNames == null || resourceCounts == null
                || resourceNames.length != ResourceType.values().length
                || resourceCounts.length != resourceNames.length
                || missions == null || missions.length > 500
                || activity == null || activity.length > 160) {
            throw new java.io.IOException("Session save is incomplete or exceeds supported limits.");
        }
        EnumMap<ResourceType, Integer> resources = new EnumMap<>(ResourceType.class);
        for (int index = 0; index < resourceNames.length; index++) {
            try {
                ResourceType type = ResourceType.valueOf(resourceNames[index]);
                if (resources.put(type, resourceCounts[index]) != null) {
                    throw new java.io.IOException("Session save contains duplicate resource types.");
                }
            } catch (IllegalArgumentException ex) {
                throw new java.io.IOException("Session save contains an unknown resource type.", ex);
            }
        }
        if (resources.size() != ResourceType.values().length) {
            throw new java.io.IOException("Session save omits a resource category.");
        }
        try {
            List<ZoneSnapshot> zoneStates = new ArrayList<>(zones.length);
            for (SavedZone zone : zones) {
                zoneStates.add(new ZoneSnapshot(zone.id, zone.name, zone.column, zone.row,
                        zone.waterLevel, zone.lastRain, zone.exposedResidents, zone.leveeReinforced,
                        zone.lastReadingTick, RiskLevel.valueOf(zone.risk)));
            }
            List<MissionSnapshot> missionStates = new ArrayList<>(missions.length);
            for (SavedMission mission : missions) {
                missionStates.add(new MissionSnapshot(mission.id, MissionType.valueOf(mission.type),
                        mission.zoneId, mission.progressTicks, mission.durationTicks,
                        MissionStatus.valueOf(mission.status), mission.startedAtTick));
            }
            List<ActivityEntry> activityEntries = new ArrayList<>(activity.length);
            for (SavedActivity entry : activity) {
                activityEntries.add(new ActivityEntry(Instant.ofEpochMilli(entry.epochMillis),
                        entry.category, entry.message));
            }
            if (sourcePathHint != null) {
                activityEntries.add(new ActivityEntry(Instant.now(), "RESTORE",
                        "Snapshot decoded from " + sourcePathHint));
                if (activityEntries.size() > 160) {
                    activityEntries.remove(0);
                }
            }
            BasinSnapshot snapshot = new BasinSnapshot(tick, zoneStates, resources,
                    missionStates, activityEntries);
            BasinModel.restore(snapshot);
            return snapshot;
        } catch (IllegalArgumentException | NullPointerException | java.time.DateTimeException ex) {
            throw new java.io.IOException("Session save contains invalid basin state.", ex);
        }
    }

    void setSourcePathHint(String sourcePathHint) {
        this.sourcePathHint = sourcePathHint;
    }
}
