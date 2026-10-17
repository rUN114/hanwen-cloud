package org.hanwen.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.common.*;
import org.hanwen.learning.client.CourseClient;
import org.hanwen.learning.mapper.*;
import org.hanwen.learning.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class PracticeMarkService {
    final QuizAttemptMapper attempts;
    final AttemptItemMapper items;
    final QuizMarkEventMapper events;
    final CourseClient courses;
    final Remote remote;
    final DirectoryClient directory;

    public PracticeMarkService(
            QuizAttemptMapper a,
            AttemptItemMapper i,
            QuizMarkEventMapper e,
            CourseClient c,
            Remote r,
            DirectoryClient d) {
        attempts = a;
        items = i;
        events = e;
        courses = c;
        remote = r;
        directory = d;
    }

    public record Item(Long id, Integer score, String comment) {}

    public record Mark(Integer expectedVersion, List<Item> items) {}

    QuizAttempt required(long id, boolean lock) {
        var a = lock ? attempts.lock(id) : attempts.selectById(id);
        if (a == null) throw ApiException.missing();
        return a;
    }

    void teacher(QuizAttempt a) {
        Views.role("TEACHER");
        if (!remote.call("practice-mark-course", () -> courses.course(a.courseId)).canGrade())
            throw ApiException.forbidden();
    }

    List<AttemptItem> items(long id) {
        return items.selectList(
                new QueryWrapper<AttemptItem>().eq("attempt_id", id).orderByAsc("id"));
    }

    public Object pending(int page, int size) {
        Views.role("TEACHER");
        int offset = Views.offset(page, size);
        var ids = remote.call("practice-mark-staff", () -> courses.staffIds(false));
        if (ids.isEmpty()) return Views.page(List.of(), page, size, 0);
        var q =
                new QueryWrapper<QuizAttempt>()
                        .in("course_id", ids)
                        .eq("grading_status", "PENDING");
        long total = attempts.selectCount(q);
        var rows =
                attempts.selectList(
                        q.orderByAsc("completed_at", "id")
                                .last("LIMIT " + size + " OFFSET " + offset));
        var names = Names.read(rows.stream().map(x -> x.studentId).toList(), directory, remote);
        return Views.page(
                rows.stream()
                        .map(
                                x ->
                                        Views.map(
                                                "id",
                                                x.id,
                                                "courseId",
                                                x.courseId,
                                                "quizId",
                                                x.quizId,
                                                "studentName",
                                                names.getOrDefault(x.studentId, "学生"),
                                                "completedAt",
                                                x.completedAt))
                        .toList(),
                page,
                size,
                total);
    }

    public Object detail(long id) {
        var a = required(id, false);
        teacher(a);
        var rows = items(id);
        var name = Names.read(List.of(a.studentId), directory, remote);
        var log =
                events.selectList(
                        new QueryWrapper<QuizMarkEvent>()
                                .eq("attempt_id", id)
                                .orderByDesc("id")
                                .last("LIMIT 50"));
        var graders = Names.read(log.stream().map(e -> e.teacherId).toList(), directory, remote);
        return Views.map(
                "id",
                a.id,
                "courseId",
                a.courseId,
                "quizId",
                a.quizId,
                "studentName",
                name.getOrDefault(a.studentId, "学生"),
                "score",
                "PENDING".equals(a.gradingStatus) ? null : a.score,
                "total",
                a.total,
                "gradingStatus",
                a.gradingStatus,
                "gradingVersion",
                a.gradingVersion,
                "completedAt",
                a.completedAt,
                "items",
                rows.stream()
                        .map(
                                i ->
                                        Views.map(
                                                "id",
                                                i.id,
                                                "questionId",
                                                i.questionId,
                                                "questionType",
                                                i.questionType,
                                                "prompt",
                                                i.prompt,
                                                "selectedOption",
                                                i.selectedOption,
                                                "correctOption",
                                                i.correctOption,
                                                "explanation",
                                                i.explanation,
                                                "points",
                                                i.points,
                                                "knowledgePoint",
                                                i.knowledgePoint,
                                                "awardedPoints",
                                                QuestionRules.earned(
                                                        i.questionType,
                                                        i.correct,
                                                        i.awardedPoints,
                                                        i.points),
                                                "teacherComment",
                                                i.teacherComment))
                        .toList(),
                "history",
                log.stream()
                        .map(
                                e ->
                                        Views.map(
                                                "id",
                                                e.id,
                                                "teacherName",
                                                graders.getOrDefault(e.teacherId, "教师"),
                                                "score",
                                                e.score,
                                                "version",
                                                e.version,
                                                "itemsJson",
                                                e.itemsJson,
                                                "createdAt",
                                                e.createdAt))
                        .toList());
    }

    @Transactional
    public Object mark(long id, Mark in) {
        var a = required(id, true);
        teacher(a);
        Names.version(a.gradingVersion, in.expectedVersion());
        var rows = items(id);
        var written = rows.stream().filter(x -> QuestionRules.manual(x.questionType)).toList();
        if (written.isEmpty()) throw ApiException.conflict("客观题答卷不需要人工评分");
        if (a.gradingVersion >= 50) throw ApiException.conflict("本答卷已达50次评分修订上限");
        if (in.items() == null || in.items().size() != written.size())
            throw ApiException.bad("请逐题完成全部主观题评分");
        var map = new HashMap<Long, Item>();
        for (var x : in.items())
            if (x == null || x.id() == null || map.put(x.id(), x) != null)
                throw ApiException.bad("分项编号缺失或重复");
        for (var x : written) {
            var mark = map.get(x.id);
            if (mark == null) throw ApiException.bad("分项不属于本答卷");
            x.awardedPoints = QuestionRules.awarded(mark.score(), x.points);
            x.teacherComment = Views.optional(mark.comment(), 2000, "批语");
            x.correct = x.awardedPoints.equals(x.points);
            items.updateById(x);
        }
        a.score =
                rows.stream()
                        .mapToInt(
                                x ->
                                        x.awardedPoints == null
                                                ? (Boolean.TRUE.equals(x.correct) ? x.points : 0)
                                                : x.awardedPoints)
                        .sum();
        a.gradingStatus = "GRADED";
        a.gradingVersion++;
        a.graderId = Actor.current().id();
        a.gradedAt = Views.now();
        attempts.updateById(a);
        var event = new QuizMarkEvent();
        event.attemptId = id;
        event.teacherId = a.graderId;
        event.version = a.gradingVersion;
        event.score = a.score;
        event.createdAt = a.gradedAt;
        event.itemsJson =
                tools.jackson.databind.json.JsonMapper.builder()
                        .build()
                        .writeValueAsString(
                                written.stream()
                                        .map(
                                                x ->
                                                        Views.map(
                                                                "id",
                                                                x.id,
                                                                "score",
                                                                x.awardedPoints,
                                                                "comment",
                                                                x.teacherComment))
                                        .toList());
        events.insert(event);
        return detail(id);
    }
}
