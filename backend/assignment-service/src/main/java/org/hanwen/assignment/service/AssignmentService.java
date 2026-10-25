package org.hanwen.assignment.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.assignment.client.CourseClient;
import org.hanwen.assignment.client.LearningClient;
import org.hanwen.assignment.mapper.*;
import org.hanwen.assignment.model.*;
import org.hanwen.common.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class AssignmentService {
    final TaskMapper tasks;
    final SubmissionMapper submissions;
    final GradeMapper grades;
    final CourseClient courses;
    final LearningClient learning;
    final Remote remote;
    final DraftMapper drafts;
    final GradeHistoryMapper history;
    final DirectoryClient directory;
    final TransactionTemplate transaction;

    public record Create(
            Long courseId,
            String title,
            String description,
            String maxScore,
            String deadlineAt,
            List<RubricRules.Criterion> rubric) {}

    public record Write(
            String title,
            String description,
            String maxScore,
            String deadlineAt,
            List<RubricRules.Criterion> rubric) {}

    public record Answer(String answerText) {}

    public record Modify(String answerText, Integer expectedVersion) {}

    public record Mark(
            String score, String comment, Integer expectedVersion, List<String> rubricScores) {}

    // Access through methods so Spring's transactional proxy delegates to the initialized target.
    public TaskMapper tasks() {
        return tasks;
    }

    public SubmissionMapper submissions() {
        return submissions;
    }

    public GradeMapper grades() {
        return grades;
    }

    public CourseClient courses() {
        return courses;
    }

    public LearningClient learning() {
        return learning;
    }

    public Remote remote() {
        return remote;
    }

    public DirectoryClient directory() {
        return directory;
    }

    public AssignmentService(
            TaskMapper t,
            SubmissionMapper s,
            GradeMapper g,
            CourseClient c,
            LearningClient l,
            Remote r,
            DraftMapper d,
            GradeHistoryMapper h,
            DirectoryClient directory,
            PlatformTransactionManager manager) {
        tasks = t;
        submissions = s;
        grades = g;
        courses = c;
        learning = l;
        remote = r;
        drafts = d;
        history = h;
        this.directory = directory;
        transaction = new TransactionTemplate(manager);
    }

    public Task task(long id) {
        Task t = tasks.selectById(id);
        if (t == null) throw ApiException.missing();
        return t;
    }

    Task lockedTask(long id) {
        Task t = tasks.lock(id);
        if (t == null) throw ApiException.missing();
        return t;
    }

    Submission submission(long id) {
        Submission s = submissions.selectById(id);
        if (s == null) throw ApiException.missing();
        return s;
    }

    Contracts.CourseValidation course(long id) {
        return remote.call("assignment-course", () -> courses.course(id));
    }

    void owner(Task t) {
        Views.role("TEACHER");
        if (!course(t.courseId).canGrade()) throw ApiException.forbidden();
    }

    void editor(Task t) {
        Views.role("TEACHER");
        if (!course(t.courseId).canTeach()) throw ApiException.forbidden();
    }

    public void student(Task t, boolean writing) {
        Views.role("STUDENT");
        var c = course(t.courseId);
        if (t.status.equals("DRAFT") || c.status().equals("DRAFT")) throw ApiException.forbidden();
        if (!remote.call(
                        "assignment-enrollment",
                        () -> learning.exists(t.courseId, Actor.current().id()))
                .exists()) throw ApiException.forbidden();
        if (writing && !c.status().equals("PUBLISHED"))
            throw ApiException.conflict("课程已归档，不能提交或修改答案");
    }

    void readable(Task t) {
        if (Actor.current().role().equals("TEACHER")) owner(t);
        else student(t, false);
    }

    public void writable(Task t) {
        if (!t.status.equals("PUBLISHED")) throw ApiException.conflict("作业未发布或已关闭");
        if (t.deadlineAt != null && !Views.now().isBefore(t.deadlineAt))
            throw ApiException.conflict("作业已截止");
    }

    public static Map<String, Object> taskView(Task t) {
        return Views.map(
                "id",
                t.id,
                "courseId",
                t.courseId,
                "teacherId",
                t.teacherId,
                "title",
                t.title,
                "description",
                t.description,
                "maxScore",
                t.maxScore,
                "deadlineAt",
                t.deadlineAt,
                "status",
                t.status,
                "updatedAt",
                t.updatedAt,
                "rubric",
                Rubrics.read(t.rubricJson));
    }

    static Map<String, Object> submissionView(Submission s, boolean answer) {
        var v =
                Views.map(
                        "id",
                        s.id,
                        "assignmentId",
                        s.assignmentId,
                        "studentId",
                        s.studentId,
                        "status",
                        s.status,
                        "submittedAt",
                        s.submittedAt,
                        "version",
                        s.version);
        if (answer) v.put("answerText", s.answerText);
        return v;
    }

    static Object gradeView(Grade g, int version) {
        return g == null
                ? null
                : Views.map(
                        "submissionId",
                        g.submissionId,
                        "teacherId",
                        g.teacherId,
                        "score",
                        g.score,
                        "comment",
                        g.comment,
                        "rubricScores",
                        Rubrics.scores(g.rubricScores),
                        "gradedAt",
                        g.gradedAt,
                        "submissionVersion",
                        version);
    }

    Grade grade(long id) {
        return grades.selectOne(new QueryWrapper<Grade>().eq("submission_id", id));
    }

    public Object detail(long id) {
        Task t = task(id);
        readable(t);
        return taskView(t);
    }

    public Object list(long courseId, int page, int size) {
        int offset = Views.offset(page, size);
        var c = course(courseId);
        Actor a = Actor.current();
        var q = new QueryWrapper<Task>().eq("course_id", courseId);
        if (a.role().equals("TEACHER")) {
            if (!c.canGrade()) throw ApiException.forbidden();
        } else if (a.role().equals("STUDENT")) {
            if (!remote.call("assignment-enrollment", () -> learning.exists(courseId, a.id()))
                    .exists()) throw ApiException.forbidden();
            q.ne("status", "DRAFT");
        } else throw ApiException.forbidden();
        long total = tasks.selectCount(q);
        return Views.page(
                tasks
                        .selectList(q.orderByDesc("id").last("LIMIT " + size + " OFFSET " + offset))
                        .stream()
                        .map(AssignmentService::taskView)
                        .toList(),
                page,
                size,
                total);
    }

    void fields(Task t, Write in) {
        t.title = Views.text(in.title(), 100, "作业标题");
        t.description = Views.text(in.description(), 10000, "作业题目");
        t.maxScore = Views.score(in.maxScore(), new BigDecimal("100"), true);
        t.deadlineAt = Views.deadline(in.deadlineAt());
        t.rubricJson = Rubrics.write(RubricRules.validate(in.rubric(), t.maxScore));
        t.updatedAt = Views.now();
    }

    @Transactional
    public Object create(Create in) {
        Views.role("TEACHER");
        if (in.courseId() == null || in.courseId() < 1) throw ApiException.bad("请选择课程");
        var c = course(in.courseId());
        if (!c.canTeach()) throw ApiException.forbidden();
        if (!c.status().equals("PUBLISHED")) throw ApiException.conflict("请先发布课程");
        Task t = new Task();
        fields(
                t,
                new Write(
                        in.title(), in.description(), in.maxScore(), in.deadlineAt(), in.rubric()));
        t.courseId = in.courseId();
        t.teacherId = Actor.current().id();
        t.status = "DRAFT";
        t.createdAt = t.updatedAt;
        tasks.insert(t);
        return taskView(t);
    }

    public Object update(long id, Write in) {
        Task initial = task(id);
        var c = course(initial.courseId);
        Views.role("TEACHER");
        if (!c.canTeach()) throw ApiException.forbidden();
        if (!c.status().equals("PUBLISHED")) throw ApiException.conflict("归档课程不能修改作业");
        return transaction.execute(
                _ -> {
                    Task t = lockedTask(id);
                    if (!t.courseId.equals(initial.courseId))
                        throw ApiException.conflict("所属课程已改变，请刷新");
                    if (!t.status.equals("DRAFT")) throw ApiException.conflict("已发布作业的题目、满分和截止已冻结");
                    fields(t, in);
                    tasks.updateById(t);
                    return taskView(t);
                });
    }

    public Object transition(long id, boolean publish) {
        Task initial = task(id);
        var c = course(initial.courseId);
        Views.role("TEACHER");
        if (!c.canTeach()) throw ApiException.forbidden();
        if (!c.status().equals("PUBLISHED")) throw ApiException.conflict("归档课程不能修改作业状态");
        return transaction.execute(
                _ -> {
                    Task t = lockedTask(id);
                    if (!t.courseId.equals(initial.courseId))
                        throw ApiException.conflict("所属课程已改变，请刷新");
                    return transitionLocked(t, publish);
                });
    }

    // 调用方已核对课程权限，并持有该作业行锁；批量操作复用同一课程快照。
    Object transitionLocked(Task t, boolean publish) {
        if (!t.status.equals(publish ? "DRAFT" : "PUBLISHED"))
            throw ApiException.conflict("作业状态已改变，请刷新");
        if (publish && t.deadlineAt != null && !t.deadlineAt.isAfter(Views.now()))
            throw ApiException.bad("发布时截止时间须在未来");
        t.status = publish ? "PUBLISHED" : "CLOSED";
        t.updatedAt = Views.now();
        tasks.updateById(t);
        return taskView(t);
    }

    @Transactional
    public Object submit(long id, Answer in) {
        Task initial = task(id);
        student(initial, true);
        Task t = lockedTask(id);
        writable(t);
        String content = Views.text(in.answerText(), 10000, "答案");
        // 已持有作业锁；使用当前读，避免等待锁之前建立的快照漏掉并发提交。
        var existing =
                submissions.selectOne(
                        new QueryWrapper<Submission>()
                                .eq("assignment_id", id)
                                .eq("student_id", Actor.current().id())
                                .last("FOR UPDATE"));
        if (existing != null) {
            if (!existing.answerText.equals(content))
                throw new ApiException(409, "SUBMISSION_EXISTS", "作业已提交，修改答案请使用修改功能");
            return submissionView(existing, true);
        }
        Submission s = new Submission();
        s.assignmentId = id;
        s.studentId = Actor.current().id();
        s.answerText = content;
        s.status = "SUBMITTED";
        s.version = 0;
        s.submittedAt = s.createdAt = s.updatedAt = Views.now();
        submissions.insert(s);
        drafts.delete(
                new QueryWrapper<Draft>()
                        .eq("assignment_id", id)
                        .eq("student_id", Actor.current().id()));
        return submissionView(s, true);
    }

    void version(Submission s, Integer expected) {
        if (expected == null || expected < 0) throw ApiException.bad("缺少有效的expectedVersion");
        if (!s.version.equals(expected))
            throw new ApiException(409, "VERSION_CONFLICT", "答案或评分已改变，请重新查看后再操作");
    }

    @Transactional
    public Object modify(long id, Modify in) {
        Submission initial = submission(id);
        if (initial.studentId != Actor.current().id()) throw ApiException.forbidden();
        student(task(initial.assignmentId), true);
        Task t = lockedTask(initial.assignmentId);
        Submission s = submissions.lock(id);
        writable(t);
        version(s, in.expectedVersion());
        if (!s.status.equals("SUBMITTED")) throw ApiException.conflict("已评分答案不能修改");
        s.answerText = Views.text(in.answerText(), 10000, "答案");
        s.version++;
        s.submittedAt = s.updatedAt = Views.now();
        submissions.updateById(s);
        drafts.delete(
                new QueryWrapper<Draft>()
                        .eq("assignment_id", s.assignmentId)
                        .eq("student_id", s.studentId));
        return submissionView(s, true);
    }

    public Object submissions(long id, int page, int size, String status) {
        Task t = task(id);
        owner(t);
        int offset = Views.offset(page, size);
        var q = new QueryWrapper<Submission>().eq("assignment_id", id);
        if (status != null && !status.isBlank()) {
            if (!Set.of("SUBMITTED", "GRADED").contains(status)) throw ApiException.bad("提交状态不合法");
            q.eq("status", status);
        }
        long total = submissions.selectCount(q);
        var rows =
                submissions.selectList(
                        q.orderByDesc("id").last("LIMIT " + size + " OFFSET " + offset));
        var names = Names.read(rows.stream().map(s -> s.studentId).toList(), directory, remote);
        return Views.page(
                rows.stream()
                        .map(
                                s -> {
                                    var view = submissionView(s, false);
                                    view.put("studentName", names.getOrDefault(s.studentId, "学生"));
                                    return view;
                                })
                        .toList(),
                page,
                size,
                total);
    }

    public Object submissionDetail(long id) {
        Submission s = submission(id);
        Task t = task(s.assignmentId);
        if (Actor.current().role().equals("TEACHER")) owner(t);
        else {
            Views.role("STUDENT");
            if (s.studentId != Actor.current().id()) throw ApiException.forbidden();
            student(t, false);
        }
        var v = submissionView(s, true);
        v.put(
                "studentName",
                Names.read(List.of(s.studentId), directory, remote)
                        .getOrDefault(s.studentId, "学生"));
        v.put("taskTitle", t.title);
        v.put("maxScore", t.maxScore.setScale(2, RoundingMode.UNNECESSARY).toPlainString());
        v.put("grade", gradeView(grade(s.id), s.version));
        v.put("rubric", Rubrics.read(t.rubricJson));
        v.put("courseId", t.courseId.toString());
        return v;
    }

    public Object mark(long id, Mark in) {
        Submission initial = submission(id);
        Task snapshot = task(initial.assignmentId);
        owner(snapshot);
        return transaction.execute(_ -> markLocal(id, in, snapshot));
    }

    private Object markLocal(long id, Mark in, Task snapshot) {
        Task t = lockedTask(snapshot.id);
        if (!t.courseId.equals(snapshot.courseId)) throw ApiException.conflict("所属课程已改变，请刷新");
        Submission s = submissions.lock(id);
        if (s == null) throw ApiException.missing();
        if (!s.assignmentId.equals(t.id)) throw ApiException.conflict("所属作业已改变，请刷新");
        version(s, in.expectedVersion());
        var criteria = Rubrics.read(t.rubricJson);
        BigDecimal score =
                criteria.isEmpty()
                        ? Views.score(in.score(), t.maxScore, false)
                        : RubricRules.score(criteria, in.rubricScores());
        if (criteria.isEmpty() && in.rubricScores() != null && !in.rubricScores().isEmpty())
            throw ApiException.bad("本作业未设置分项评分");
        String comment = Views.optional(in.comment(), 2000, "评语");
        Grade g = grade(id);
        boolean newGrade = g == null;
        if (newGrade) {
            g = new Grade();
            g.submissionId = id;
            g.createdAt = Views.now();
        }
        g.teacherId = Actor.current().id();
        g.score = score;
        g.comment = comment;
        g.rubricScores = criteria.isEmpty() ? null : Rubrics.write(in.rubricScores());
        g.gradedAt = g.updatedAt = Views.now();
        if (newGrade) grades.insert(g);
        else grades.updateById(g);
        s.status = "GRADED";
        s.version++;
        s.updatedAt = Views.now();
        submissions.updateById(s);
        GradeHistory h = new GradeHistory();
        h.submissionId = s.id;
        h.teacherId = g.teacherId;
        h.submissionVersion = s.version;
        h.score = g.score;
        h.comment = g.comment;
        h.rubricScores = g.rubricScores;
        h.gradedAt = g.gradedAt;
        history.insert(h);
        drafts.delete(
                new QueryWrapper<Draft>()
                        .eq("assignment_id", s.assignmentId)
                        .eq("student_id", s.studentId));
        return gradeView(g, s.version);
    }

    public Object mine(int page, int size, Long courseId, Long assignmentId) {
        Views.role("STUDENT");
        int offset = Views.offset(page, size);
        var q = new QueryWrapper<Submission>().eq("student_id", Actor.current().id());
        if (assignmentId != null) q.eq("assignment_id", assignmentId);
        if (courseId != null) {
            var ids =
                    tasks.selectList(new QueryWrapper<Task>().eq("course_id", courseId)).stream()
                            .map(t -> t.id)
                            .toList();
            if (ids.isEmpty()) return Views.page(List.of(), page, size, 0);
            q.in("assignment_id", ids);
        }
        long total = submissions.selectCount(q);
        var rows =
                submissions.selectList(
                        q.orderByDesc("id").last("LIMIT " + size + " OFFSET " + offset));
        var taskById = new HashMap<Long, Task>();
        var gradeBySubmission = new HashMap<Long, Grade>();
        if (!rows.isEmpty()) {
            tasks.selectByIds(rows.stream().map(s -> s.assignmentId).distinct().toList())
                    .forEach(t -> taskById.put(t.id, t));
            grades.selectList(
                            new QueryWrapper<Grade>()
                                    .in("submission_id", rows.stream().map(s -> s.id).toList()))
                    .forEach(g -> gradeBySubmission.put(g.submissionId, g));
        }
        return Views.page(
                rows.stream()
                        .map(
                                s -> {
                                    Task t = taskById.get(s.assignmentId);
                                    if (t == null) throw ApiException.missing();
                                    Grade g = gradeBySubmission.get(s.id);
                                    return Views.map(
                                            "submissionId",
                                            s.id,
                                            "assignmentId",
                                            s.assignmentId,
                                            "courseId",
                                            t.courseId,
                                            "taskTitle",
                                            t.title,
                                            "maxScore",
                                            t.maxScore,
                                            "status",
                                            s.status,
                                            "score",
                                            g == null ? null : g.score,
                                            "comment",
                                            g == null ? null : g.comment,
                                            "gradedAt",
                                            g == null ? null : g.gradedAt);
                                })
                        .toList(),
                page,
                size,
                total);
    }

    public Contracts.HasData has(long id) {
        return new Contracts.HasData(
                submissions.selectCount(new QueryWrapper<Submission>().eq("student_id", id)) > 0);
    }
}
