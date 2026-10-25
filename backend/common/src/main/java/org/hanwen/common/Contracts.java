package org.hanwen.common;

public final class Contracts {
    public record QuizBrief(long id, String title, java.time.LocalDateTime closesAt) {}

    public record ScoreBrief(
            long studentId,
            long quizId,
            int score,
            int total,
            long attemptId,
            String gradingStatus) {}

    public record GradeFeed(java.util.List<Long> students, java.util.List<ScoreBrief> attempts) {}

    public record StudentAccount(long id, String username, String displayName, String status) {}

    public record ChapterText(long courseId, long chapterId, String content, String status) {}

    public record QuizChoice(Long questionId, String choice) {}

    public record QuizAnswers(java.util.List<QuizChoice> answers, Long runId) {}

    public record QuizQuestionSnapshot(
            long questionId,
            String prompt,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            String correctOption,
            String explanation,
            String questionType,
            int points,
            String knowledgePoint) {
        public QuizQuestionSnapshot(
                long questionId,
                String prompt,
                String optionA,
                String optionB,
                String optionC,
                String optionD,
                String correctOption,
                String explanation,
                String questionType,
                int points) {
            this(
                    questionId,
                    prompt,
                    optionA,
                    optionB,
                    optionC,
                    optionD,
                    correctOption,
                    explanation,
                    questionType,
                    points,
                    null);
        }
    }

    public record QuizRelease(
            long quizId,
            String status,
            String courseStatus,
            java.time.LocalDateTime closesAt,
            int maxAttempts) {}

    public record QuizSnapshot(
            long quizId,
            long courseId,
            long chapterId,
            int version,
            String status,
            String courseStatus,
            java.time.LocalDateTime opensAt,
            java.time.LocalDateTime closesAt,
            int timeLimitMinutes,
            int maxAttempts,
            java.util.List<QuizQuestionSnapshot> questions) {}

    public record QuizItem(
            long questionId,
            String prompt,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            String selectedOption,
            String correctOption,
            String explanation,
            Boolean correct,
            String questionType,
            int points,
            String knowledgePoint,
            Integer awardedPoints) {}

    public record QuizResult(
            long quizId,
            long courseId,
            long chapterId,
            int score,
            int total,
            java.util.List<QuizItem> items) {}

    public record PublicUser(long id, String displayName) {}

    public record WorkBrief(
            long id, String title, String author, String dynasty, String genre, String status) {}

    public record AuthState(long id, String role, String status, int authVersion) {}

    public record CourseValidation(
            long id,
            long teacherId,
            String status,
            int chapterCount,
            String name,
            String accessRole) {
        public boolean canTeach() {
            return "OWNER".equals(accessRole) || "CO_TEACHER".equals(accessRole);
        }

        public boolean canGrade() {
            return canTeach() || "ASSISTANT".equals(accessRole);
        }

        public boolean principal() {
            return "OWNER".equals(accessRole);
        }
    }

    public record ChapterValidation(long chapterId, long courseId, String courseStatus) {}

    public record Exists(boolean exists) {}

    public record HasData(boolean hasData) {}
}
