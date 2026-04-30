package com.github.brickwall2900.polonium;

import com.github.brickwall2900.app.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PoloniumConfig extends Config {
    public List<String> paths = new ArrayList<>();
    public long warnDays = 365 / 2;
    public long deleteDays = 365;
    public boolean deleteForever = false;
    public float gravity = 1;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PoloniumConfig that = (PoloniumConfig) o;
        return warnDays == that.warnDays && deleteDays == that.deleteDays && Objects.equals(paths, that.paths);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paths, warnDays, deleteDays);
    }
}
