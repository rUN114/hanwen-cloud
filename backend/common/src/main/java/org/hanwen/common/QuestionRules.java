package org.hanwen.common;

import java.util.*;

/** Exact-set objective grading; written responses await human marking. */
public final class QuestionRules {
    public static String type(String type) {
        String value = type == null ? "SINGLE" : type;
        if (!Set.of("SINGLE", "MULTIPLE", "TRUE_FALSE", "FILL_BLANK", "SHORT_ANSWER")
                .contains(value)) throw ApiException.bad("题型须为单选、多选、判断、填空或简答");
        return value;
    }

    public static boolean manual(String type) {
        return "FILL_BLANK".equals(type) || "SHORT_ANSWER".equals(type);
    }

    public static Integer earned(String type, Boolean correct, Integer awarded, int maximum) {
        if (awarded != null) return awarded;
        if (manual(type)) return null;
        return Boolean.TRUE.equals(correct) ? maximum : 0;
    }

    public static int awarded(Integer score, int maximum) {
        if (score == null || score < 0 || score > maximum)
            throw ApiException.bad("分项得分须为0至本题满分的整数");
        return score;
    }

    public static int points(Integer points) {
        int value = points == null ? 1 : points;
        if (value < 1 || value > 100) throw ApiException.bad("每题分值须为1至100整数");
        return value;
    }

    public static String choice(String type, String raw, boolean answerKey) {
        type = type(type);
        if (manual(type)) return Views.text(raw, 4000, answerKey ? "参考答案" : "文字作答");
        if (raw == null || raw.isEmpty() || raw.length() > 4) throw ApiException.bad("请选择有效答案");
        var allowed = type.equals("TRUE_FALSE") ? "AB" : "ABCD";
        var choices = new TreeSet<Character>();
        for (char c : raw.toCharArray()) {
            if (allowed.indexOf(c) < 0 || !choices.add(c)) throw ApiException.bad("答案包含重复或无效选项");
        }
        if (!type.equals("MULTIPLE") && choices.size() != 1
                || type.equals("MULTIPLE") && answerKey && choices.size() < 2)
            throw ApiException.bad("单选/判断须选一项，多选正确答案至少两项");
        var value = new StringBuilder();
        choices.forEach(value::append);
        return value.toString();
    }

    public static Contracts.QuizResult grade(
            Contracts.QuizSnapshot snapshot, Contracts.QuizAnswers in) {
        var list = snapshot.questions();
        if (in.answers() == null || in.answers().size() != list.size() || list.isEmpty())
            throw ApiException.bad("请完成全部题目，每题提交一次");
        Map<Long, String> answers = new HashMap<>();
        for (var a : in.answers()) {
            if (a == null || a.questionId() == null || answers.containsKey(a.questionId()))
                throw ApiException.bad("答案包含重复题目");
            answers.put(a.questionId(), a.choice());
        }
        int score = 0, total = 0;
        var result = new ArrayList<Contracts.QuizItem>();
        for (var q : list) {
            String selected = choice(q.questionType(), answers.get(q.questionId()), false);
            Boolean correct = manual(q.questionType()) ? null : selected.equals(q.correctOption());
            int points = points(q.points());
            total += points;
            if (Boolean.TRUE.equals(correct)) score += points;
            result.add(
                    new Contracts.QuizItem(
                            q.questionId(),
                            q.prompt(),
                            q.optionA(),
                            q.optionB(),
                            q.optionC(),
                            q.optionD(),
                            selected,
                            q.correctOption(),
                            q.explanation(),
                            correct,
                            q.questionType(),
                            points,
                            q.knowledgePoint(),
                            correct == null ? null : correct ? points : 0));
        }
        return new Contracts.QuizResult(
                snapshot.quizId(), snapshot.courseId(), snapshot.chapterId(), score, total, result);
    }
}
