package org.hanwen.course.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.common.*;
import org.hanwen.course.mapper.*;
import org.hanwen.course.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;

@Service
public class CourseCopyService {
    private final CourseService access;
    private final CourseMapper courses;
    private final ChapterMapper chapters;
    private final QuizMapper quizzes;
    private final QuizQuestionMapper questions;

    public record Copy(String name) {}

    public CourseCopyService(
            CourseService a,
            CourseMapper c,
            ChapterMapper h,
            QuizMapper q,
            QuizQuestionMapper questions) {
        access = a;
        courses = c;
        chapters = h;
        quizzes = q;
        this.questions = questions;
    }

    @Transactional
    public Object copy(long id, Copy in) {
        Views.role("TEACHER");
        String name = Views.text(in.name(), 100, "新课程名称");
        var source = access.locked(id);
        access.principal(source);
        var rows =
                chapters.selectList(
                        new QueryWrapper<Chapter>()
                                .eq("course_id", id)
                                .orderByAsc("sort_no", "id"));
        var sets =
                quizzes.selectList(
                        new QueryWrapper<Quiz>()
                                .eq("course_id", id)
                                .ne("status", "ARCHIVED")
                                .orderByAsc("id"));
        if (rows.size() > 500 || sets.size() > 100) throw ApiException.conflict("课程内容过多，请分批整理后复制");
        var next = new Course();
        next.teacherId = source.teacherId;
        next.name = name;
        next.summary = source.summary;
        next.category = source.category;
        next.status = "DRAFT";
        next.createdAt = next.updatedAt = Views.now();
        courses.insert(next);
        var ids = new HashMap<Long, Long>();
        for (var h : rows) {
            var n = new Chapter();
            n.courseId = next.id;
            n.title = h.title;
            n.content = h.content;
            // Preserve textual lesson layout; images must be selected from the new course
            // resources.
            n.lessonDocument =
                    h.lessonDocument == null
                            ? null
                            : h.lessonDocument.replaceAll(
                                    "!\\[[^\\]\\r\\n]*\\]\\(resource:[0-9]+\\)", "[图片需在新课程重新选择]");
            n.sortNo = h.sortNo;
            n.createdAt = n.updatedAt = Views.now();
            chapters.insert(n);
            ids.put(h.id, n.id);
        }
        int count = 0;
        for (var q : sets) {
            var n = new Quiz();
            n.courseId = next.id;
            n.chapterId = q.chapterId == null ? null : ids.get(q.chapterId);
            n.title = q.title;
            n.instructions = q.instructions;
            n.status = "DRAFT";
            n.version = 0;
            n.timeLimitMinutes = q.timeLimitMinutes;
            n.maxAttempts = q.maxAttempts;
            // New term dates must be set by the teacher before publishing.
            n.createdAt = n.updatedAt = Views.now();
            quizzes.insert(n);
            for (var r :
                    questions.selectList(
                            new QueryWrapper<QuizQuestion>()
                                    .eq("quiz_id", q.id)
                                    .orderByAsc("sort_no", "id"))) {
                var x = new QuizQuestion();
                x.quizId = n.id;
                x.prompt = r.prompt;
                x.optionA = r.optionA;
                x.optionB = r.optionB;
                x.optionC = r.optionC;
                x.optionD = r.optionD;
                x.correctOption = r.correctOption;
                x.explanation = r.explanation;
                x.sortNo = r.sortNo;
                x.questionType = r.questionType;
                x.points = r.points;
                x.bankId = r.bankId;
                x.bankVersion = r.bankVersion;
                questions.insert(x);
                count++;
            }
        }
        return Views.map(
                "course",
                CourseService.view(next),
                "chapters",
                rows.size(),
                "quizzes",
                sets.size(),
                "questions",
                count);
    }
}
