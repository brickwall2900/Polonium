package com.github.brickwall2900.polonium;

import com.dynatrace.hash4j.hashing.HashValue128;
import com.dynatrace.hash4j.hashing.Hashing;
import com.github.brickwall2900.app.ApplicationUtil;
import com.github.brickwall2900.app.ProgressIndicator;
import org.tinylog.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

import static com.github.brickwall2900.app.TranslatableText.text;

public class PoloniumFileTracker {
    public static final Path DATABASE_DIR;

    static {
        DATABASE_DIR = ApplicationUtil.resolveSafe(Polonium.rootDirectory, "database");
        ApplicationUtil.createDirectoriesSafe(DATABASE_DIR);
    }

    private static String sanitize(String input) { // could've been a regex statement but whatever
        return input.replace('/', '_')
                .replace('\\', '_')
                .replace('<', '_')
                .replace('>', '_')
                .replace('?', '_')
                .replace(':', '_')
                .replace('\"', '_')
                .replace('|', '_');
    }

    private static String hash(String input) {
        HashValue128 value = Hashing.xxh3_128().hashStream().putString(input).get();
        return Long.toHexString(value.getMostSignificantBits()) + Long.toHexString(value.getLeastSignificantBits());
    }

    public static PoloniumMole loadMole(String name, ProgressIndicator progressIndicator) {
        String domainName = "mole-" + sanitize(hash(name));
        Path domainPath = ApplicationUtil.resolveSafe(DATABASE_DIR, domainName);
        Logger.info("MolePath = {}", domainPath);
        createMole(domainPath);
        return readMole(domainPath, progressIndicator);
    }

    private static void createMole(Path domainPath) {
        if (!Files.exists(domainPath)) {
            try {
                Files.createFile(domainPath);
                Logger.info("Created a new mole: {}", domainPath);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot create mole: " + domainPath, e);
            }
        } else if (Files.isDirectory(domainPath)) {
            throw new IllegalStateException("Mole appears as a directory: " + domainPath);
        }
    }

    private static final byte DIRECTORY_PUSH = 0x01;
    private static final byte DIRECTORY_POP = 0x02;
    private static final byte MOLE_ENTRY = 0x67;
    private static final byte ENTRY_COUNT = 0x42;
    private static final byte VERSION_CONTROL = 0x03;

    private static final short VERSION = 0x01;

