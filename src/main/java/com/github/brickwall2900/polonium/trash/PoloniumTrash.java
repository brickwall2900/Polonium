package com.github.brickwall2900.polonium.trash;

import com.github.brickwall2900.app.ApplicationUtil;
import com.github.brickwall2900.polonium.Polonium;
import com.github.brickwall2900.polonium.PoloniumConfig;
import com.github.brickwall2900.polonium.PoloniumHasher;
import com.github.brickwall2900.polonium.PoloniumVolatile;
import org.jetbrains.annotations.NotNull;
import org.tinylog.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.github.brickwall2900.app.TranslatableText.text;

public class PoloniumTrash {
    public static final Path TRASH_DIRECTORY;
    public static final Path TRASH_DATABASE_FILE;
    public static final TrashDatabase TRASH_DATABASE;
    private static final int VERSION = 1;
    private static PoloniumConfig config;

    static {
        TRASH_DIRECTORY = ApplicationUtil.resolveSafe(Polonium.rootDirectory, "trash");
        TRASH_DATABASE_FILE = ApplicationUtil.resolveSafe(Polonium.rootDirectory, "trash-db");

        ApplicationUtil.createDirectoriesSafe(TRASH_DIRECTORY);
        ApplicationUtil.createFileSafe(TRASH_DATABASE_FILE);

        TRASH_DATABASE = readTrashDatabase();
        addShutdownHook();
    }

