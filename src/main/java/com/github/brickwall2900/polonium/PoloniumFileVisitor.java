package com.github.brickwall2900.polonium;

import com.dynatrace.hash4j.hashing.HashValue128;
import com.github.brickwall2900.polonium.trash.PoloniumTrash;
import org.tinylog.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class PoloniumFileVisitor extends SimpleFileVisitor<Path> {
    private final Path dir;
    private final PoloniumMole mole;
    private final long deleteDays;
    private final long warnDays;
    private final long[] hashTmp = new long[2];
    protected final Set<Path> unvisitedFiles;
    protected final Set<Path> addedFiles;
    protected final Set<Path> deletedFiles;
    protected final Set<Path> warnedFiles;
    protected final Set<Path> files;

    public PoloniumFileVisitor(Path dir, PoloniumMole mole, long deleteDays, long warnDays) {
        this.dir = dir;
        this.mole = mole;
        this.deleteDays = deleteDays;
        this.warnDays = warnDays;

        unvisitedFiles = new HashSet<>(mole.entryList.keySet());
        addedFiles = new HashSet<>();
        deletedFiles = new HashSet<>();
        warnedFiles = new HashSet<>();
        files = new HashSet<>(mole.entryList.keySet());
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        try {
            BasicFileAttributes fileAttributes = Files.readAttributes(file, BasicFileAttributes.class);
            if (!fileAttributes.isSymbolicLink() && (!Files.isHidden(file) || Boolean.getBoolean(Polonium.PROP_INCLUDE_HIDDEN_FILES))) {
                Path relFile = dir.relativize(file);
                PoloniumMole.Entry entry = mole.entryList.get(relFile);
                if (entry != null && entry.excluded) {
                    Logger.debug("File {} was excluded", relFile);
                } else {
                    Logger.debug("File {}", relFile);
                    PoloniumHasher.hashFile(file, hashTmp, fileAttributes);
                    handleFileEntry(relFile, file, hashTmp[0], hashTmp[1], fileAttributes);
                }
                unvisitedFiles.remove(relFile);
            }
        } catch (IOException e) {
            Logger.error(e, "An exception occurred while processing file {}", file);
        }
        return FileVisitResult.CONTINUE;
    }

    private void handleFileEntry(Path file, Path fullPath, long hash1, long hash2, BasicFileAttributes fileAttributes) throws IOException {
        long timestampNow = Instant.now().getEpochSecond();
        PoloniumMole.Entry entry = mole.entryList.get(file);
        if (entry == null) {
            Logger.debug("{} is not found in file tracker, adding now.", file);
            entry = new PoloniumMole.Entry(file, hash1, hash2, timestampNow);
            addedFiles.add(file);
        } else {
            long entryHash1 = entry.hash1;
            long entryHash2 = entry.hash2;
            long decay = entry.getDecay();
            if (hash1 != entryHash1 || hash2 != entryHash2) {
                Logger.info("{} changed, reset decay.", file);
                decay = 0;
                entry.timestamp = timestampNow;
            }
            Logger.debug("{} is present in file tracker, tick decay which is {} days.", file, decay);
            if (decay > deleteDays) {
                deleteFile(file, fullPath, fileAttributes);
                mole.entryList.remove(file);
                return;
            } else if (decay > warnDays) {
                Logger.warn(" ==== {} is subject to get deleted. ==== ", file);
                warnedFiles.add(file);
            } else {
                Logger.debug("{} is not subject to warning nor deletion.", file);
            }
        }
        mole.entryList.put(file, entry);
    }

    private void deleteFile(Path file, Path fullPath, BasicFileAttributes fileAttributes) throws IOException {
        long timestampNow = Instant.now().getEpochSecond();
        Logger.warn("Deleting file {}", file);

        PoloniumMole.Entry entry = mole.entryList.get(file);
        PoloniumHasher.hashFile(fullPath, hashTmp, fileAttributes);
        long entryHash1 = entry.hash1;
        long entryHash2 = entry.hash2;
        long currentHash1 = hashTmp[0];
        long currentHash2 = hashTmp[1];
        if (currentHash1 != entryHash1 || currentHash2 != entryHash2) {
            Logger.info("{} changed, reset decay.", file);
            entry.timestamp = timestampNow;
            return;
        }

        deletedFiles.add(file);
        PoloniumTrash.trash(fullPath);
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
        Logger.error("visitFileFailed", exc);
        return FileVisitResult.CONTINUE;
    }
}
