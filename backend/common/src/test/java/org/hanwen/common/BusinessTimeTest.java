package org.hanwen.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.*;

class BusinessTimeTest {
    @Test
    void currentBusinessDateDoesNotDependOnJvmTimezone() {
        var before = LocalDateTime.now(BusinessTime.ZONE).minusSeconds(1);
        var value = BusinessTime.now();
        assertFalse(value.isBefore(before));
        assertFalse(value.isAfter(LocalDateTime.now(BusinessTime.ZONE)));
        assertEquals(0, value.getNano() % 1_000_000);
    }

    @Test
    void serializationAndDeadlineParsingReferToTheSameInstant() {
        var local = LocalDateTime.of(2026, 10, 24, 18, 30);
        assertEquals("2026-10-24T18:30+08:00", BusinessTime.atOffset(local).toString());
        assertEquals(
                Instant.parse("2026-10-24T10:30:00Z"), BusinessTime.atOffset(local).toInstant());
        assertEquals(local, Views.deadline("2026-10-24T10:30:00Z"));
    }
}
