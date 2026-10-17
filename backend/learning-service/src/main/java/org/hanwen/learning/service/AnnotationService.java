package org.hanwen.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.common.*;
import org.hanwen.learning.client.CourseClient;
import org.hanwen.learning.mapper.*;
import org.hanwen.learning.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service
public class AnnotationService {
    final LearningService learning;
    final CourseClient course;
    final AnnotationMapper annotations;
    final Remote remote;

    public AnnotationService(LearningService l, CourseClient c, AnnotationMapper a, Remote r) {
        learning = l;
        course = c;
        annotations = a;
        remote = r;
    }

    public record Write(
            Long enrollmentId,
            Long chapterId,
            Integer startOffset,
            Integer endOffset,
            String quoteText,
            String comment,
            String color) {}

    static String hash(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    Object view(Annotation a, String current) {
        return Views.map(
                "id",
                a.id,
                "enrollmentId",
                a.enrollmentId,
                "chapterId",
                a.chapterId,
                "startOffset",
                a.startOffset,
                "endOffset",
                a.endOffset,
                "quoteText",
                a.quoteText,
                "comment",
                a.comment,
                "color",
                a.color,
                "createdAt",
                a.createdAt,
                "anchorValid",
                a.sourceHash.equals(hash(current)));
    }

    Contracts.ChapterText text(long enrollment, long chapter) {
        var e = learning.own(enrollment);
        return remote.call("annotation-chapter", () -> course.text(e.courseId, chapter));
    }

    public Object list(long enrollment, long chapter, int page, int size) {
        int offset = Views.offset(page, size);
        var t = text(enrollment, chapter);
        var q =
                new QueryWrapper<Annotation>()
                        .eq("student_id", Actor.current().id())
                        .eq("enrollment_id", enrollment)
                        .eq("chapter_id", chapter);
        long total = annotations.selectCount(q);
        return Views.page(
                annotations
                        .selectList(q.orderByDesc("id").last("LIMIT " + size + " OFFSET " + offset))
                        .stream()
                        .map(a -> view(a, t.content()))
                        .toList(),
                page,
                size,
                total);
    }

    @Transactional
    public Object create(Write in) {
        Views.role("STUDENT");
        if (in.enrollmentId() == null || in.chapterId() == null) throw ApiException.bad("请选择章节");
        var t = text(in.enrollmentId(), in.chapterId());
        if (!t.status().equals("PUBLISHED")) throw ApiException.conflict("归档课程只读，不能新增批注");
        if (in.startOffset() == null
                || in.endOffset() == null
                || in.startOffset() < 0
                || in.endOffset() <= in.startOffset()
                || in.endOffset() > t.content().length()
                || in.endOffset() - in.startOffset() > 500) throw ApiException.bad("请选择1至500字的原文");
        String quote = t.content().substring(in.startOffset(), in.endOffset());
        if (!quote.equals(in.quoteText())) throw ApiException.conflict("原文已变化，请重新选择文句");
        if (!Set.of("RED", "PINE", "GOLD").contains(String.valueOf(in.color())))
            throw ApiException.bad("批注颜色不合法");
        Annotation a = new Annotation();
        a.studentId = Actor.current().id();
        a.enrollmentId = in.enrollmentId();
        a.chapterId = in.chapterId();
        a.startOffset = in.startOffset();
        a.endOffset = in.endOffset();
        a.quoteText = quote;
        a.sourceHash = hash(t.content());
        a.comment = Views.text(in.comment(), 1000, "批注");
        a.color = in.color();
        a.createdAt = a.updatedAt = Views.now();
        annotations.insert(a);
        return view(a, t.content());
    }

    @Transactional
    public void delete(long id) {
        Views.role("STUDENT");
        var a = annotations.lock(id);
        if (a == null) throw ApiException.missing();
        if (a.studentId != Actor.current().id()) throw ApiException.forbidden();
        annotations.deleteById(id);
    }
}
