package com.basinwatch.domain;

public record DispatchOutcome(boolean accepted, String message, MissionSnapshot mission) {
    public static DispatchOutcome rejected(String message) {
        return new DispatchOutcome(false, message, null);
    }
}
