package org.hanwen.assignment.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.assignment.client.LearningClient;
import org.hanwen.assignment.mapper.*;
import org.hanwen.assignment.model.*;
import org.hanwen.common.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AnalyticsService {
    final TaskMapper tasks;
    final SubmissionMapper submissions;
    final LearningClient learning;
    final Remote remote;
    final AssignmentService assignment;
    final AnalyticsMapper analytics;

    public AnalyticsService(
            AssignmentService a,
            AnalyticsMapper m,
            TaskMapper t,
            SubmissionMapper sub,
            LearningClient l,
            Remote r) {
        assignment = a;
        analytics = m;
        tasks = t;
        submissions = sub;
        learning = l;
        remote = r;
    }

    static Map<String, Object> safe(Map<String, Object> input) {
        var result = new LinkedHashMap<String, Object>();
        for (var e : input.entrySet()) {
            Object v = e.getValue();
            if (e.getKey().equals("id") || e.getKey().endsWith("Id")) v = v.toString();
            if (v instanceof java.math.BigDecimal b) v = b.setScale(2).toPlainString();
            if (v instanceof java.time.LocalDateTime t)
                v = t.atOffset(java.time.ZoneOffset.ofHours(8)).toString();
            if (v instanceof java.sql.Timestamp t)
                v = t.toLocalDateTime().atOffset(java.time.ZoneOffset.ofHours(8)).toString();
            result.put(e.getKey(), v);
        }
        return result;
    }

    public Object course(long id) {
        Views.role("TEACHER");
        if (!assignment.course(id).canGrade()) throw ApiException.forbidden();
        return Views.map(
                "tasks",
                analytics.tasks(id).stream().map(AnalyticsService::safe).toList(),
                "learners",
                analytics.learners(id).stream().map(AnalyticsService::safe).toList());
    }

    public Object overview() {
        Views.role("TEACHER");
        var ids =
                remote.call("analytics-staff-courses", () -> assignment.courses().staffIds(false));
        var v = new LinkedHashMap<String, Object>();
        v.put("pendingGrading", ids.isEmpty() ? 0 : analytics.pending(ids));
        v.put(
                "items",
                ids.isEmpty()
                        ? List.of()
                        : analytics.inbox(ids).stream().map(AnalyticsService::safe).toList());
        return v;
    }

    public Object pending(int page, int size) {
        Views.role("STUDENT");
        int offset = Views.offset(page, size);
        var ids = remote.call("pending-enrollments", () -> learning.activeCourses());
        if (ids.isEmpty()) return Views.page(List.of(), page, size, 0);
        var done =
                submissions
                        .selectList(
                                new QueryWrapper<Submission>()
                                        .select("assignment_id")
                                        .eq("student_id", Actor.current().id()))
                        .stream()
                        .map(s -> s.assignmentId)
                        .toList();
        var q =
                new QueryWrapper<Task>()
                        .in("course_id", ids)
                        .eq("status", "PUBLISHED")
                        .and(w -> w.isNull("deadline_at").or().gt("deadline_at", Views.now()));
        if (!done.isEmpty()) q.notIn("id", done);
        long total = tasks.selectCount(q);
        return Views.page(
                tasks
                        .selectList(
                                q.orderByAsc("deadline_at", "id")
                                        .last("LIMIT " + size + " OFFSET " + offset))
                        .stream()
                        .map(AssignmentService::taskView)
                        .toList(),
                page,
                size,
                total);
    }
}
