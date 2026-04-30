package com.github.brickwall2900.app.gson;

import com.github.brickwall2900.app.gson.adapters.InstantAdapter;
import com.github.brickwall2900.app.gson.adapters.PathAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Path;
import java.time.Instant;

public class GsonUtil {
    private static Gson gson;

    public static Gson getGson() {
        if (gson == null) {
            GsonBuilder builder = new GsonBuilder();
            gson = builder
                    .setPrettyPrinting()
                    .serializeNulls()
                    .registerTypeAdapter(Instant.class, new InstantAdapter())
                    .registerTypeAdapter(Path.class, new PathAdapter())
                    .create();
        }
        return gson;
    }
}
