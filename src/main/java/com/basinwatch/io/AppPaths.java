package com.basinwatch.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public record AppPaths(Path root, Path logs, Path archive, Path saves, Path reports) {
    public static Path resolveDataRoot(String[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return defaultDataRoot();
        }
        if (arguments.length != 2 || !"--data-dir".equals(arguments[0])
                || arguments[1] == null || arguments[1].isBlank()) {
            throw new IllegalArgumentException(
                    "Usage: BasinWatch [--data-dir <writable-folder>]");
        }
        return Path.of(arguments[1]).toAbsolutePath().normalize();
    }

    public static Path defaultDataRoot() {
        String osName = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            String appData = System.getenv("APPDATA");
            Path roaming = appData == null || appData.isBlank()
                    ? Path.of(System.getProperty("user.home"), "AppData", "Roaming")
                    : Path.of(appData);
            return roaming.resolve("BasinWatch").toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".basinwatch")
                .toAbsolutePath().normalize();
    }

    public static AppPaths create(Path root) throws IOException {
        Path absolute = root.toAbsolutePath().normalize();
        Path logs = absolute.resolve("data").resolve("logs");
        Path archive = absolute.resolve("data").resolve("archive");
        Path saves = absolute.resolve("data").resolve("saves");
        Path reports = absolute.resolve("data").resolve("reports");
        Files.createDirectories(logs);
        Files.createDirectories(archive);
        Files.createDirectories(saves);
        Files.createDirectories(reports);
        return new AppPaths(absolute, logs, archive, saves, reports);
    }
}
