package org.hanwen.learning.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;

class InsightsServiceTest {
    @Test
    void databaseValuesKeepTheirApiTypesAndPrecision() {
        var time = LocalDateTime.of(2026, 10, 22, 10, 30);
        var row = new LinkedHashMap<String, Object>();
        row.put("studentId", 9007199254740993L);
        row.put("classId", null);
        row.put("averagePercent", new BigDecimal("83.330000"));
        row.put("percent", new BigDecimal("50"));
        row.put("responses", new BigDecimal("3"));
        row.put("joinedAt", time);
        row.put("lastRecordedAt", Timestamp.valueOf(time));
        row.put("label", "已批阅");

        var response = InsightsService.safe(row);

        assertEquals("9007199254740993", response.get("studentId"));
        assertTrue(response.containsKey("classId"));
        assertNull(response.get("classId"));
        assertEquals("83.33", response.get("averagePercent"));
        assertEquals("50.00", response.get("percent"));
        assertEquals(3L, response.get("responses"));
        assertEquals("2026-10-22T10:30+08:00", response.get("joinedAt"));
        assertEquals(response.get("joinedAt"), response.get("lastRecordedAt"));
        assertEquals("已批阅", response.get("label"));
        assertEquals(new BigDecimal("83.330000"), row.get("averagePercent"));
    }
}
