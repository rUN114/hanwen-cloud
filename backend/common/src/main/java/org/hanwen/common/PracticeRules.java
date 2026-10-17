package org.hanwen.common;

import java.time.LocalDateTime;

public final class PracticeRules {
    public static void available(Contracts.QuizSnapshot q, LocalDateTime now) {
        if (!q.status().equals("PUBLISHED") || !q.courseStatus().equals("PUBLISHED"))
            throw ApiException.conflict("练习或课程已归档，不能开始或提交");
        if (q.opensAt() != null && now.isBefore(q.opensAt())) throw ApiException.conflict("练习尚未开放");
        if (q.closesAt() != null && !now.isBefore(q.closesAt()))
            throw ApiException.conflict("练习已截止");
    }

    public static LocalDateTime deadline(Contracts.QuizSnapshot q, LocalDateTime now) {
        var due = q.timeLimitMinutes() == 0 ? null : now.plusMinutes(q.timeLimitMinutes());
        return q.closesAt() != null && (due == null || q.closesAt().isBefore(due))
                ? q.closesAt()
                : due;
    }

    public static boolean expired(LocalDateTime due, LocalDateTime now) {
        return due != null && !now.isBefore(due);
    }
}
