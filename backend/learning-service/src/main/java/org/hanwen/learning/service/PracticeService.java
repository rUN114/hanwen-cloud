package org.hanwen.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.common.*;
import org.hanwen.learning.client.CourseClient;
import org.hanwen.learning.mapper.*;
import org.hanwen.learning.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

@Service
public class PracticeService {
    final QuizAttemptMapper attempts;
    final AttemptItemMapper items;
    final QuizRunMapper runs;
    final RunQuestionMapper runQuestions;
    final EnrollmentMapper enrollments;
    final CourseClient courses;
    final DirectoryClient directory;
    final Remote remote;
    final ReviewCardMapper reviewCards;
    final TransactionTemplate transaction;

    public PracticeService(
            QuizAttemptMapper a,
            AttemptItemMapper i,
            QuizRunMapper r,
            RunQuestionMapper rq,
            EnrollmentMapper e,
            CourseClient c,
            DirectoryClient d,
            Remote remote,
            ReviewCardMapper reviewCards,
            PlatformTransactionManager manager) {
        attempts = a;
        items = i;
        runs = r;
        runQuestions = rq;
        enrollments = e;
        courses = c;
        directory = d;
        this.remote = remote;
        this.reviewCards = reviewCards;
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public record Draft(List<Contracts.QuizChoice> answers, Integer expectedVersion) {}

    Contracts.QuizSnapshot snapshot(long quiz) {
        Views.role("STUDENT");
        return remote.call("practice-snapshot", () -> courses.snapshot(quiz));
    }

    QueryWrapper<QuizRun> mineRuns(long quiz) {
        return new QueryWrapper<QuizRun>()
                .eq("quiz_id", quiz)
                .eq("student_id", Actor.current().id());
    }

    QueryWrapper<QuizAttempt> mineAttempts(long quiz) {
        return new QueryWrapper<QuizAttempt>()
                .eq("quiz_id", quiz)
                .eq("student_id", Actor.current().id());
    }

    List<RunQuestion> questions(long run) {
        return runQuestions.selectList(
                new QueryWrapper<RunQuestion>().eq("run_id", run).orderByAsc("id"));
    }

    QuizRun ownedRun(long quiz, long id, boolean lock) {
        Views.role("STUDENT");
        var r = lock ? runs.lock(id) : runs.selectById(id);
        if (r == null || r.quizId != quiz) throw ApiException.missing();
        if (r.studentId != Actor.current().id()) throw ApiException.forbidden();
        return r;
    }

    long used(long quiz) {
        return runs.selectCount(mineRuns(quiz))
                + attempts.selectCount(mineAttempts(quiz).isNull("run_id"));
    }

    QuizRun latestRun(long quiz) {
        var list = runs.selectList(mineRuns(quiz).orderByDesc("id").last("LIMIT 1"));
        return list.isEmpty() ? null : list.getFirst();
    }

    void expire(QuizRun r) {
        if (r != null
                && r.status.equals("ACTIVE")
                && PracticeRules.expired(r.deadlineAt, Views.now())) {
            r.status = "EXPIRED";
            r.version++;
            runs.updateById(r);
        }
    }

    private boolean released(
            Contracts.QuizRelease q, long used, QuizRun last, java.time.LocalDateTime now) {
        return !q.status().equals("PUBLISHED")
                || !q.courseStatus().equals("PUBLISHED")
                || PracticeRules.expired(q.closesAt(), now)
                || used >= q.maxAttempts()
                        && (last == null
                                || !last.status.equals("ACTIVE")
                                || PracticeRules.expired(last.deadlineAt, now));
    }

    boolean released(Contracts.QuizSnapshot q) {
        return released(
                new Contracts.QuizRelease(
                        q.quizId(), q.status(), q.courseStatus(), q.closesAt(), q.maxAttempts()),
                used(q.quizId()),
                latestRun(q.quizId()),
                Views.now());
    }

    public boolean reviewReleased(long quiz) {
        return released(snapshot(quiz));
    }

    Map<Long, Boolean> reviewReleased(Collection<Long> quizzes) {
        Views.role("STUDENT");
        var ids = quizzes.stream().distinct().toList();
        var allowed = new HashMap<Long, Boolean>();
        var now = Views.now();
        for (int offset = 0; offset < ids.size(); offset += 100) {
            var batch = ids.subList(offset, Math.min(offset + 100, ids.size()));
            String query =
                    batch.stream()
                            .map(Object::toString)
                            .collect(java.util.stream.Collectors.joining(","));
            var metadata = remote.call("practice-releases", () -> courses.releases(query));
            var used = new HashMap<Long, Long>();
            var latestIds = new HashMap<Long, Long>();
            runs.selectMaps(
                            new QueryWrapper<QuizRun>()
                                    .select("quiz_id", "COUNT(*) AS n", "MAX(id) AS latest")
                                    .eq("student_id", Actor.current().id())
                                    .in("quiz_id", batch)
                                    .groupBy("quiz_id"))
                    .forEach(
                            row -> {
                                long quiz = ((Number) row.get("quiz_id")).longValue();
                                used.put(quiz, ((Number) row.get("n")).longValue());
                                latestIds.put(quiz, ((Number) row.get("latest")).longValue());
                            });
            attempts.selectMaps(
                            new QueryWrapper<QuizAttempt>()
                                    .select("quiz_id", "COUNT(*) AS n")
                                    .eq("student_id", Actor.current().id())
                                    .in("quiz_id", batch)
                                    .isNull("run_id")
                                    .groupBy("quiz_id"))
                    .forEach(
                            row ->
                                    used.merge(
                                            ((Number) row.get("quiz_id")).longValue(),
                                            ((Number) row.get("n")).longValue(),
                                            Long::sum));
            var last = new HashMap<Long, QuizRun>();
            if (!latestIds.isEmpty())
                runs.selectByIds(latestIds.values()).forEach(run -> last.put(run.quizId, run));
            for (var q : metadata)
                allowed.put(
                        q.quizId(),
                        released(q, used.getOrDefault(q.quizId(), 0L), last.get(q.quizId()), now));
            if (!allowed.keySet().containsAll(batch)) throw ApiException.missing();
        }
        return allowed;
    }

    Map<String, Object> runView(QuizRun r) {
        var v =
                Views.map(
                        "id",
                        r.id,
                        "quizId",
                        r.quizId,
                        "courseId",
                        r.courseId,
                        "chapterId",
                        r.chapterId,
                        "status",
                        r.status,
                        "attemptNo",
                        r.attemptNo,
                        "quizVersion",
                        r.quizVersion,
                        "version",
                        r.version,
                        "startedAt",
                        r.startedAt,
                        "deadlineAt",
                        r.deadlineAt,
                        "serverTime",
                        Views.now());
        if (r.status.equals("ACTIVE"))
            v.put(
                    "questions",
                    questions(r.id).stream()
                            .map(
                                    q ->
                                            Views.map(
                                                    "id",
                                                    q.questionId,
                                                    "prompt",
                                                    q.prompt,
                                                    "optionA",
                                                    q.optionA,
                                                    "optionB",
                                                    q.optionB,
                                                    "optionC",
                                                    q.optionC,
                                                    "optionD",
                                                    q.optionD,
                                                    "questionType",
                                                    q.questionType,
                                                    "points",
                                                    q.points,
                                                    "knowledgePoint",
                                                    q.knowledgePoint,
                                                    "selectedOption",
                                                    q.selectedOption))
                            .toList());
        return v;
    }

    Object view(QuizAttempt a, boolean detail, boolean release) {
        if (a == null) return null;
        var v =
                Views.map(
                        "id",
                        a.id,
                        "quizId",
                        a.quizId,
                        "courseId",
                        a.courseId,
                        "chapterId",
                        a.chapterId,
                        "score",
                        "PENDING".equals(a.gradingStatus) ? null : a.score,
                        "total",
                        a.total,
                        "completedAt",
                        a.completedAt,
                        "gradingStatus",
                        a.gradingStatus,
                        "gradingVersion",
                        a.gradingVersion,
                        "gradedAt",
                        a.gradedAt,
                        "runId",
                        a.runId,
                        "attemptNo",
                        a.attemptNo,
                        "quizVersion",
                        a.quizVersion,
                        "status",
                        "SUBMITTED",
                        "answersReleased",
                        release || a.runId == null);
        if (detail)
            v.put(
                    "items",
                    items
                            .selectList(
                                    new QueryWrapper<AttemptItem>()
                                            .eq("attempt_id", a.id)
                                            .orderByAsc("id"))
                            .stream()
                            .map(
                                    i -> {
                                        var row =
                                                Views.map(
                                                        "questionId",
                                                        i.questionId,
                                                        "prompt",
                                                        i.prompt,
                                                        "optionA",
                                                        i.optionA,
                                                        "optionB",
                                                        i.optionB,
                                                        "optionC",
                                                        i.optionC,
                                                        "optionD",
                                                        i.optionD,
                                                        "selectedOption",
                                                        i.selectedOption,
                                                        "questionType",
                                                        i.questionType,
                                                        "points",
                                                        i.points,
                                                        "knowledgePoint",
                                                        i.knowledgePoint);
                                        if (release || a.runId == null)
                                            row.putAll(
                                                    Views.map(
                                                            "correctOption",
                                                            i.correctOption,
                                                            "explanation",
                                                            i.explanation,
                                                            "correct",
                                                            i.correct,
                                                            "awardedPoints",
                                                            QuestionRules.earned(
                                                                    i.questionType,
                                                                    i.correct,
                                                                    i.awardedPoints,
                                                                    i.points),
                                                            "teacherComment",
                                                            i.teacherComment));
                                        return row;
                                    })
                            .toList());
        return v;
    }

    // Remote validation can call back into this service; perform it before acquiring a DB
    // connection.
    public Object state(long quiz) {
        var q = snapshot(quiz);
        return transaction.execute(status -> stateLocal(quiz, q));
    }

    Object stateLocal(long quiz, Contracts.QuizSnapshot q) {
        var last = latestRun(quiz);
        if (last != null) {
            last = ownedRun(quiz, last.id, true);
            expire(last);
        }
        var history = attempts.selectList(mineAttempts(quiz).orderByDesc("id"));
        boolean release = released(q);
        return Views.map(
                "usedAttempts",
                (int) used(quiz),
                "maxAttempts",
                q.maxAttempts(),
                "serverTime",
                Views.now(),
                "active",
                last != null && last.status.equals("ACTIVE") ? runView(last) : null,
                "lastRun",
                last == null ? null : runView(last),
                "answersReleased",
                release,
                "history",
                history.stream().map(a -> view(a, false, release)).toList());
    }

    // Remote validation can call back into this service; perform it before acquiring a DB
    // connection.
    public Object start(long quiz) {
        var q = snapshot(quiz);
        return transaction.execute(status -> startLocal(quiz, q));
    }

    Object startLocal(long quiz, Contracts.QuizSnapshot q) {
        // Serialize concurrent starts for a student's enrolled course, across service instances.
        var e =
                enrollments.selectOne(
                        new QueryWrapper<Enrollment>()
                                .eq("course_id", q.courseId())
                                .eq("student_id", Actor.current().id()));
        if (e == null) throw ApiException.forbidden();
        enrollments.lock(e.id);
        var last = latestRun(quiz);
        if (last != null) {
            last = ownedRun(quiz, last.id, true);
            expire(last);
        }
        PracticeRules.available(q, Views.now());
        if (last != null && last.status.equals("ACTIVE")) return runView(last);
        long used = used(quiz);
        if (used >= q.maxAttempts()) throw ApiException.conflict("尝试次数已用完");
        if (q.questions().isEmpty()) throw ApiException.conflict("练习没有题目");
        var r = new QuizRun();
        r.quizId = quiz;
        r.courseId = q.courseId();
        r.chapterId = q.chapterId();
        r.studentId = Actor.current().id();
        r.quizVersion = q.version();
        r.attemptNo = (int) used + 1;
        r.maxAttempts = q.maxAttempts();
        r.status = "ACTIVE";
        r.startedAt = Views.now();
        r.deadlineAt = PracticeRules.deadline(q, r.startedAt);
        r.version = 0;
        runs.insert(r);
        for (var x : q.questions()) {
            var t = new RunQuestion();
            t.runId = r.id;
            t.questionId = x.questionId();
            t.prompt = x.prompt();
            t.optionA = x.optionA();
            t.optionB = x.optionB();
            t.optionC = x.optionC();
            t.optionD = x.optionD();
            t.correctOption = x.correctOption();
            t.explanation = x.explanation();
            t.questionType = x.questionType();
            t.points = x.points();
            t.knowledgePoint = x.knowledgePoint();
            runQuestions.insert(t);
        }
        return runView(r);
    }

    // Remote validation can call back into this service; perform it before acquiring a DB
    // connection.
    public Object draft(long quiz, long id, Draft in) {
        var current = snapshot(quiz);
        return transaction.execute(status -> draftLocal(quiz, id, in, current));
    }

    Object draftLocal(long quiz, long id, Draft in, Contracts.QuizSnapshot current) {
        var r = ownedRun(quiz, id, true);
        expire(r);
        if (!r.status.equals("ACTIVE")) return runView(r);
        PracticeRules.available(current, Views.now());
        Names.version(r.version, in.expectedVersion());
        if (in.answers() == null || in.answers().size() > 100) throw ApiException.bad("草稿最多100题");
        var answers = new HashMap<Long, String>();
        for (var a : in.answers()) {
            if (a == null || a.questionId() == null || answers.containsKey(a.questionId()))
                throw ApiException.bad("草稿包含重复题目");
            answers.put(a.questionId(), a.choice());
        }
        var rows = questions(r.id);
        var ids = new HashSet<>(rows.stream().map(x -> x.questionId).toList());
        if (!ids.containsAll(answers.keySet())) throw ApiException.bad("草稿包含其他练习题目");
        for (var x : rows) {
            var selected = answers.get(x.questionId);
            x.selectedOption =
                    selected == null || selected.isEmpty()
                            ? null
                            : QuestionRules.choice(x.questionType, selected, false);
            runQuestions.updateById(x);
        }
        r.version++;
        runs.updateById(r);
        return runView(r);
    }

    public Object result(long quiz, Long attemptId) {
        var q = snapshot(quiz);
        QuizAttempt a;
        if (attemptId != null) {
            a = attempts.selectById(attemptId);
            if (a == null || a.quizId != quiz) throw ApiException.missing();
            if (a.studentId != Actor.current().id()) throw ApiException.forbidden();
        } else {
            var list = attempts.selectList(mineAttempts(quiz).orderByDesc("id").last("LIMIT 1"));
            a = list.isEmpty() ? null : list.getFirst();
        }
        return view(a, true, released(q));
    }

    // Remote validation can call back into this service; perform it before acquiring a DB
    // connection.
    public Object submit(long quiz, Contracts.QuizAnswers in) {
        var current = snapshot(quiz);
        return transaction.execute(status -> submitLocal(quiz, in, current));
    }

    Object submitLocal(long quiz, Contracts.QuizAnswers in, Contracts.QuizSnapshot current) {
        Long runId = in.runId();
        if (runId == null) {
            // Compatibility for original untimed, single-attempt clients only.
            if (current.timeLimitMinutes() != 0 || current.maxAttempts() != 1)
                throw ApiException.bad("请先开始测验，并提交本次runId");
            var prior = attempts.selectList(mineAttempts(quiz).orderByDesc("id").last("LIMIT 1"));
            if (!prior.isEmpty()) return view(prior.getFirst(), true, released(current));
            startLocal(quiz, current);
            runId = latestRun(quiz).id;
        }
        var r = ownedRun(quiz, runId, true);
        if (r.status.equals("SUBMITTED"))
            return view(
                    attempts.selectOne(new QueryWrapper<QuizAttempt>().eq("run_id", r.id)),
                    true,
                    released(current));
        expire(r);
        if (r.status.equals("EXPIRED")) return runView(r);
        PracticeRules.available(current, Views.now());
        var frozen =
                new Contracts.QuizSnapshot(
                        quiz,
                        r.courseId,
                        r.chapterId,
                        r.quizVersion,
                        "PUBLISHED",
                        "PUBLISHED",
                        null,
                        null,
                        0,
                        r.maxAttempts,
                        questions(r.id).stream()
                                .map(
                                        x ->
                                                new Contracts.QuizQuestionSnapshot(
                                                        x.questionId,
                                                        x.prompt,
                                                        x.optionA,
                                                        x.optionB,
                                                        x.optionC,
                                                        x.optionD,
                                                        x.correctOption,
                                                        x.explanation,
                                                        x.questionType,
                                                        x.points,
                                                        x.knowledgePoint))
                                .toList());
        var result = QuestionRules.grade(frozen, in);
        var a = new QuizAttempt();
        a.quizId = quiz;
        a.courseId = r.courseId;
        a.chapterId = r.chapterId;
        a.studentId = r.studentId;
        a.runId = r.id;
        a.attemptNo = r.attemptNo;
        a.quizVersion = r.quizVersion;
        a.score = result.score();
        a.total = result.total();
        a.completedAt = Views.now();
        a.gradingStatus =
                result.items().stream().anyMatch(x -> QuestionRules.manual(x.questionType()))
                        ? "PENDING"
                        : "GRADED";
        a.gradingVersion = 0;
        attempts.insert(a);
        for (var x : result.items()) {
            var t = new AttemptItem();
            t.attemptId = a.id;
            t.questionId = x.questionId();
            t.prompt = x.prompt();
            t.optionA = x.optionA();
            t.optionB = x.optionB();
            t.optionC = x.optionC();
            t.optionD = x.optionD();
            t.selectedOption = x.selectedOption();
            t.correctOption = x.correctOption();
            t.explanation = x.explanation();
            t.correct = x.correct();
            t.questionType = x.questionType();
            t.points = x.points();
            t.knowledgePoint = x.knowledgePoint();
            t.awardedPoints = x.awardedPoints();
            items.insert(t);
            if (Boolean.FALSE.equals(t.correct) && !QuestionRules.manual(t.questionType)) {
                var card = new ReviewCard();
                card.studentId = a.studentId;
                card.courseId = a.courseId;
                card.quizId = a.quizId;
                card.questionId = t.questionId;
                card.quizVersion = a.quizVersion;
                card.attemptItemId = t.id;
                card.lastWrongAt = a.completedAt;
                card.nextReviewAt = a.completedAt;
                reviewCards.mistake(card);
            }
        }
        r.status = "SUBMITTED";
        r.version++;
        runs.updateById(r);
        return view(a, true, released(current));
    }

    public Object courseResults(long course, int page, int size) {
        Views.role("TEACHER");
        var c = remote.call("practice-course", () -> courses.course(course));
        if (!c.canGrade()) throw ApiException.forbidden();
        int offset = Views.offset(page, size);
        var w = new QueryWrapper<QuizAttempt>().eq("course_id", course);
        long total = attempts.selectCount(w);
        var rows =
                attempts.selectList(
                        w.orderByDesc("id").last("LIMIT " + size + " OFFSET " + offset));
        var names = Names.read(rows.stream().map(a -> a.studentId).toList(), directory, remote);
        return Views.page(
                rows.stream()
                        .map(
                                a ->
                                        Views.map(
                                                "id",
                                                a.id,
                                                "quizId",
                                                a.quizId,
                                                "studentId",
                                                a.studentId,
                                                "displayName",
                                                names.getOrDefault(a.studentId, "学生"),
                                                "score",
                                                "PENDING".equals(a.gradingStatus) ? null : a.score,
                                                "total",
                                                a.total,
                                                "attemptNo",
                                                a.attemptNo,
                                                "quizVersion",
                                                a.quizVersion,
                                                "gradingStatus",
                                                a.gradingStatus,
                                                "gradingVersion",
                                                a.gradingVersion,
                                                "completedAt",
                                                a.completedAt))
                        .toList(),
                page,
                size,
                total);
    }
}
