package org.hanwen.course.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.common.*;
import org.hanwen.course.client.LearningClient;
import org.hanwen.course.mapper.*;
import org.hanwen.course.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;

@Service
public class CourseService {
    final CourseMapper courses;
    final ChapterMapper chapters;
    final LearningClient learning;
    final Remote remote;
    final WorkMapper works;
    final DirectoryClient directory;
    final ResourceFileMapper resources;
    final CourseTeamMapper team;

    public record CourseWrite(String name, String summary, String category) {}

    public record ChapterWrite(
            String title, String content, Integer sortNo, String lessonDocument) {}

    public CourseService(
            CourseMapper c,
            ChapterMapper h,
            LearningClient l,
            Remote r,
            WorkMapper w,
            DirectoryClient d,
            ResourceFileMapper resources,
            CourseTeamMapper team) {
        courses = c;
        chapters = h;
        learning = l;
        remote = r;
        works = w;
        directory = d;
        this.resources = resources;
        this.team = team;
    }

    public Course required(long id) {
        Course c = courses.selectById(id);
        if (c == null) throw ApiException.missing();
        return c;
    }

    public Course locked(long id) {
        Course c = courses.lock(id);
        if (c == null) throw ApiException.missing();
        return c;
    }

    public String accessRole(Course c) {
        var a = Actor.current();
        if (!a.role().equals("TEACHER")) return "NONE";
        if (c.teacherId == a.id()) return "OWNER";
        var m =
                team.selectOne(
                        new QueryWrapper<CourseTeam>()
                                .eq("course_id", c.id)
                                .eq("user_id", a.id())
                                .eq("status", "ACTIVE"));
        return m == null ? "NONE" : m.role;
    }

    public void owner(Course c) {
        Views.role("TEACHER");
        if (!java.util.Set.of("OWNER", "CO_TEACHER").contains(accessRole(c)))
            throw ApiException.forbidden();
    }

    public void principal(Course c) {
        Views.role("TEACHER");
        if (c.teacherId != Actor.current().id()) throw ApiException.forbidden();
    }

    public void savePrincipal(Course c) {
        courses.updateById(c);
    }

    public boolean staff(Course c) {
        return !accessRole(c).equals("NONE");
    }

    public java.util.List<Long> staffCourseIds(boolean teachOnly) {
        Views.role("TEACHER");
        var q =
                new QueryWrapper<CourseTeam>()
                        .eq("user_id", Actor.current().id())
                        .eq("status", "ACTIVE");
        if (teachOnly) q.eq("role", "CO_TEACHER");
        var delegated =
                team.selectList(q.last("LIMIT 1001")).stream().map(m -> m.courseId).toList();
        var owned =
                courses.selectList(
                        new QueryWrapper<Course>()
                                .eq("teacher_id", Actor.current().id())
                                .last("LIMIT 1001"));
        var ids = new LinkedHashSet<>(delegated);
        owned.forEach(c -> ids.add(c.id));
        if (ids.size() > 1000 || delegated.size() > 1000 || owned.size() > 1000)
            throw ApiException.bad("协作课程超过1000项，请分账号管理");
        return java.util.List.copyOf(ids);
    }

    public void readable(Course c, boolean content) {
        Actor a = Actor.current();
        if (staff(c)) return;
        if (!content && c.status.equals("PUBLISHED")) return;
        if (!a.role().equals("STUDENT")
                || c.status.equals("DRAFT")
                || !remote.call("course-enrollment", () -> learning.exists(c.id, a.id())).exists())
            throw ApiException.forbidden();
    }

    public static java.util.Map<String, Object> view(Course c) {
        return Views.map(
                "id",
                c.id,
                "teacherId",
                c.teacherId,
                "name",
                c.name,
                "summary",
                c.summary,
                "category",
                c.category,
                "status",
                c.status,
                "createdAt",
                c.createdAt,
                "updatedAt",
                c.updatedAt);
    }

    public static Object chapterView(Chapter c, boolean body) {
        var v = Views.map("id", c.id, "courseId", c.courseId, "title", c.title, "sortNo", c.sortNo);
        if (body) {
            v.put("content", c.content);
            v.put("lessonDocument", c.lessonDocument);
        }
        return v;
    }

    public Object list(
            int page, int size, String keyword, String category, String status, boolean teacher) {
        int offset = Views.offset(page, size);
        var q = new QueryWrapper<Course>();
        if (teacher) {
            Views.role("TEACHER");
            var ids = staffCourseIds(false);
            if (ids.isEmpty()) return Views.page(java.util.List.of(), page, size, 0);
            q.in("id", ids);
            if (status != null && !status.isBlank()) q.eq("status", status);
        } else q.eq("status", "PUBLISHED");
        if (keyword != null && !keyword.isBlank()) q.like("name", keyword);
        if (category != null && !category.isBlank()) q.eq("category", category);
        long total = courses.selectCount(q);
        var rows =
                courses.selectList(q.orderByDesc("id").last("LIMIT " + size + " OFFSET " + offset));
        var names = Names.read(rows.stream().map(c -> c.teacherId).toList(), directory, remote);
        return Views.page(
                rows.stream()
                        .map(
                                c -> {
                                    var v = view(c);
                                    v.put("accessRole", accessRole(c));
                                    v.put("teacherName", names.getOrDefault(c.teacherId, "教师"));
                                    return v;
                                })
                        .toList(),
                page,
                size,
                total);
    }

