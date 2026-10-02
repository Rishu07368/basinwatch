package com.basinwatch.domain;

public enum RiskLevel {
    NORMAL("Routine", 0),
    WATCH("Watch", 1),
    WARNING("Warning", 2),
    CRITICAL("Critical", 3);

    private final String label;
    private final int rank;

    RiskLevel(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }

    public String label() {
        return label;
    }

    public int rank() {
        return rank;
    }
}
