package org.hanwen.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

class ViewsTest {
    @Test
    void paginationTotalRemainsNumericWhileIdsRemainStrings() {
        assertInstanceOf(Number.class, Views.page(List.of(), 1, 10, 1L).get("total"));
        assertEquals("9007199254740993", Views.map("id", 9007199254740993L).get("id"));
    }

    @Test
    void scoreRejectsOverflowPrecisionAndNegative() {
        for (String value : List.of("-1", "100.01", "83.001", "1e2"))
            assertThrows(
                    ApiException.class, () -> Views.score(value, new BigDecimal("100"), false));
        assertEquals(new BigDecimal("0.00"), Views.score("0", new BigDecimal("100"), false));
        assertThrows(ApiException.class, () -> Views.score("0", new BigDecimal("100"), true));
    }

    @Test
    void supplementaryCharactersCountAsOneCharacter() {
        assertEquals("𠀀文", Views.text(" 𠀀文 ", 2, "正文"));
        assertThrows(ApiException.class, () -> Views.text("𠀀文字", 2, "正文"));
    }

    @Test
    void deadlineNormalizesOffsetAndRejectsMissingZone() {
        assertEquals("2026-10-09T18:00", Views.deadline("2026-10-09T10:00:00Z").toString());
        assertThrows(ApiException.class, () -> Views.deadline("2026-10-09T10:00:00"));
    }
}
