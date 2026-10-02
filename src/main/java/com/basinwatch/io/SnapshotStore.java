package com.basinwatch.io;

import com.basinwatch.domain.BasinSnapshot;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class SnapshotStore {
    private static final long MAX_SAVE_BYTES = 5L * 1024 * 1024;

    public void save(Path target, BasinSnapshot snapshot) throws IOException {
        Path absolute = target.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) {
            throw new IOException("Session save needs a parent directory.");
        }
        Files.createDirectories(parent);
        byte[] payload = encode(SessionSnapshot.from(snapshot));
        if (payload.length > MAX_SAVE_BYTES) {
            throw new IOException("Session is too large to save safely.");
        }
        Path temporary = Files.createTempFile(parent, absolute.getFileName().toString(), ".pending");
        boolean moved = false;
        try {
            try (BufferedOutputStream output = new BufferedOutputStream(Files.newOutputStream(
                    temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING))) {
                output.write(payload);
            }
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    public BasinSnapshot load(Path source) throws IOException {
        Path absolute = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) {
            throw new IOException("Session save does not exist: " + absolute);
        }
        long size = Files.size(absolute);
        if (size < 1 || size > MAX_SAVE_BYTES) {
            throw new IOException("Session save is empty or exceeds the 5 MB safety limit.");
        }
        try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(absolute))) {
            byte[] payload = input.readNBytes((int) MAX_SAVE_BYTES + 1);
            if (payload.length > MAX_SAVE_BYTES) {
                throw new IOException("Session save exceeds the 5 MB safety limit.");
            }
            SessionSnapshot snapshot = decode(payload);
            snapshot.setSourcePathHint(absolute.getFileName().toString());
            return snapshot.toBasinSnapshot();
        } catch (InvalidClassException ex) {
            throw new IOException("Session save contains a class that BasinWatch does not accept.", ex);
        }
    }

    private static byte[] encode(SessionSnapshot snapshot) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(snapshot);
            output.flush();
            return bytes.toByteArray();
        }
    }

    private static SessionSnapshot decode(byte[] payload) throws IOException {
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(payload);
             ObjectInputStream input = new ObjectInputStream(bytes)) {
            input.setObjectInputFilter(SnapshotStore::filter);
            Object restored = input.readObject();
            if (!(restored instanceof SessionSnapshot snapshot)) {
                throw new IOException("File does not contain a BasinWatch session.");
            }
            return snapshot;
        } catch (ClassNotFoundException ex) {
            throw new IOException("A required BasinWatch save class is unavailable.", ex);
        }
    }

    private static ObjectInputFilter.Status filter(ObjectInputFilter.FilterInfo info) {
        if (info.depth() > 12 || info.references() > 2_000 || info.streamBytes() > MAX_SAVE_BYTES) {
            return ObjectInputFilter.Status.REJECTED;
        }
        Class<?> type = info.serialClass();
        if (type == null) {
            return ObjectInputFilter.Status.UNDECIDED;
        }
        if (type.isPrimitive() || type == String.class
                || type == SessionSnapshot.class || type == SavedZone.class
                || type == SavedMission.class || type == SavedActivity.class) {
            return ObjectInputFilter.Status.ALLOWED;
        }
        if (type.isArray()) {
            Class<?> component = type.getComponentType();
            if (component.isPrimitive() || component == String.class
                    || component == SavedZone.class || component == SavedMission.class
                    || component == SavedActivity.class) {
                return ObjectInputFilter.Status.ALLOWED;
            }
        }
        return ObjectInputFilter.Status.REJECTED;
    }
}
