package org.hanwen.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.common.*;
import org.hanwen.course.mapper.*;
import org.hanwen.course.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class CommunityService {
    final CourseService course;
    final NoticeMapper notices;
    final QuestionMapper questions;
    final ReplyMapper replies;
    final DirectoryClient directory;
    final Remote remote;

    public CommunityService(
            CourseService c,
            NoticeMapper n,
            QuestionMapper q,
            ReplyMapper r,
            DirectoryClient d,
            Remote rm) {
        course = c;
        notices = n;
        questions = q;
        replies = r;
        directory = d;
        remote = rm;
    }

    public record NoticeWrite(
            String title, String content, Boolean pinned, Integer expectedVersion) {}

    public record Ask(String title, String content, Long chapterId) {}

    public record ReplyWrite(String content) {}

    public record Resolve(Boolean resolved, Integer expectedVersion) {}

    Course read(long id) {
        var c = course.required(id);
        course.readable(c, true);
        return c;
    }

    void active(Course c) {
        if (!c.status.equals("PUBLISHED")) throw ApiException.conflict("公告与答疑在课程发布后开放，归档后只读");
    }

    static Map<String, Object> noticeView(Notice n) {
        return Views.map(
                "id",
                n.id,
                "courseId",
                n.courseId,
                "authorId",
                n.authorId,
                "title",
                n.title,
                "content",
                n.content,
                "pinned",
                n.pinned,
                "version",
                n.version,
                "createdAt",
                n.createdAt,
                "updatedAt",
                n.updatedAt);
    }

    public Object notices(long id, int page, int size) {
        read(id);
        int offset = Views.offset(page, size);
        var q = new QueryWrapper<Notice>().eq("course_id", id);
        long total = notices.selectCount(q);
        return Views.page(
                notices
                        .selectList(
                                q.orderByDesc("pinned", "id")
                                        .last("LIMIT " + size + " OFFSET " + offset))
                        .stream()
                        .map(CommunityService::noticeView)
                        .toList(),
                page,
                size,
                total);
    }

    @Transactional
    public Object notice(long courseId, Long id, NoticeWrite in) {
        var c = course.locked(courseId);
        course.owner(c);
        active(c);
        Notice n = id == null ? new Notice() : notices.lock(id);
        if (n == null || id != null && n.courseId != courseId) throw ApiException.missing();
        if (id != null) Names.version(n.version, in.expectedVersion());
        n.courseId = courseId;
        n.authorId = Actor.current().id();
        n.title = Views.text(in.title(), 100, "公告标题");
        n.content = Views.text(in.content(), 5000, "公告正文");
        n.pinned = Boolean.TRUE.equals(in.pinned());
        n.updatedAt = Views.now();
        if (id == null) {
            n.createdAt = n.updatedAt;
            n.version = 0;
            notices.insert(n);
        } else {
            n.version++;
            notices.updateById(n);
        }
        return noticeView(n);
    }

    @Transactional
    public void deleteNotice(long courseId, long id, int expectedVersion) {
        var c = course.locked(courseId);
        course.owner(c);
        active(c);
        var n = notices.lock(id);
        if (n == null || n.courseId != courseId) throw ApiException.missing();
        Names.version(n.version, expectedVersion);
        notices.deleteById(id);
    }

    Map<String, Object> questionView(Question q, String name) {
        return Views.map(
                "id",
                q.id,
                "courseId",
                q.courseId,
                "chapterId",
                q.chapterId,
                "studentId",
                q.studentId,
                "displayName",
                name,
                "title",
                q.title,
                "content",
                q.content,
                "resolved",
                q.resolved,
                "version",
                q.version,
                "createdAt",
                q.createdAt,
                "updatedAt",
                q.updatedAt,
                "replyCount",
                replies.selectCount(new QueryWrapper<Reply>().eq("question_id", q.id)).intValue());
    }

    public Object questions(long id, int page, int size, String keyword, Boolean resolved) {
        read(id);
        int offset = Views.offset(page, size);
        var q = new QueryWrapper<Question>().eq("course_id", id);
        if (keyword != null && !keyword.isBlank()) q.like("title", keyword);
        if (resolved != null) q.eq("resolved", resolved);
        long total = questions.selectCount(q);
        var rows =
                questions.selectList(
                        q.orderByDesc("id").last("LIMIT " + size + " OFFSET " + offset));
        var names = Names.read(rows.stream().map(x -> x.studentId).toList(), directory, remote);
        return Views.page(
                rows.stream().map(x -> questionView(x, names.get(x.studentId))).toList(),
                page,
                size,
                total);
    }

    @Transactional
    public Object ask(long id, Ask in) {
        Views.role("STUDENT");
        var c = course.locked(id);
        course.readable(c, true);
        active(c);
        if (in.chapterId() != null) course.chapter(id, in.chapterId());
        Question q = new Question();
        q.courseId = id;
        q.studentId = Actor.current().id();
        q.chapterId = in.chapterId();
        q.title = Views.text(in.title(), 100, "问题标题");
        q.content = Views.text(in.content(), 5000, "问题正文");
        q.resolved = false;
        q.version = 0;
        q.createdAt = q.updatedAt = Views.now();
        questions.insert(q);
        return questionView(q, null);
    }

    Question required(long id) {
        var q = questions.selectById(id);
        if (q == null) throw ApiException.missing();
        read(q.courseId);
        return q;
    }

    public Object detail(long id) {
        var q = required(id);
        var names = Names.read(List.of(q.studentId), directory, remote);
        return questionView(q, names.get(q.studentId));
    }

    public Object replies(long id, int page, int size) {
        required(id);
        int offset = Views.offset(page, size);
        var query = new QueryWrapper<Reply>().eq("question_id", id);
        long total = replies.selectCount(query);
        var rows =
                replies.selectList(
                        query.orderByAsc("id").last("LIMIT " + size + " OFFSET " + offset));
        var names = Names.read(rows.stream().map(x -> x.authorId).toList(), directory, remote);
        return Views.page(
                rows.stream()
                        .map(
                                r ->
                                        Views.map(
                                                "id",
                                                r.id,
                                                "authorId",
                                                r.authorId,
                                                "displayName",
                                                names.get(r.authorId),
                                                "role",
                                                r.role,
                                                "content",
                                                r.content,
                                                "createdAt",
                                                r.createdAt))
                        .toList(),
                page,
                size,
                total);
    }

    @Transactional
    public Object reply(long id, ReplyWrite in) {
        var q = required(id);
        var c = course.locked(q.courseId);
        course.readable(c, true);
        active(c);
        Reply r = new Reply();
        r.questionId = id;
        r.authorId = Actor.current().id();
        r.role = Actor.current().role();
        r.content = Views.text(in.content(), 5000, "回复");
        r.createdAt = Views.now();
        replies.insert(r);
        return Views.map("id", r.id);
    }

    @Transactional
    public Object resolve(long id, Resolve in) {
        var initial = required(id);
        var c = course.locked(initial.courseId);
        course.readable(c, true);
        active(c);
        var q = questions.lock(id);
        if (Actor.current().id() != q.studentId
                && !(course.staff(c))) throw ApiException.forbidden();
        Names.version(q.version, in.expectedVersion());
        if (in.resolved() == null) throw ApiException.bad("请选择解决状态");
        q.resolved = in.resolved();
        q.version++;
        q.updatedAt = Views.now();
        questions.updateById(q);
        return questionView(q, null);
    }
}
