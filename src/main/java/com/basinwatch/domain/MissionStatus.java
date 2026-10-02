package com.basinwatch.domain;

public enum MissionStatus {
    ACTIVE("In progress"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    private final String label;

    MissionStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
