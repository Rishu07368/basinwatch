package com.basinwatch.domain;

import java.util.List;
import java.util.Map;

public record BasinSnapshot(
        long tick,
        List<ZoneSnapshot> zones,
        Map<ResourceType, Integer> availableResources,
        List<MissionSnapshot> missions,
        List<ActivityEntry> activity) {
    public BasinSnapshot {
        zones = List.copyOf(zones);
        availableResources = Map.copyOf(availableResources);
        missions = List.copyOf(missions);
        activity = List.copyOf(activity);
    }

    public ZoneSnapshot zone(String id) {
        return zones.stream()
                .filter(zone -> zone.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown zone: " + id));
    }
}
