package org.hanwen.common;

import java.math.*;
import java.util.*;

public final class RubricRules {
    public record Criterion(String name, String maxScore) {}

    public static List<Criterion> validate(List<Criterion> criteria, BigDecimal max) {
        if (criteria == null) return List.of();
        if (criteria.size() > 12) throw ApiException.bad("评分细则最多12项");
        var result = new ArrayList<Criterion>();
        BigDecimal sum = BigDecimal.ZERO;
        var names = new HashSet<String>();
        for (var c : criteria) {
            if (c == null) throw ApiException.bad("评分细则不可为空");
            String name = Views.text(c.name(), 80, "评分项名称");
            if (!names.add(name)) throw ApiException.bad("评分项名称不可重复");
            var value = Views.score(c.maxScore(), max, true);
            sum = sum.add(value);
            result.add(new Criterion(name, value.toPlainString()));
        }
        if (!result.isEmpty() && sum.compareTo(max) != 0) throw ApiException.bad("分项满分之和须等于作业满分");
        return result;
    }

    public static BigDecimal score(List<Criterion> criteria, List<String> scores) {
        if (scores == null || scores.size() != criteria.size()) throw ApiException.bad("请填写全部分项得分");
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < scores.size(); i++)
            sum =
                    sum.add(
                            Views.score(
                                    scores.get(i),
                                    new BigDecimal(criteria.get(i).maxScore()),
                                    false));
        return sum;
    }
}
