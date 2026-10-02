package com.basinwatch.domain;

public record DispatchRequest(
        long tick,
        String zoneId,
        MissionType missionType) implements BasinEvent {
    public DispatchRequest {
        if (tick < 0 || zoneId == null || !zoneId.matches("[A-Z0-9-]{1,32}")
                || missionType == null) {
            throw new IllegalArgumentException("Invalid dispatch request.");
        }
    }

    @Override
    public String category() {
        return "DISPATCH REQUEST";
    }

    @Override
    public String describe() {
        return missionType.label() + " requested for " + zoneId;
    }
}
