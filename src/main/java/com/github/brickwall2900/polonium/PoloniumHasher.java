package com.github.brickwall2900.polonium;

import com.dynatrace.hash4j.hashing.HashStream128;
import com.dynatrace.hash4j.hashing.Hashing;
import org.tinylog.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

public class PoloniumHasher {
    private static final int BUFFER_SIZE = 32 * 1024 * 1024; // 32 MB
    private static final int MAPPED_SIZE = 256 * 1024 * 1024; // 256 MB
    private static final long LARGE_FILE = 128 * 1024 * 1024; // 128 MB
    private static final byte[] TMP_BUFFER = new byte[BUFFER_SIZE];
    private static final boolean MAP_ONLY = Boolean.getBoolean(Polonium.PROP_ALWAYS_MAP_FILES_TO_MEMORY);

    public static void hashFile(Path file, long[] dst, BasicFileAttributes fileAttributes) throws IOException {
        long fileSize = fileAttributes.size();
        if (fileSize >= LARGE_FILE || MAP_ONLY) {
            hashLargeFile(file, fileSize, dst);
        } else {
            hashSmallFile(file, fileSize, dst);
        }
    }

    // it simply can NOT get faster than this ;-;
    //
    // without leaving file handles and memory mapped buffers
    // and letting the GC take care of them...
    // (who knows? 2 years might've passed and GC did NOT collect them free buffers...)
    // this serves as a compromise ;)
    private static void hashSmallFile(Path file, long fileSize, long[] dst) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(file), BUFFER_SIZE)) {
            long hash1, hash2;
            HashStream128 hasher = Hashing.xxh3_128().hashStream();

            int read = in.read(TMP_BUFFER);
            while (read > 0) {
                hasher.putBytes(TMP_BUFFER, 0, read);
                read = in.read(TMP_BUFFER);
            }
            hash1 = hasher.get().getLeastSignificantBits();
            hash2 = hasher.get().getMostSignificantBits();
            Logger.debug("{} has {} bytes in size, FileHash1 = {}, FileHash2 = {}", file, fileSize, hash1, hash2);
            dst[0] = hash1;
            dst[1] = hash2;
        }
    }

    private static void hashLargeFile(Path file, long size, long[] dst) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long position = 0;
            long hash1, hash2;

            HashStream128 hasher = Hashing.xxh3_128().hashStream();

            while (position < size) {
                long mappedSize = Math.min(MAPPED_SIZE, size - position);
                MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, position, mappedSize);
                long mapPosition = 0;
                while (mapPosition < mappedSize) {
                    int reading = Math.min(BUFFER_SIZE, buffer.remaining());
                    buffer.get(TMP_BUFFER, 0, reading);
                    hasher.putBytes(TMP_BUFFER, 0, reading);
                    mapPosition += reading;
                }
                position += mappedSize;
            }
            hash1 = hasher.get().getLeastSignificantBits();
            hash2 = hasher.get().getMostSignificantBits();
            Logger.debug("{} has {} bytes in size, FileHash1 = {}, FileHash2 = {}", file, size, hash1, hash2);
            dst[0] = hash1;
            dst[1] = hash2;
        }
    }
}