    void fields(Course c, CourseWrite in) {
        c.name = Views.text(in.name(), 100, "课程名称");
        c.summary = Views.optional(in.summary(), 2000, "课程简介");
        c.category = Views.text(in.category(), 30, "分类");
        c.updatedAt = Views.now();
    }

    @Transactional
    public Object create(CourseWrite in) {
        Views.role("TEACHER");
        Course c = new Course();
        fields(c, in);
        c.teacherId = Actor.current().id();
        c.status = "DRAFT";
        c.createdAt = c.updatedAt;
        courses.insert(c);
        return view(c);
    }

    @Transactional
    public Object update(long id, CourseWrite in) {
        Course c = locked(id);
        owner(c);
        if (c.status.equals("ARCHIVED")) throw ApiException.conflict("归档课程只读");
        fields(c, in);
        courses.updateById(c);
        return view(c);
    }

    @Transactional
    public Object transition(long id, boolean publish) {
        Course c = locked(id);
        principal(c);
        if (!c.status.equals(publish ? "DRAFT" : "PUBLISHED"))
            throw ApiException.conflict("课程状态已改变，请刷新");
        if (publish && chapters.selectCount(new QueryWrapper<Chapter>().eq("course_id", id)) == 0)
            throw ApiException.conflict("请先添加至少一个章节");
        c.status = publish ? "PUBLISHED" : "ARCHIVED";
        c.updatedAt = Views.now();
        courses.updateById(c);
        return view(c);
    }

    public Object detail(long id) {
        Course c = required(id);
        readable(c, false);
        var v = view(c);
        v.put("accessRole", accessRole(c));
        v.put(
                "teacherName",
                Names.read(java.util.List.of(c.teacherId), directory, remote)
                        .getOrDefault(c.teacherId, "教师"));
        return v;
    }

    public Object outline(long id) {
        Course c = required(id);
        readable(c, true);
        return chapters
                .selectList(new QueryWrapper<Chapter>().eq("course_id", id).orderByAsc("sort_no"))
                .stream()
                .map(h -> chapterView(h, false))
                .toList();
    }

    public Chapter chapter(long id, long chapterId) {
        Chapter h = chapters.selectById(chapterId);
        if (h == null || h.courseId != id) throw ApiException.missing();
        return h;
    }

    public Object read(long id, long chapterId) {
        readable(required(id), true);
        return chapterView(chapter(id, chapterId), true);
    }

    @Transactional
    public Object writeChapter(long id, Long chapterId, ChapterWrite in) {
        Course c = locked(id);
        owner(c);
        if (c.status.equals("ARCHIVED") || chapterId == null && !c.status.equals("DRAFT"))
            throw ApiException.conflict("此课程不能新增或修改章节");
        Chapter h = chapterId == null ? new Chapter() : chapter(id, chapterId);
        if (in.sortNo() == null || in.sortNo() < 1 || in.sortNo() > 100000)
            throw ApiException.bad("排序号须为1至100000");
        if (chapterId != null && c.status.equals("PUBLISHED") && !in.sortNo().equals(h.sortNo))
            throw ApiException.conflict("已发布课程的章节顺序已冻结");
        h.courseId = id;
        h.title = Views.text(in.title(), 100, "章节标题");
        h.content = Views.text(in.content(), 20000, "章节正文");
        h.lessonDocument = DocumentRules.validate(in.lessonDocument(), true);
        for (long resourceId : DocumentRules.resources(h.lessonDocument)) {
            var f = resources.selectById(resourceId);
            if (f == null
                    || f.courseId != id
                    || !f.status.equals("ACTIVE")
                    || !f.mimeType.startsWith("image/")) throw ApiException.bad("讲义图片须为本课程有效图片资源");
        }
        h.sortNo = in.sortNo();
        h.updatedAt = Views.now();
        if (chapterId == null) {
            h.createdAt = h.updatedAt;
            chapters.insert(h);
        } else chapters.updateById(h);
        return chapterView(h, true);
    }

    @Transactional
    public void deleteChapter(long id, long chapterId) {
        Course c = locked(id);
        owner(c);
        if (!c.status.equals("DRAFT")) throw ApiException.conflict("仅草稿课程可删除章节");
        chapter(id, chapterId);
        chapters.deleteById(chapterId);
    }

    public Contracts.CourseValidation validation(long id) {
        Course c = required(id);
        return new Contracts.CourseValidation(
                c.id,
                c.teacherId,
                c.status,
                chapters.selectCount(new QueryWrapper<Chapter>().eq("course_id", id)).intValue(),
                c.name,
                accessRole(c));
    }

    public Contracts.ChapterValidation validateChapter(long id, long chapterId) {
        Course c = required(id);
        Chapter h = chapter(id, chapterId);
        return new Contracts.ChapterValidation(h.id, c.id, c.status);
    }

    public Contracts.HasData has(long id) {
        return new Contracts.HasData(
                courses.selectCount(new QueryWrapper<Course>().eq("teacher_id", id)) > 0
                        || works.selectCount(new QueryWrapper<Work>().eq("teacher_id", id)) > 0
                        || team.selectCount(
                                        new QueryWrapper<CourseTeam>()
                                                .eq("user_id", id)
                                                .eq("status", "ACTIVE"))
                                > 0);
    }

    public Contracts.ChapterText chapterText(long id, long chapterId) {
        var c = required(id);
        readable(c, true);
        var h = chapter(id, chapterId);
        return new Contracts.ChapterText(id, chapterId, h.content, c.status);
    }
}