    private static PoloniumMole readMole(Path domainPath, ProgressIndicator progressIndicator) {
        try (ProgressIndicator forked = progressIndicator.fork(text("task.mole.read"), 1)) {
            PoloniumMole mole = new PoloniumMole();

            try (DataInputStream dataInputStream = new DataInputStream(
                    new BufferedInputStream(Files.newInputStream(domainPath)))) {
                ArrayList<String> directoryStack = new ArrayList<>();
                int controlByte;
                Long entryCount = null;
                long entriesRead = 0;

                while ((controlByte = dataInputStream.read()) >= 0) {
                    byte control = (byte) controlByte;
                    switch (control) {
                        case VERSION_CONTROL -> {
                            short versionRead = dataInputStream.readShort();
                            if (versionRead != VERSION) {
                                throw new IllegalStateException("Version mismatch! " + versionRead + "; expected version " + VERSION);
                            }
                        }
                        case DIRECTORY_PUSH -> directoryStack.addLast(dataInputStream.readUTF());
                        case DIRECTORY_POP -> {
                            if (directoryStack.isEmpty()) {
                                throw new IllegalStateException("Directory stack underflow");
                            }

                            directoryStack.removeLast();
                        }
                        case MOLE_ENTRY -> {
                            String fileName = dataInputStream.readUTF();
                            Path path;
                            if (!directoryStack.isEmpty()) {
                                path = Path.of(directoryStack.getFirst());
                                int size = directoryStack.size();
                                for (int i = 1; i < size; i++) {
                                    path = ApplicationUtil.resolveSafe(path, directoryStack.get(i));
                                }
                                path = ApplicationUtil.resolveSafe(path, fileName);
                            } else {
                                path = Path.of(fileName); // TODO: malicious user input?
                            }
                            // if only that could've been done more elegantly

                            long hash1 = dataInputStream.readLong();
                            long hash2 = dataInputStream.readLong();
                            long timestamp = dataInputStream.readLong();
                            boolean excluded = dataInputStream.readBoolean();
                            mole.entryList.put(path, new PoloniumMole.Entry(path, hash1, hash2, timestamp, excluded));
                            entriesRead++;
                            forked.updateProgress(entriesRead);
                        }
                        case ENTRY_COUNT -> {
                            if (entryCount != null) {
                                throw new IllegalStateException("Entry count already initialized!");
                            } else {
                                entryCount = dataInputStream.readLong();
                                forked.setMaxProgress(entryCount);
                            }
                        }
                        default -> throw new IllegalStateException("Bad control byte " + control);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            Logger.info("Read mole with {} entries", mole.entryList.size());
            return mole;
        }
    }

    public static void saveMole(String name, PoloniumMole mole, ProgressIndicator progressIndicator) {
        String domainName = "mole-" + sanitize(hash(name));
        Path domainPath = ApplicationUtil.resolveSafe(DATABASE_DIR, domainName);
        Logger.info("MolePath = {}", domainPath);
        createMole(domainPath);
        writeMole(domainPath, mole, progressIndicator);
    }

    private static void writeEntryToStream(DataOutputStream out, String name, PoloniumMole.Entry entry) throws IOException {
        out.writeByte(MOLE_ENTRY);
        out.writeUTF(name);
        out.writeLong(entry.hash1);
        out.writeLong(entry.hash2);
        out.writeLong(entry.timestamp);
        out.writeBoolean(entry.excluded);
    }

    private static void writeMole(Path path, PoloniumMole mole, ProgressIndicator progressIndicator) {
        try (ProgressIndicator forked = progressIndicator.fork(text("task.mole.write"), mole.entryList.size())) {
            Path tmpPath = ApplicationUtil.resolveSafe(path.getParent(), path.getFileName().toString() + ".tmp");
            try (DataOutputStream dataOutputStream = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(tmpPath)))) {
                long writtenEntries = 0;
                TreeSet<Path> directoryTree = new TreeSet<>(mole.entryList.keySet());

                dataOutputStream.writeByte(VERSION_CONTROL);
                dataOutputStream.writeShort(VERSION);

                // write entry count first
                dataOutputStream.writeByte(ENTRY_COUNT);
                dataOutputStream.writeLong(directoryTree.size());

                Path prevPath = null;
                for (Path file : directoryTree) {
                    PoloniumMole.Entry entry = mole.entryList.get(file);
                    if (prevPath == null) {
                        // first path, we need to do a PUSH
                        for (int i = 0; i < file.getNameCount(); i++) {
                            Path name = file.getName(i);
                            if (i < file.getNameCount() - 1) {
                                dataOutputStream.writeByte(DIRECTORY_PUSH);
                                dataOutputStream.writeUTF(name.toString());
                            } else {
                                assert entry != null;
                                writeEntryToStream(dataOutputStream, name.toString(), entry);
                                writtenEntries++;
                                forked.updateProgress(writtenEntries);
                            }
                        }
                    } else {
                        try {
                            if (Objects.equals(prevPath.getParent(), file.getParent())) {
                                assert entry != null;
                                writeEntryToStream(dataOutputStream, file.getFileName().toString(), entry);
                                writtenEntries++;
                                forked.updateProgress(writtenEntries);
                            } else {
                                Path prevFileParent = prevPath.getParent();
                                Path relativePath = file;
                                if (prevFileParent != null) {
                                    relativePath = prevFileParent.relativize(file);
                                }
                                for (int i = 0; i < relativePath.getNameCount(); i++) {
                                    Path name = relativePath.getName(i);
                                    String fileName = name.getFileName().toString();
                                    if (i < relativePath.getNameCount() - 1) {
                                        if (fileName.equals("..")) {
                                            dataOutputStream.writeByte(DIRECTORY_POP);
                                        } else {
                                            dataOutputStream.writeByte(DIRECTORY_PUSH);
                                            dataOutputStream.writeUTF(fileName);
                                        }
                                    } else {
                                        assert entry != null;
                                        writeEntryToStream(dataOutputStream, fileName, entry);
                                        writtenEntries++;
                                        forked.updateProgress(writtenEntries);
                                    }
                                }
                            }
                        } catch (IllegalArgumentException e) {
                            Logger.warn(e);
                        }
                    }
                    prevPath = file;
                    // if only this could've been done MORE ELEGANTLY...
                }
                Files.move(tmpPath, path, StandardCopyOption.REPLACE_EXISTING);
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
            Logger.info("Written mole with {} entries", mole.entryList.size());
        }
    }

    public static void removeMole(String name) {
        String domainName = "mole-" + sanitize(hash(name));
        Path domainPath = ApplicationUtil.resolveSafe(DATABASE_DIR, domainName);
        if (Files.exists(domainPath)) {
            try {
                Files.delete(domainPath);
                Logger.info("Deleted mole: {}", domainPath);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot delete mole: " + domainPath, e);
            }
        } else if (Files.isDirectory(domainPath)) {
            throw new IllegalStateException("Mole appears as a directory: " + domainPath);
        }
    }

    public static boolean doesMoleExist(String name) {
        String domainName = "mole-" + sanitize(hash(name));
        Path domainPath = ApplicationUtil.resolveSafe(DATABASE_DIR, domainName);
        return Files.exists(domainPath) && Files.isRegularFile(domainPath);
    }
}