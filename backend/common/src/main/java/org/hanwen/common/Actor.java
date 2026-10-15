package org.hanwen.common;

public record Actor(long id, String role, int version) {
    private static final ThreadLocal<Actor> VALUE = new ThreadLocal<>();

    public static Actor current() {
        Actor a = VALUE.get();
        if (a == null) throw new ApiException(401, "AUTH_INVALID", "请重新登录");
        return a;
    }

    public static void set(Actor a) {
        VALUE.set(a);
    }

    public static void clear() {
        VALUE.remove();
    }
}
