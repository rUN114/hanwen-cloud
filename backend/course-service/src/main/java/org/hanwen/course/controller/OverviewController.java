package org.hanwen.course.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.common.*;
import org.hanwen.course.mapper.CourseMapper;
import org.hanwen.course.mapper.QuestionMapper;
import org.hanwen.course.model.Course;
import org.hanwen.course.service.CourseService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class OverviewController {
    final CourseMapper courses;
    final QuestionMapper questions;
    final CourseService service;

    public OverviewController(CourseMapper c, QuestionMapper q, CourseService service) {
        courses = c;
        questions = q;
        this.service = service;
    }

    @GetMapping("/api/teacher/courses/summary")
    public Object overview() {
        Views.role("TEACHER");
        long id = Actor.current().id();
        var v = new LinkedHashMap<String, Object>();
        var ids = service.staffCourseIds(false);
        for (String state : List.of("DRAFT", "PUBLISHED", "ARCHIVED"))
            v.put(
                    state,
                    ids.isEmpty()
                            ? 0
                            : courses.selectCount(
                                    new QueryWrapper<Course>().in("id", ids).eq("status", state)));
        v.put("questions", questions.pending(id));
        return ApiResponse.ok(v);
    }
}
