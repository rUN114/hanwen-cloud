package org.hanwen.common;

import java.util.UUID;

public final class Trace {
    private static final ThreadLocal<String> VALUE = new ThreadLocal<>();

    public static String id() {
        String s = VALUE.get();
        return s == null ? UUID.randomUUID().toString() : s;
    }

    public static void set(String s) {
        VALUE.set(s);
    }

    public static void clear() {
        VALUE.remove();
    }
}
