package org.hanwen.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.*;
import java.util.*;

public class GradeRulesTest {
    @Test
    void pendingNeverBecomesZero() {
        var r =
                GradeRules.calculate(
                        List.of(
                                new GradeRules.Item("ASSIGNMENT", "1", "习作", "PENDING", null),
                                new GradeRules.Item(
                                        "PRACTICE", "2", "练习", "GRADED", new BigDecimal("80"))),
                        70,
                        "ZERO");
        assertNull(r.total());
        assertEquals(1, r.pending());
    }

    @Test
    void missingPolicyAndWeights() {
        var rows =
                List.of(
                        new GradeRules.Item("ASSIGNMENT", "1", "甲", "GRADED", new BigDecimal("80")),
                        new GradeRules.Item("ASSIGNMENT", "2", "乙", "MISSING", null),
                        new GradeRules.Item("PRACTICE", "3", "练习", "GRADED", new BigDecimal("60")));
        assertEquals(new BigDecimal("74.00"), GradeRules.calculate(rows, 70, "EXCLUDE").total());
        assertEquals(new BigDecimal("46.00"), GradeRules.calculate(rows, 70, "ZERO").total());
        assertNull(GradeRules.calculate(List.of(), 70, "EXCLUDE").total());
    }

    @Test
    void zeroWeightCategoryDoesNotBlockTheFinalGrade() {
        var practiceOnly =
                List.of(
                        new GradeRules.Item("ASSIGNMENT", "1", "习作", "PENDING", null),
                        new GradeRules.Item("PRACTICE", "2", "练习", "GRADED", new BigDecimal("80")));
        assertEquals(
                new BigDecimal("80.00"), GradeRules.calculate(practiceOnly, 0, "EXCLUDE").total());

        var assignmentOnly =
                List.of(
                        new GradeRules.Item(
                                "ASSIGNMENT", "1", "习作", "GRADED", new BigDecimal("70")),
                        new GradeRules.Item("PRACTICE", "2", "练习", "PENDING", null));
        assertEquals(
                new BigDecimal("70.00"),
                GradeRules.calculate(assignmentOnly, 100, "EXCLUDE").total());
    }

    @Test
    void pendingWorkKeepsThePreviewButWithholdsTheFinalGrade() {
        var result =
                GradeRules.calculate(
                        List.of(
                                new GradeRules.Item(
                                        "ASSIGNMENT", "1", "甲", "GRADED", new BigDecimal("80")),
                                new GradeRules.Item("ASSIGNMENT", "2", "乙", "PENDING", null),
                                new GradeRules.Item(
                                        "PRACTICE", "3", "练习", "GRADED", new BigDecimal("60"))),
                        70,
                        "EXCLUDE");
        assertEquals(new BigDecimal("74.00"), result.provisional());
        assertNull(result.total());
        assertEquals(1, result.pending());
    }

    @Test
    void rubricIsServerComputed() {
        var c =
                RubricRules.validate(
                        List.of(
                                new RubricRules.Criterion("理解", "60"),
                                new RubricRules.Criterion("表达", "40")),
                        new BigDecimal("100"));
        assertEquals(new BigDecimal("85.00"), RubricRules.score(c, List.of("50", "35")));
        assertThrows(ApiException.class, () -> RubricRules.score(c, List.of("61", "35")));
        assertThrows(
                ApiException.class,
                () ->
                        RubricRules.validate(
                                List.of(new RubricRules.Criterion("理解", "60")),
                                new BigDecimal("100")));
    }
}
