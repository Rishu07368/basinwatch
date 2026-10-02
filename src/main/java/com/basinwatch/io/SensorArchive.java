package com.basinwatch.io;

import com.basinwatch.domain.SensorReading;

import java.io.BufferedOutputStream;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class SensorArchive implements AutoCloseable {
    private static final int MAGIC = 0x42574152;
    private static final int FORMAT_VERSION = 1;
    private final DataOutputStream output;

    public SensorArchive(Path path) throws IOException {
        boolean newArchive = !Files.exists(path) || Files.size(path) == 0;
        if (!newArchive) {
            try (DataInputStream header = new DataInputStream(
                    new BufferedInputStream(Files.newInputStream(path)))) {
                if (header.readInt() != MAGIC || header.readInt() != FORMAT_VERSION) {
                    throw new IOException("Sensor archive marker or format version is invalid.");
                }
            } catch (java.io.EOFException ex) {
                throw new IOException("Sensor archive header is incomplete.", ex);
            }
        }
        output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)));
        if (newArchive) {
            output.writeInt(MAGIC);
            output.writeInt(FORMAT_VERSION);
            output.flush();
        }
    }

    public synchronized void append(SensorReading reading) throws IOException {
        output.writeLong(reading.tick());
        output.writeUTF(reading.stationId());
        output.writeUTF(reading.zoneId());
        output.writeDouble(reading.rainfallMillimeters());
        output.writeDouble(reading.observedRiseCentimeters());
    }

    @Override
    public synchronized void close() throws IOException {
        output.close();
    }
}
