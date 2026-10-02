package com.basinwatch.domain;

public enum ResourceType {
    PUMP("Portable pumps"),
    LEVEE_CREW("Levee crews"),
    SUPPLY_TRUCK("Supply trucks"),
    EVAC_VEHICLE("Evacuation vehicles"),
    FIELD_CREW("Field crews");

    private final String label;

    ResourceType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
