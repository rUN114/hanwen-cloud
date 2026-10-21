package org.hanwen.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

public final class Views {
    public static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], value(kv[i + 1]));
        return m;
    }

    private static Object value(Object v) {
        if (v instanceof Long l) return l.toString();
        if (v instanceof LocalDateTime t) return t.atOffset(ZoneOffset.ofHours(8)).toString();
        if (v instanceof BigDecimal n) return n.setScale(2, RoundingMode.HALF_UP).toPlainString();
        return v;
    }

    public static Map<String, Object> page(List<?> items, int page, int size, long total) {
        Map<String, Object> result = map("items", items, "page", page, "size", size);
        result.put("total", total);
        return result;
    }

    public static int size(int s) {
        if (s < 1 || s > 100) throw ApiException.bad("每页数量须为1至100");
        return s;
    }

    public static int offset(int p, int s) {
        size(s);
        if (p < 1 || p > 100000) throw ApiException.bad("页码不合法");
        return (p - 1) * s;
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                .truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    }

    public static String text(String s, int max, String label) {
        if (s == null) throw ApiException.bad(label + "不能为空");
        s = s.strip();
        if (s.isEmpty() || s.codePointCount(0, s.length()) > max)
            throw ApiException.bad(label + "长度须为1至" + max + "字");
        return s;
    }

    public static String optional(String s, int max, String label) {
        return s == null || s.isBlank() ? null : text(s, max, label);
    }

    public static BigDecimal score(String s, BigDecimal max, boolean positive) {
        try {
            if (s == null || !s.matches("\\d{1,3}(\\.\\d{1,2})?"))
                throw new IllegalArgumentException();
            BigDecimal n = new BigDecimal(s);
            if (n.compareTo(max) > 0 || n.signum() < (positive ? 1 : 0))
                throw new IllegalArgumentException();
            return n.setScale(2);
        } catch (Exception e) {
            throw ApiException.bad("分数须在允许范围内，最多两位小数");
        }
    }

    public static LocalDateTime deadline(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return OffsetDateTime.parse(s)
                    .atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                    .toLocalDateTime();
        } catch (Exception e) {
            throw ApiException.bad("截止时间须包含时区");
        }
    }

    public static void role(String expected) {
        if (!Actor.current().role().equals(expected)) throw ApiException.forbidden();
    }
}
