package com.github.brickwall2900.app;

import java.util.ResourceBundle;

public class TranslatableText {
    public static final ResourceBundle BUNDLE = ResourceBundle.getBundle("lang.lang");

    public static String text(String key, Object... objects) {
        return BUNDLE.getString(key).formatted(objects);
    }

    public static String[] getArray(String key) {
        String text = text(key);
        return text.split("\\|");
    }
}
