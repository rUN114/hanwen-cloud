package org.hanwen.common;

import java.time.LocalDateTime;

public final class ReviewRules {
    public record Schedule(int streak, boolean mastered, LocalDateTime next) {}

    public static Schedule next(int streak, boolean correct, LocalDateTime now) {
        if (streak < 0) throw ApiException.bad("复习记录无效");
        // 答错后十分钟再复习；连续答对按 1、3、7 天安排，第三次标记为已掌握。
        int next = correct ? Math.min(3, streak + 1) : 0;
        return new Schedule(
                next,
                next == 3,
                correct ? now.plusDays(next == 1 ? 1 : next == 2 ? 3 : 7) : now.plusMinutes(10));
    }

    private ReviewRules() {}
}
