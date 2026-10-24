package org.hanwen.common;

import java.time.*;
import java.time.temporal.ChronoUnit;

public final class BusinessTime {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private BusinessTime() {}

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE).truncatedTo(ChronoUnit.MILLIS);
    }

    public static OffsetDateTime atOffset(LocalDateTime value) {
        return value.atZone(ZONE).toOffsetDateTime();
    }
}
