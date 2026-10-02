package com.basinwatch.domain;

import java.util.Locale;

public record SensorReading(
        long tick,
        String stationId,
        String zoneId,
        double rainfallMillimeters,
        double observedRiseCentimeters) implements BasinEvent {

    public SensorReading {
        if (tick < 0 || !validIdentifier(stationId) || !validIdentifier(zoneId)
                || !Double.isFinite(rainfallMillimeters) || rainfallMillimeters < 0
                || rainfallMillimeters > 500
                || !Double.isFinite(observedRiseCentimeters) || observedRiseCentimeters < 0
                || observedRiseCentimeters > 200) {
            throw new IllegalArgumentException("Invalid sensor reading.");
        }
    }

    @Override
    public String category() {
        return "OBSERVATION";
    }

    @Override
    public String describe() {
        return String.format(Locale.ROOT, "%s reports %.1f mm rain; water rise %.1f cm",
                stationId, rainfallMillimeters, observedRiseCentimeters);
    }

    public String encode() {
        return String.format(Locale.ROOT, "READ|%d|%s|%s|%.3f|%.3f",
                tick, stationId, zoneId, rainfallMillimeters, observedRiseCentimeters);
    }

    public static SensorReading parse(String line) {
        String[] fields = line.split("\\|", -1);
        if (fields.length != 6 || !"READ".equals(fields[0])) {
            throw new IllegalArgumentException("Malformed sensor observation.");
        }
        try {
            return new SensorReading(
                    Long.parseLong(fields[1]),
                    fields[2],
                    fields[3],
                    Double.parseDouble(fields[4]),
                    Double.parseDouble(fields[5]));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Sensor reading contains an invalid number.", ex);
        }
    }

    private static boolean validIdentifier(String value) {
        return value != null && value.matches("[A-Z0-9-]{1,32}");
    }
}
