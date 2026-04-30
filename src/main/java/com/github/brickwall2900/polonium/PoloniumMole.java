package com.github.brickwall2900.polonium;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PoloniumMole {
    public Map<Path, Entry> entryList = new HashMap<>();

    public static class Entry implements Comparable<Entry> {
        public final Path filePath;
        public final long hash1;
        public final long hash2;
        public long timestamp;
        public boolean excluded;

        public Entry(Path filePath, long hash1, long hash2, long timestamp) {
            this.filePath = filePath;
            this.hash1 = hash1;
            this.hash2 = hash2;
            this.timestamp = timestamp;
        }

        public Entry(Path filePath, long hash1, long hash2, long timestamp, boolean excluded) {
            this.filePath = filePath;
            this.hash1 = hash1;
            this.hash2 = hash2;
            this.timestamp = timestamp;
            this.excluded = excluded;
        }

        public long getDecay() {
            return ChronoUnit.DAYS.between(
                    Instant.ofEpochSecond(timestamp),
                    Instant.now()
            );
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Entry entry = (Entry) o;
            return timestamp == entry.timestamp &&
                    hash1 == entry.hash1 &&
                    hash2 == entry.hash2 &&
                    Objects.equals(filePath, entry.filePath) &&
                    excluded == entry.excluded;
        }

        @Override
        public int hashCode() {
            return Objects.hash(filePath, hash1, hash2, timestamp, excluded);
        }

        @Override
        public int compareTo(@NotNull PoloniumMole.Entry o) {
            return filePath.compareTo(o.filePath);
        }
    }
}
