package org.hanwen.common;

import java.math.*;
import java.util.*;

public final class GradeRules {
    public record Item(String kind, String id, String title, String status, BigDecimal percent) {}

    public record Result(
            BigDecimal assignment,
            BigDecimal practice,
            BigDecimal provisional,
            BigDecimal total,
            int pending,
            int missing) {}

    public static Result calculate(List<Item> items, int weight, String missingRule) {
        if (weight < 0 || weight > 100 || !Set.of("EXCLUDE", "ZERO").contains(missingRule))
            throw ApiException.bad("总评规则不合法");
        var groups = new HashMap<String, List<BigDecimal>>();
        groups.put("ASSIGNMENT", new ArrayList<>());
        groups.put("PRACTICE", new ArrayList<>());
        int pending = 0, missing = 0;
        for (var i : items) {
            if (!groups.containsKey(i.kind())) throw ApiException.bad("成绩项类别不合法");
            if (i.percent() != null) groups.get(i.kind()).add(i.percent());
            else if (i.status().equals("MISSING")) {
                missing++;
                if (missingRule.equals("ZERO")) groups.get(i.kind()).add(BigDecimal.ZERO);
            } else if (i.kind().equals("ASSIGNMENT") ? weight > 0 : weight < 100) pending++;
        }
        var a = mean(groups.get("ASSIGNMENT"));
        var p = mean(groups.get("PRACTICE"));
        BigDecimal preview = weightedPreview(a, p, weight);
        return new Result(a, p, preview, pending == 0 ? preview : null, pending, missing);
    }

    private static BigDecimal weightedPreview(
            BigDecimal assignment, BigDecimal practice, int assignmentWeight) {
        if (assignmentWeight > 0 && assignment == null) return null;
        int practiceWeight = 100 - assignmentWeight;
        if (practiceWeight > 0 && practice == null) return null;

        BigDecimal weightedTotal = BigDecimal.ZERO;
        if (assignment != null) {
            weightedTotal =
                    weightedTotal.add(assignment.multiply(BigDecimal.valueOf(assignmentWeight)));
        }
        if (practice != null) {
            weightedTotal =
                    weightedTotal.add(practice.multiply(BigDecimal.valueOf(practiceWeight)));
        }
        return weightedTotal.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal mean(List<BigDecimal> values) {
        return values.isEmpty()
                ? null
                : values.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP);
    }

    public static BigDecimal percent(BigDecimal score, BigDecimal max) {
        if (max.signum() <= 0) throw ApiException.bad("满分须大于0");
        return score.multiply(BigDecimal.valueOf(100)).divide(max, 6, RoundingMode.HALF_UP);
    }
}
