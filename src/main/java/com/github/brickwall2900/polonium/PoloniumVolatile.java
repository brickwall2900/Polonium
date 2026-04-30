package com.github.brickwall2900.polonium;

import com.github.brickwall2900.app.ApplicationUtil;
import com.github.brickwall2900.app.gson.GsonUtil;
import com.google.gson.Gson;
import org.tinylog.Logger;
import org.tinylog.Supplier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class PoloniumVolatile {
    public static final Path VOLATILE_FILE;
    private static final Map<String, Object> VOLATILE_CONTENTS;
    private static final Gson GSON;

    static {
        VOLATILE_FILE = ApplicationUtil.resolveSafe(Polonium.rootDirectory, "volatile.json");
        ApplicationUtil.createFileSafe(VOLATILE_FILE);

        GSON = GsonUtil.getGson();
        VOLATILE_CONTENTS = readContents();
        addShutdownHook();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readContents() {
        Map<String, Object> volatileContents = new TreeMap<>();

        Logger.info("Loading volatile contents: {}", VOLATILE_FILE);
        try (BufferedReader reader = Files.newBufferedReader(VOLATILE_FILE)) {
            Object o = GSON.fromJson(reader, Map.class);
            if (o instanceof Map<?,?> m) {
                volatileContents.putAll((Map<? extends String, ?>) m);
            } else {
                Logger.error("Invalid or null volatile object: {}; loading empty one instead!", o);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load volatile contents", e);
        }

        Logger.info("Loaded volatile contents");

        return volatileContents;
    }

    private static void saveContents() {
        Logger.info("Saving volatile contents: {}", VOLATILE_FILE);
        String json = GSON.toJson(VOLATILE_CONTENTS);
        try {
            Files.writeString(VOLATILE_FILE, json);
            Logger.info("Saved volatile contents");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save volatile contents", e);
        }
    }

    public static Object get(String key) {
        Object o = VOLATILE_CONTENTS.get(key);
        Logger.trace("Volatile GET: [{}] = {}", key, o);
        return o;
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(String key, Supplier<T> absent) {
        if (!VOLATILE_CONTENTS.containsKey(key)) {
            VOLATILE_CONTENTS.put(key, absent.get());
        }
        T o = (T) VOLATILE_CONTENTS.get(key);
        Logger.trace("Volatile GET: [{}] = {}", key, o);
        return o;
    }

    public static Object remove(String key) {
        Object o = VOLATILE_CONTENTS.remove(key);
        Logger.trace("Volatile DELETE: [{}] = {}", key, o);
        return o;
    }

    public static Object put(String key, Object value) {
        Logger.trace("Volatile PUT: [{}] = {}", key, value);
        return VOLATILE_CONTENTS.put(key, value);
    }

    // save trash database
    private static void addShutdownHook() {
        Polonium.addShutdownHook(PoloniumVolatile::saveContents);
    }
}