    private static TrashDatabase readTrashDatabase() {
        TrashDatabase trashDatabase = new TrashDatabase();

        Logger.info("Loading trash database: {}", TRASH_DATABASE_FILE);
        try (DataInputStream inputStream = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(TRASH_DATABASE_FILE)))) {
            int versionRead = inputStream.read();
            if (versionRead >= 0) {
                if (versionRead != VERSION) {
                    throw new IllegalStateException("Invalid trash database version");
                }
                long count = inputStream.readLong();
                while (count > 0) {
                    String id = inputStream.readUTF();
                    String path = inputStream.readUTF();
                    String filename = inputStream.readUTF();
                    long timestamp = inputStream.readLong();
                    long size = inputStream.readLong();
                    TrashDatabase.Entry entry = new TrashDatabase.Entry(id, timestamp, size, filename, path);
                    trashDatabase.entryMap.put(id, entry);
                    count--;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Logger.info("Loaded trash database; it has {} items in trash", trashDatabase.getItemsTrashed());

        return trashDatabase;
    }

    private static void saveTrashDatabase(TrashDatabase trashDatabase) {
        Path tmpPath = ApplicationUtil.resolveSafe(TRASH_DATABASE_FILE.getParent(), TRASH_DATABASE_FILE.getFileName().toString() + ".tmp");
        Logger.info("Saving trash database: {}", TRASH_DATABASE_FILE);
        Logger.info("Trash has {} items.", TRASH_DATABASE.getItemsTrashed());
        try (DataOutputStream outputStream = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(tmpPath)))) {
            outputStream.write(VERSION);
            outputStream.writeLong(trashDatabase.entryMap.size());
            for (TrashDatabase.Entry entry : trashDatabase.entryMap.values()) {
                outputStream.writeUTF(entry.id);
                outputStream.writeUTF(entry.filename);
                outputStream.writeUTF(entry.path);
                outputStream.writeLong(entry.timestampTrashed);
                outputStream.writeLong(entry.size);
            }
            Files.move(tmpPath, TRASH_DATABASE_FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (Files.exists(tmpPath)) {
                try {
                    Files.delete(tmpPath);
                } catch (IOException e) {
                    Logger.error("Unable to delete temporary file!");
                }
            }
        }
    }

    // save trash database
    private static void addShutdownHook() {
        Polonium.addShutdownHook(() -> saveTrashDatabase(TRASH_DATABASE));
    }

    public static void setConfig(PoloniumConfig c) {
        config = c;
    }

    public static void decay() {
        Objects.requireNonNull(config, "Config was not set!");
        HashSet<String> files = new HashSet<>(TRASH_DATABASE.entryMap.keySet());
        Logger.info("Trash is decaying...");

        Set<TrashDatabase.Entry> deletedForeverFiles = new HashSet<>();

        List<TrashDatabase.Entry> deletedForeverItems = PoloniumVolatile.get("Trash.DeletedForever", ArrayList::new);
        deletedForeverItems.clear();

        List<TrashDatabase.Entry> warnedItems = PoloniumVolatile.get("Trash.Warned", ArrayList::new);
        warnedItems.clear();

        List<TrashDatabase.Entry> filesInTrash = PoloniumVolatile.get("Trash", ArrayList::new);
        filesInTrash.clear();

        if (config.deleteForever) {
            for (String file : files) {
                TrashDatabase.Entry entry = TRASH_DATABASE.entryMap.get(file);
                if (entry.getDecay() > config.deleteDays) {
                    deleteForever(file);
                    deletedForeverFiles.add(entry);
                    deletedForeverItems.add(entry);
                } else if (entry.getDecay() > config.warnDays) {
                    Logger.warn(" ========== File {} is about to get PERMANENTLY DELETED in {} days! ========== ",
                            config.deleteDays - entry.getDecay());
                    warnedItems.add(entry);
                }
            }
        } else {
            Logger.warn("Deletion forever is not enabled.");
        }
        filesInTrash.addAll(TRASH_DATABASE.entryMap.values());

        Logger.trace("== Files in trash ==");
        for (TrashDatabase.Entry e : new TreeSet<>(TRASH_DATABASE.entryMap.values())) {
            Logger.trace("[*] {}", e.filename);
        }

        Logger.trace("== Files DELETED FOREVER ==");
        for (TrashDatabase.Entry e : new TreeSet<>(deletedForeverFiles)) {
            Logger.trace("[X] {}", e.filename);
        }

        PoloniumVolatile.put("Trash.DeletedForever", deletedForeverItems);
        PoloniumVolatile.put("Trash.Warned", warnedItems);
        PoloniumVolatile.put("Trash", filesInTrash);
    }

    private static String makeId(Path path) throws IOException {
        String randomChars = Integer.toHexString(path.hashCode());
        long[] dstHash = new long[2];
        PoloniumHasher.hashFile(path, dstHash, Files.readAttributes(path, BasicFileAttributes.class));
        String hash1 = Long.toHexString(dstHash[0]);
        String hash2 = Long.toHexString(dstHash[1]);
        return randomChars + hash1 + hash2;
    }

    public static void trash(Path path) {
        try {
            long size = Files.size(path);
            String id = makeId(path);
            String filename = path.getFileName().toString();
            Logger.warn("Putting item to trash: {} ({})", path, id);
            Files.move(path, ApplicationUtil.resolveSafe(TRASH_DIRECTORY, id));
            TRASH_DATABASE.entryMap.put(id, new TrashDatabase.Entry(id, Instant.now().getEpochSecond(), size, filename, path.toString()));
        } catch (IOException e) {
            Logger.error(e);
            throw new UncheckedIOException(e);
        }
    }

    public static void restore(String id, Path dest) {
        if (TRASH_DATABASE.entryMap.containsKey(id)) {
            Path trashedFile = ApplicationUtil.resolveSafe(TRASH_DIRECTORY, id);
            TrashDatabase.Entry entry = TRASH_DATABASE.entryMap.remove(id);
            try {
                if (Files.exists(trashedFile)) {
                    Logger.warn("Restoring item from trash: {} -> {}", trashedFile, dest);
                    Files.move(trashedFile, dest);
                    TRASH_DATABASE.entryMap.remove(id);

                    List<TrashDatabase.Entry> trashItems = PoloniumVolatile.get("Trash", ArrayList::new);
                    trashItems.remove(entry);
                    PoloniumVolatile.put("Trash", trashItems);
                } else {
                    Logger.warn("File {} (filename: {}; id: {}) was not present in trash!", trashedFile, entry.filename, id);
                    throw new IOException("File not present in trash!");
                }
            } catch (IOException e) {
                Logger.error(e);
                throw new UncheckedIOException(e);
            }
        } else {
            throw new IllegalArgumentException("No such trashed item exist in database!");
        }
    }

    public static void deleteForever(String id) {
        if (!config.deleteForever) {
            return;
        }

        Path trashedFile = ApplicationUtil.resolveSafe(TRASH_DIRECTORY, id);
        try {
            TrashDatabase.Entry entry = TRASH_DATABASE.entryMap.get(id);
            if (Files.exists(trashedFile)) {
                Logger.warn("Deleting file forever from trash: {}; (filename: {}; id: {})", trashedFile, entry.filename, id);
                Files.delete(trashedFile);
                TRASH_DATABASE.entryMap.remove(id);
            } else {
                Logger.warn("File {} (filename: {}; id: {}) was not present in trash!", trashedFile, entry.filename, id);
                TRASH_DATABASE.entryMap.remove(id);
                throw new IOException("File not present in trash!");
            }
        } catch (IOException e) {
            Logger.error(e);
            throw new UncheckedIOException(e);
        }
    }

    public static class TrashDatabase {
        public Map<String, Entry> entryMap = new HashMap<>();

        public int getItemsTrashed() {
            return entryMap.size();
        }

        public static class Entry implements Comparable<Entry> {
            public String id;
            public long timestampTrashed;
            public long size;
            public String filename;
            public String path;

            public Entry(String id, long timestampTrashed, long size, String filename, String path) {
                this.id = id;
                this.timestampTrashed = timestampTrashed;
                this.size = size;
                this.filename = filename;
                this.path = path;
            }

            public long getDecay() {
                return ChronoUnit.DAYS.between(
                        Instant.ofEpochSecond(timestampTrashed),
                        Instant.now()
                );
            }

            @Override
            public boolean equals(Object o) {
                if (o == null || getClass() != o.getClass()) return false;
                Entry entry = (Entry) o;
                return timestampTrashed == entry.timestampTrashed && size == entry.size && Objects.equals(id, entry.id) && Objects.equals(filename, entry.filename) && Objects.equals(path, entry.path);
            }

            @Override
            public int hashCode() {
                return Objects.hash(id, timestampTrashed, size, filename, path);
            }

            @Override
            public int compareTo(@NotNull PoloniumTrash.TrashDatabase.Entry o) {
                return filename.compareTo(o.filename);
            }

            @Override
            public String toString() {
                return text("trash.entry", filename, getDecay());
            }
        }
    }
}
