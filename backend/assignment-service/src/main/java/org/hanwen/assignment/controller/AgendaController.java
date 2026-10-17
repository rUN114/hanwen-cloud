package org.hanwen.assignment.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.assignment.client.CourseClient;
import org.hanwen.assignment.client.LearningClient;
import org.hanwen.assignment.mapper.*;
import org.hanwen.assignment.model.*;
import org.hanwen.assignment.service.AssignmentService;
import org.hanwen.common.*;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.*;

@RestController
public class AgendaController {
    final TaskMapper tasks;
    final SubmissionMapper submissions;
    final LearningClient learning;
    final Remote remote;
    final CourseClient courses;

    public AgendaController(
            TaskMapper t, SubmissionMapper s, LearningClient l, Remote r, CourseClient courses) {
        tasks = t;
        submissions = s;
        learning = l;
        remote = r;
        this.courses = courses;
    }

    @GetMapping("/api/assignments/agenda")
    public Object month(@RequestParam String month) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
            if (ym.getYear() < 1900 || ym.getYear() > 2100) throw new IllegalArgumentException();
        } catch (Exception e) {
            throw ApiException.bad("月份须为YYYY-MM");
        }
        var a = Actor.current();
        var q =
                new QueryWrapper<Task>()
                        .ne("status", "DRAFT")
                        .ge("deadline_at", ym.atDay(1).atStartOfDay())
                        .lt("deadline_at", ym.plusMonths(1).atDay(1).atStartOfDay());
        if (a.role().equals("TEACHER")) {
            var ids = remote.call("agenda-staff-courses", () -> courses.staffIds(false));
            if (ids.isEmpty())
                return ApiResponse.ok(Views.map("items", List.of(), "hasMore", false));
            q.in("course_id", ids);
        } else {
            Views.role("STUDENT");
            var ids = remote.call("agenda-enrollment", () -> learning.activeCourses());
            if (ids.isEmpty())
                return ApiResponse.ok(Views.map("items", List.of(), "hasMore", false));
            q.in("course_id", ids);
        }
        long total = tasks.selectCount(q);
        var rows = tasks.selectList(q.orderByAsc("deadline_at", "id").last("LIMIT 200"));
        return ApiResponse.ok(
                Views.map(
                        "items",
                        rows.stream()
                                .map(
                                        t -> {
                                            var v = AssignmentService.taskView(t);
                                            if (a.role().equals("STUDENT")) {
                                                var sub =
                                                        submissions.selectOne(
                                                                new QueryWrapper<Submission>()
                                                                        .eq("assignment_id", t.id)
                                                                        .eq("student_id", a.id()));
                                                v.put(
                                                        "submissionStatus",
                                                        sub == null ? null : sub.status);
                                                v.put(
                                                        "submissionId",
                                                        sub == null ? null : sub.id.toString());
                                            }
                                            return v;
                                        })
                                .toList(),
                        "hasMore",
                        total > 200));
    }
}
