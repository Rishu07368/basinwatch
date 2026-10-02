package com.basinwatch.io;

import com.basinwatch.domain.ActivityEntry;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;

public final class OperationalJournal implements AutoCloseable {
    private final BufferedWriter writer;
    private int pendingLines;

    public OperationalJournal(Path path) throws IOException {
        writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public synchronized void append(ActivityEntry entry) throws IOException {
        writer.write(DateTimeFormatter.ISO_INSTANT.format(entry.at()));
        writer.write(" | ");
        writer.write(entry.category().replace('\n', ' '));
        writer.write(" | ");
        writer.write(entry.message().replace('\n', ' '));
        writer.newLine();
        pendingLines++;
        if (pendingLines >= 12 || entry.category().contains("WARNING")
                || entry.category().contains("COMPLETE") || entry.category().contains("ALLOCATED")) {
            writer.flush();
            pendingLines = 0;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        writer.flush();
        writer.close();
    }
}
