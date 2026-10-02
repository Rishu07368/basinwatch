package com.basinwatch.io;

import com.basinwatch.domain.ActivityEntry;
import com.basinwatch.domain.BasinSnapshot;
import com.basinwatch.domain.MissionSnapshot;
import com.basinwatch.domain.ResourceType;
import com.basinwatch.domain.ZoneSnapshot;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class ReportWriter {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private ReportWriter() {
    }

    public static Path write(Path directory, BasinSnapshot snapshot) throws IOException {
        String filename = "basin-status-" + FILE_TIME.format(Instant.now())
                + "-" + UUID.randomUUID().toString().substring(0, 8) + ".txt";
        Path report = directory.resolve(filename);
        try (BufferedWriter writer = Files.newBufferedWriter(report, StandardCharsets.UTF_8)) {
            writer.write("BASINWATCH · SITUATION REPORT");
            writer.newLine();
            writer.write("Generated: " + Instant.now());
            writer.newLine();
            writer.write("Simulation interval: " + snapshot.tick());
            writer.newLine();
            writer.newLine();
            writer.write("ZONE CONDITIONS");
            writer.newLine();
            for (ZoneSnapshot zone : snapshot.zones()) {
                writer.write(String.format("%s | %s | %.2f m | %.1f mm rain | %s | %d exposed%n",
                        zone.id(), zone.name(), zone.waterLevelMeters(), zone.lastRainMillimeters(),
                        zone.risk().label(), zone.exposedResidents()));
            }
            writer.newLine();
            writer.write("AVAILABLE RESPONSE RESOURCES");
            writer.newLine();
            for (ResourceType type : ResourceType.values()) {
                writer.write(type.label() + ": "
                        + snapshot.availableResources().getOrDefault(type, 0));
                writer.newLine();
            }
            writer.newLine();
            writer.write("RESPONSE OPERATIONS");
            writer.newLine();
            for (MissionSnapshot mission : snapshot.missions()) {
                writer.write(mission.id() + " | " + mission.type().label() + " | "
                        + mission.zoneId() + " | " + mission.status().label() + " | "
                        + mission.progressPercent() + "%");
                writer.newLine();
            }
            writer.newLine();
            writer.write("RECENT ACTIVITY");
            writer.newLine();
            for (ActivityEntry entry : snapshot.activity()) {
                writer.write(entry.toString());
                writer.newLine();
            }
        }
        return report;
    }
}
