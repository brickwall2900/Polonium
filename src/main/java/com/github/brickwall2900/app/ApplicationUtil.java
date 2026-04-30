package com.github.brickwall2900.app;

import org.tinylog.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ApplicationUtil {
    public static Path createDirectoriesSafe(Path directory) {
        if (!Files.exists(directory)) {
            try {
                Logger.debug("Creating directory {}", directory);
                return Files.createDirectories(directory);
            } catch (IOException e) {
                Logger.error(e);
                throw new UncheckedIOException(e);
            }
        } else if (Files.isRegularFile(directory)) {
            throw new IllegalStateException("DIRECTORY appears as a file: " + directory);
        }
        return directory;
    }

    public static Path createFileSafe(Path file) {
        if (!Files.exists(file)) {
            try {
                Logger.info("Creating a new file: {}", file);
                return Files.createFile(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else if (Files.isDirectory(file)) {
            throw new IllegalStateException("FILE appears as a directory: " + file);
        }
        return file;
    }

    public static Path resolveSafe(Path path, String other) {
        Path normalized = path.resolve(other).normalize();
        if (!normalized.startsWith(path)) {
            throw new IllegalArgumentException("Attempted traversal attack: %s -> %s".formatted(path, normalized));
        }

        return normalized;
    }
}
