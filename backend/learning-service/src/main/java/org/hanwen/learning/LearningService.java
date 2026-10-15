package org.hanwen.learning;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.common.*;
import org.hanwen.learning.mapper.*;
import org.hanwen.learning.model.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;

@Service
public class LearningService {
    final EnrollmentMapper enrollments;
    final ProgressMapper progress;
    final CourseClient course;
    final Remote remote;
    final FavoriteMapper favorites;

    public record Join(Long courseId) {}

    public LearningService(
            EnrollmentMapper e, ProgressMapper p, CourseClient c, Remote r, FavoriteMapper f) {
        enrollments = e;
        progress = p;
        course = c;
        remote = r;
        favorites = f;
    }

    public Contracts.CourseValidation course(long id) {
        return remote.call("learning-course", () -> course.course(id));
    }

    public Enrollment own(long id) {
        Views.role("STUDENT");
        Enrollment e = enrollments.selectById(id);
        if (e == null) throw ApiException.missing();
        if (e.studentId != Actor.current().id()) throw ApiException.forbidden();
        return e;
    }

    public Object view(Enrollment e) {
        var c = course(e.courseId);
        return Views.map(
                "id",
                e.id,
                "courseId",
                e.courseId,
                "studentId",
                e.studentId,
                "joinedAt",
                e.joinedAt,
                "courseName",
                c.name(),
                "courseStatus",
                c.status());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Object join(Join in) {
        Views.role("STUDENT");
        if (in.courseId() == null || in.courseId() < 1) throw ApiException.bad("请选择课程");
        var c = course(in.courseId());
        if (!c.status().equals("PUBLISHED")) throw ApiException.conflict("课程未发布或已归档，不能加入");
        long student = Actor.current().id();
        var q =
                new QueryWrapper<Enrollment>()
                        .eq("course_id", in.courseId())
                        .eq("student_id", student);
        Enrollment e = enrollments.selectOne(q);
        if (e == null) {
            e = new Enrollment();
            e.courseId = in.courseId();
            e.studentId = student;
            e.joinedAt = e.createdAt = e.updatedAt = Views.now();
            try {
                enrollments.insert(e);
            } catch (DuplicateKeyException ex) {
                e = enrollments.selectOne(q);
                if (e == null) throw ex;
            }
        }
        return Views.map(
                "id",
                e.id,
                "courseId",
                e.courseId,
                "studentId",
                e.studentId,
                "joinedAt",
                e.joinedAt,
                "courseName",
                c.name(),
                "courseStatus",
                c.status());
    }

    public Object list(int page, int size, Long courseId) {
        Views.role("STUDENT");
        int offset = Views.offset(page, size);
        var q = new QueryWrapper<Enrollment>().eq("student_id", Actor.current().id());
        if (courseId != null) q.eq("course_id", courseId);
        long total = enrollments.selectCount(q);
        return Views.page(
                enrollments
                        .selectList(q.orderByDesc("id").last("LIMIT " + size + " OFFSET " + offset))
                        .stream()
                        .map(this::view)
                        .toList(),
                page,
                size,
                total);
    }

    public Object detail(long id) {
        return view(own(id));
    }

    public static Object progressView(Progress p) {
        return Views.map(
                "chapterId", p.chapterId, "completed", p.completed, "completedAt", p.completedAt);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Object complete(long id, long chapterId) {
        Enrollment e = own(id);
        var h = remote.call("learning-chapter", () -> course.chapter(e.courseId, chapterId));
        var q = new QueryWrapper<Progress>().eq("enrollment_id", id).eq("chapter_id", chapterId);
        Progress p = progress.selectOne(q);
        if (p != null) return progressView(p);
        if (!h.courseStatus().equals("PUBLISHED")) throw ApiException.conflict("归档课程仅保留已有学习记录");
        p = new Progress();
        p.enrollmentId = id;
        p.chapterId = chapterId;
        p.completed = true;
        p.completedAt = p.createdAt = p.updatedAt = Views.now();
        try {
            progress.insert(p);
        } catch (DuplicateKeyException ex) {
            p = progress.selectOne(q);
            if (p == null) throw ex;
        }
        return progressView(p);
    }

    public Object summary(long id) {
        Enrollment e = own(id);
        var c = course(e.courseId);
        var rows =
                progress.selectList(
                        new QueryWrapper<Progress>()
                                .eq("enrollment_id", id)
                                .eq("completed", true)
                                .orderByAsc("chapter_id"));
        var percent =
                c.chapterCount() == 0
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(rows.size() * 100L)
                                .divide(
                                        BigDecimal.valueOf(c.chapterCount()),
                                        2,
                                        RoundingMode.HALF_UP);
        return Views.map(
                "total",
                c.chapterCount(),
                "completedCount",
                rows.size(),
                "percent",
                percent,
                "items",
                rows.stream().map(LearningService::progressView).toList());
    }

    public Contracts.Exists exists(long courseId, long studentId) {
        Actor a = Actor.current();
        if (a.role().equals("STUDENT")) {
            if (a.id() != studentId) throw ApiException.forbidden();
        } else if (a.role().equals("TEACHER")) {
            if (!course(courseId).canGrade()) throw ApiException.forbidden();
        } else throw ApiException.forbidden();
        return new Contracts.Exists(
                enrollments.selectCount(
                                new QueryWrapper<Enrollment>()
                                        .eq("course_id", courseId)
                                        .eq("student_id", studentId))
                        > 0);
    }

    public Contracts.HasData has(long id) {
        return new Contracts.HasData(
                enrollments.selectCount(new QueryWrapper<Enrollment>().eq("student_id", id)) > 0
                        || favorites.selectCount(new QueryWrapper<Favorite>().eq("student_id", id))
                                > 0);
    }

    public Object diagnostic() {
        Views.role("TEACHER");
        return remote.call("course-instance", () -> course.instance());
    }
}
