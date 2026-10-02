package com.basinwatch.db;

public enum DatabaseHistoryType {
    SENSORS("Sensor readings"),
    MISSIONS("Missions"),
    EVENTS("Operational events"),
    RESOURCES("Resource usage");

    private final String label;

    DatabaseHistoryType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
