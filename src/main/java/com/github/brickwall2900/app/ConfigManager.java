package com.github.brickwall2900.app;

import com.github.brickwall2900.app.gson.GsonUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import org.tinylog.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class ConfigManager {
    public static Path globalConfig;
    private static Gson gson;

    public static void init(String configFileName, Path rootDirectory) {
        if (gson != null || globalConfig != null) {
            throw new IllegalStateException("ConfigManager already initialized!");
        }

        globalConfig = ApplicationUtil.resolveSafe(rootDirectory, configFileName);
        ApplicationUtil.createFileSafe(globalConfig);

        gson = GsonUtil.getGson();
    }

    public static <T> T load(Path file, Class<T> type) {
        Objects.requireNonNull(type, "type missing");
        Logger.info("Config load from {}", file);
        try (BufferedReader reader = Files.newBufferedReader(file);
             JsonReader jsonReader = new JsonReader(reader)) {
            Config c = gson.fromJson(jsonReader, type);
            if (c == null) {
                Logger.error("Failed to load config file: {}; returning new Config();", file);
            }
            return type.cast(c != null ? c : type.getConstructor().newInstance());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
            throw new UnsupportedOperationException("Failed to create default object", e);
        }
    }

    public static void save(Path file, Config config) {
        Logger.info("Config save to {}", file);
        String output = null;
        try {
            output = gson.toJson(config);
            Files.writeString(file, output); // prevent file cutoffs
        } catch (IOException e) {
            Logger.error(e, "Failed to save config file from an exception: {}; output = {}", file, output);
            throw new RuntimeException(e);
        }
    }
}
