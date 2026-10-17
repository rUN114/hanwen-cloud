package org.hanwen.course.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.common.*;
import org.hanwen.course.mapper.QuizMapper;
import org.hanwen.course.model.Quiz;
import org.hanwen.course.service.CourseService;
import org.springframework.web.bind.annotation.*;

@RestController
public class GradeCatalogController {
    final CourseService courses;
    final QuizMapper quizzes;

    public GradeCatalogController(CourseService c, QuizMapper q) {
        courses = c;
        quizzes = q;
    }

    @GetMapping("/internal/courses/{id}/grade-quizzes")
    public java.util.List<Contracts.QuizBrief> list(@PathVariable long id) {
        courses.readable(courses.required(id), true);
        var rows =
                quizzes.selectList(
                        new QueryWrapper<Quiz>()
                                .eq("course_id", id)
                                .ne("status", "DRAFT")
                                .orderByAsc("id")
                                .last("LIMIT 501"));
        if (rows.size() > 500) throw ApiException.bad("课程练习超过500项，请分课程管理");
        return rows.stream().map(q -> new Contracts.QuizBrief(q.id, q.title, q.closesAt)).toList();
    }
}
