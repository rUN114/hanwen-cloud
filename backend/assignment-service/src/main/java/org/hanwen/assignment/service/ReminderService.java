package org.hanwen.assignment.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.assignment.mapper.*;
import org.hanwen.assignment.model.*;
import org.hanwen.common.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ReminderService {
    final AssignmentService assignmentService;
    final ReminderMapper reminders;

    public ReminderService(AssignmentService assignmentService, ReminderMapper r) {
        this.assignmentService = assignmentService;
        reminders = r;
    }

    public record Send(List<Long> studentIds, String content) {}

    public record Copy(Long fromCourseId, Long toCourseId) {}

    List<Long> missing(Task t) {
        var feed =
                assignmentService
                        .remote()
                        .call(
                                "reminder-students",
                                () -> assignmentService.learning().grades(t.courseId));
        var submitted =
                assignmentService
                        .submissions()
                        .selectList(new QueryWrapper<Submission>().eq("assignment_id", t.id))
                        .stream()
                        .map(s -> s.studentId)
                        .collect(java.util.stream.Collectors.toSet());
        return feed.students().stream().filter(id -> !submitted.contains(id)).toList();
    }

    public Object candidates(long id) {
        var t = assignmentService.task(id);
        assignmentService.owner(t);
        var ids = missing(t);
        var names = Names.read(ids, assignmentService.directory(), assignmentService.remote());
        return ids.stream()
                .map(x -> Views.map("studentId", x, "displayName", names.getOrDefault(x, "学生")))
                .toList();
    }

    @Transactional
    public Object send(long id, Send in) {
        var t = assignmentService.lockedTask(id);
        assignmentService.owner(t);
        if (!assignmentService.course(t.courseId).status().equals("PUBLISHED"))
            throw ApiException.conflict("归档课程不能催交");
        assignmentService.writable(t);
        if (in.studentIds() == null
                || in.studentIds().isEmpty()
                || in.studentIds().size() > 100
                || in.studentIds().stream().anyMatch(Objects::isNull))
            throw ApiException.bad("每次选择1至100位学生");
        var ids = in.studentIds().stream().distinct().toList();
        if (!new HashSet<>(missing(t)).containsAll(ids))
            throw ApiException.conflict("选中学生已交卷或不属于本课程，请刷新名单");
        String text = Views.text(in.content(), 500, "提醒内容");
        int sent = 0;
        var day = Views.now().toLocalDate();
        for (long student : ids) {
            if (reminders.selectCount(
                            new QueryWrapper<Reminder>()
                                    .eq("assignment_id", id)
                                    .eq("student_id", student)
                                    .eq("day_key", day))
                    > 0) continue;
            var r = new Reminder();
            r.assignmentId = id;
            r.studentId = student;
            r.teacherId = Actor.current().id();
            r.content = text;
            r.dayKey = day;
            r.createdAt = Views.now();
            reminders.insert(r);
            sent++;
        }
        return Views.map("sent", sent, "alreadySentToday", ids.size() - sent);
    }

    public Object feed() {
        Views.role("STUDENT");
        var ids =
                assignmentService
                        .remote()
                        .call(
                                "reminder-courses",
                                () -> assignmentService.learning().activeCourses());
        if (ids.isEmpty()) return Views.map("items", List.of(), "hasMore", false);
        var output = new ArrayList<>();
        var due =
                assignmentService
                        .tasks()
                        .selectList(
                                new QueryWrapper<Task>()
                                        .in("course_id", ids)
                                        .eq("status", "PUBLISHED")
                                        .gt("deadline_at", Views.now())
                                        .le("deadline_at", Views.now().plusHours(48))
                                        .orderByAsc("deadline_at")
                                        .last("LIMIT 101"));
        for (var t : due.stream().limit(100).toList()) {
            if (assignmentService
                            .submissions()
                            .selectCount(
                                    new QueryWrapper<Submission>()
                                            .eq("assignment_id", t.id)
                                            .eq("student_id", Actor.current().id()))
                    == 0)
                output.add(
                        Views.map(
                                "eventKey",
                                "DEADLINE:" + t.id + ":" + t.deadlineAt.toLocalDate(),
                                "kind",
                                "DEADLINE",
                                "title",
                                t.title + "：48小时内截止",
                                "eventAt",
                                t.createdAt,
                                "url",
                                "/student/assignments/" + t.id + "/submit"));
        }
        var rows =
                reminders.selectList(
                        new QueryWrapper<Reminder>()
                                .eq("student_id", Actor.current().id())
                                .orderByDesc("id")
                                .last("LIMIT 101"));
        for (var x : rows.stream().limit(100).toList()) {
            var t = assignmentService.task(x.assignmentId);
            if (ids.contains(t.courseId))
                output.add(
                        Views.map(
                                "eventKey",
                                "NUDGE:" + x.id,
                                "kind",
                                "NUDGE",
                                "title",
                                t.title + " · " + x.content,
                                "eventAt",
                                x.createdAt,
                                "url",
                                "/student/assignments/" + t.id + "/submit"));
        }
        return Views.map("items", output, "hasMore", due.size() > 100 || rows.size() > 100);
    }

    @Transactional
    public Object copy(Copy in) {
        Views.role("TEACHER");
        if (in.fromCourseId() == null
                || in.toCourseId() == null
                || in.fromCourseId().equals(in.toCourseId()))
            throw ApiException.bad("选择不同的源课程和目标课程");
        var from = assignmentService.course(in.fromCourseId());
        var to = assignmentService.course(in.toCourseId());
        if (!from.canTeach() || !to.canTeach()) throw ApiException.forbidden();
        if (!to.status().equals("PUBLISHED")) throw ApiException.conflict("请先发布新课程，再复用作业");
        var rows =
                assignmentService
                        .tasks()
                        .selectList(
                                new QueryWrapper<Task>()
                                        .eq("course_id", from.id())
                                        .orderByAsc("id")
                                        .last("LIMIT 101"));
        if (rows.size() > 100) throw ApiException.bad("一次最多复用100份作业");
        for (var old : rows) {
            var t = new Task();
            t.courseId = to.id();
            t.teacherId = Actor.current().id();
            t.title = old.title;
            t.description = old.description;
            t.maxScore = old.maxScore;
            t.rubricJson = old.rubricJson;
            t.status = "DRAFT";
            t.createdAt = t.updatedAt = Views.now();
            assignmentService.tasks().insert(t);
        }
        return Views.map("copied", rows.size(), "courseId", to.id());
    }
}
