package com.basinwatch.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class SaveCatalog {
    private SaveCatalog() {
    }

    public static List<Path> list(Path directory) throws IOException {
        File folder = directory.toFile();
        File[] files = folder.listFiles((parent, name) -> name.toLowerCase().endsWith(".bws"));
        if (files == null) {
            throw new IOException("Unable to list session saves in " + directory.toAbsolutePath());
        }
        return Arrays.stream(files)
                .filter(File::isFile)
                .sorted(Comparator.comparingLong(File::lastModified).reversed())
                .map(File::toPath)
                .toList();
    }
}
