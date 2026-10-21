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

    @Test
    void missingZeroPolicyCanRenderARepeatingAverage() {
        var result =
                GradeRules.calculate(
                        List.of(
                                new GradeRules.Item(
                                        "ASSIGNMENT", "1", "甲", "GRADED", new BigDecimal("80")),
                                new GradeRules.Item(
                                        "ASSIGNMENT", "2", "乙", "GRADED", new BigDecimal("80")),
                                new GradeRules.Item("ASSIGNMENT", "3", "丙", "MISSING", null)),
                        100,
                        "ZERO");

        var response = Views.map("assignment", result.assignment(), "total", result.total());

        assertEquals("53.33", response.get("assignment"));
        assertEquals("53.33", response.get("total"));
    }

    @Test
    void decimalOutputRoundsHalfUpWithoutChangingTheCalculationValue() {
        var original = new BigDecimal("16.666667");

        var response = Views.map("value", original, "tie", new BigDecimal("2.345"));

        assertEquals("16.67", response.get("value"));
        assertEquals("2.35", response.get("tie"));
        assertEquals(new BigDecimal("16.666667"), original);
    }
}
