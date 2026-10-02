package com.basinwatch.domain;

import java.util.Map;

public enum MissionType {
    PUMP_DEPLOYMENT("Deploy portable pump", 8, 0.62,
            Map.of(ResourceType.PUMP, 1, ResourceType.FIELD_CREW, 1)),
    LEVEE_REINFORCEMENT("Reinforce levee", 10, 0.0,
            Map.of(ResourceType.LEVEE_CREW, 1, ResourceType.SUPPLY_TRUCK, 1)),
    EVACUATION("Evacuate exposed residents", 6, 0.0,
            Map.of(ResourceType.EVAC_VEHICLE, 1, ResourceType.FIELD_CREW, 1));

    private final String label;
    private final int durationTicks;
    private final double waterEffectMeters;
    private final Map<ResourceType, Integer> requirements;

    MissionType(String label, int durationTicks, double waterEffectMeters,
                Map<ResourceType, Integer> requirements) {
        this.label = label;
        this.durationTicks = durationTicks;
        this.waterEffectMeters = waterEffectMeters;
        this.requirements = Map.copyOf(requirements);
    }

    public String label() {
        return label;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public double waterEffectMeters() {
        return waterEffectMeters;
    }

    public Map<ResourceType, Integer> requirements() {
        return requirements;
    }

    @Override
    public String toString() {
        return label;
    }
}
