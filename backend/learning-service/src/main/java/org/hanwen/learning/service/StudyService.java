package org.hanwen.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.common.*;
import org.hanwen.learning.client.CourseClient;
import org.hanwen.learning.mapper.*;
import org.hanwen.learning.model.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.*;
import java.util.*;

@Service
public class StudyService {
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(StudyService.class);
    final CourseClient courseClient;
    final EnrollmentMapper enrollments;
    final ProgressMapper progress;
    final LearningService learning;
    final NoteMapper notes;
    final FavoriteMapper favorites;
    final DirectoryClient directory;
    final Remote remote;

    public StudyService(
            LearningService l,
            NoteMapper n,
            FavoriteMapper f,
            DirectoryClient d,
            Remote r,
            CourseClient c,
            EnrollmentMapper e,
            ProgressMapper p) {
        courseClient = c;
        enrollments = e;
        progress = p;
        learning = l;
        notes = n;
        favorites = f;
        directory = d;
        remote = r;
    }

    public record NoteWrite(
            Long enrollmentId,
            Long chapterId,
            String title,
            String content,
            Integer expectedVersion) {}

    public record FavoriteWrite(Long workId) {}

    static Map<String, Object> noteView(Note n) {
        return Views.map(
                "id",
                n.id,
                "enrollmentId",
                n.enrollmentId,
                "chapterId",
                n.chapterId,
                "title",
                n.title,
                "content",
                n.content,
                "version",
                n.version,
                "createdAt",
                n.createdAt,
                "updatedAt",
                n.updatedAt);
    }

    Note own(long id, boolean lock) {
        Views.role("STUDENT");
        var n = lock ? notes.lock(id) : notes.selectById(id);
        if (n == null) throw ApiException.missing();
        if (n.studentId != Actor.current().id()) throw ApiException.forbidden();
        return n;
    }

    public Object notes(int page, int size, String keyword, Long enrollmentId, Long chapterId) {
        Views.role("STUDENT");
        int offset = Views.offset(page, size);
        var q = new QueryWrapper<Note>().eq("student_id", Actor.current().id());
        if (enrollmentId != null) {
            learning.own(enrollmentId);
            q.eq("enrollment_id", enrollmentId);
        }
        if (chapterId != null) q.eq("chapter_id", chapterId);
        if (keyword != null && !keyword.isBlank())
            q.and(x -> x.like("title", keyword).or().like("content", keyword));
        long total = notes.selectCount(q);
        return Views.page(
                notes
                        .selectList(
                                q.orderByDesc("updated_at", "id")
                                        .last("LIMIT " + size + " OFFSET " + offset))
                        .stream()
                        .map(StudyService::noteView)
                        .toList(),
                page,
                size,
                total);
    }

    @Transactional
    public Object note(Long id, NoteWrite in) {
        Views.role("STUDENT");
        Note n;
        if (id == null) {
            if (in.enrollmentId() == null || in.chapterId() == null)
                throw ApiException.bad("请选择课程章节");
            var e = learning.own(in.enrollmentId());
            var h =
                    remote.call(
                            "note-chapter", () -> courseClient.chapter(e.courseId, in.chapterId()));
            if (h.courseStatus().equals("DRAFT")) throw ApiException.forbidden();
            n = new Note();
            n.studentId = Actor.current().id();
            n.enrollmentId = e.id;
            n.chapterId = h.chapterId();
            n.version = 0;
            n.createdAt = Views.now();
        } else {
            n = own(id, true);
            Names.version(n.version, in.expectedVersion());
            if (in.enrollmentId() != null && !in.enrollmentId().equals(n.enrollmentId)
                    || in.chapterId() != null && !in.chapterId().equals(n.chapterId))
                throw ApiException.bad("札记所属章节不可更改");
            n.version++;
        }
        n.title = Views.text(in.title(), 100, "札记标题");
        n.content = Views.text(in.content(), 10000, "札记正文");
        n.updatedAt = Views.now();
        if (id == null) notes.insert(n);
        else notes.updateById(n);
        return noteView(n);
    }

    @Transactional
    public void delete(long id, int version) {
        var n = own(id, true);
        Names.version(n.version, version);
        notes.deleteById(id);
    }

    public Object favorites(int page, int size) {
        Views.role("STUDENT");
        int offset = Views.offset(page, size);
        var q = new QueryWrapper<Favorite>().eq("student_id", Actor.current().id());
        long total = favorites.selectCount(q);
        var rows =
                favorites.selectList(
                        q.orderByDesc("id").last("LIMIT " + size + " OFFSET " + offset));
        Map<Long, Contracts.WorkBrief> works = new HashMap<>();
        if (!rows.isEmpty()) {
            String ids =
                    rows.stream()
                            .map(x -> x.workId.toString())
                            .collect(java.util.stream.Collectors.joining(","));
            for (var w : remote.call("favorite-works", () -> courseClient.works(ids)))
                works.put(w.id(), w);
        }
        return Views.page(
                rows.stream()
                        .map(
                                f -> {
                                    var w = works.get(f.workId);
                                    return Views.map(
                                            "workId",
                                            f.workId,
                                            "createdAt",
                                            f.createdAt,
                                            "title",
                                            w == null ? "作品暂不可用" : w.title(),
                                            "author",
                                            w == null ? "" : w.author(),
                                            "dynasty",
                                            w == null ? "" : w.dynasty(),
                                            "genre",
                                            w == null ? "" : w.genre(),
                                            "status",
                                            w == null ? "UNAVAILABLE" : w.status());
                                })
                        .toList(),
                page,
                size,
                total);
    }

    public Object favoriteState(long workId) {
        Views.role("STUDENT");
        return Views.map(
                "favorited",
                favorites.selectCount(
                                new QueryWrapper<Favorite>()
                                        .eq("student_id", Actor.current().id())
                                        .eq("work_id", workId))
                        > 0);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Object favorite(FavoriteWrite in) {
        Views.role("STUDENT");
        if (in.workId() == null || in.workId() < 1) throw ApiException.bad("请选择作品");
        var w = remote.call("favorite-work", () -> courseClient.work(in.workId()));
        if (!w.status().equals("PUBLISHED")) throw ApiException.conflict("作品已归档，不能新增收藏");
        var q =
                new QueryWrapper<Favorite>()
                        .eq("student_id", Actor.current().id())
                        .eq("work_id", in.workId());
        var f = favorites.selectOne(q);
        if (f == null) {
            f = new Favorite();
            f.studentId = Actor.current().id();
            f.workId = in.workId();
            f.createdAt = Views.now();
            try {
                favorites.insert(f);
            } catch (DuplicateKeyException ex) {
                if (favorites.selectCount(q) == 0) throw ex;
            }
        }
        return Views.map("favorited", true);
    }

    @Transactional
    public void unfavorite(long workId) {
        Views.role("STUDENT");
        favorites.delete(
                new QueryWrapper<Favorite>()
                        .eq("student_id", Actor.current().id())
                        .eq("work_id", workId));
    }

    public Object overview() {
        Views.role("STUDENT");
        long id = Actor.current().id();
        var result = new LinkedHashMap<String, Object>();
        result.put(
                "courses",
                enrollments.selectCount(new QueryWrapper<Enrollment>().eq("student_id", id)));
        result.put("notes", notes.selectCount(new QueryWrapper<Note>().eq("student_id", id)));
        result.put(
                "favorites",
                favorites.selectCount(new QueryWrapper<Favorite>().eq("student_id", id)));
        result.put("completedChapters", progress.completedByStudent(id));
        return result;
    }

    public Object roster(long courseId, int page, int size) {
        Views.role("TEACHER");
        var c = learning.course(courseId);
        if (!c.canGrade()) throw ApiException.forbidden();
        int offset = Views.offset(page, size);
        var q = new QueryWrapper<Enrollment>().eq("course_id", courseId);
        long total = enrollments.selectCount(q);
        var rows =
                enrollments.selectList(
                        q.orderByAsc("id").last("LIMIT " + size + " OFFSET " + offset));
        var names = Names.read(rows.stream().map(e -> e.studentId).toList(), directory, remote);
        var completedByEnrollment = new HashMap<Long, Integer>();
        var notesByEnrollment = new HashMap<Long, Integer>();
        if (!rows.isEmpty()) {
            var ids = rows.stream().map(e -> e.id).toList();
            progress.selectMaps(
                            new QueryWrapper<Progress>()
                                    .select("enrollment_id", "COUNT(*) AS n")
                                    .in("enrollment_id", ids)
                                    .eq("completed", true)
                                    .groupBy("enrollment_id"))
                    .forEach(
                            row ->
                                    completedByEnrollment.put(
                                            ((Number) row.get("enrollment_id")).longValue(),
                                            ((Number) row.get("n")).intValue()));
            notes.selectMaps(
                            new QueryWrapper<Note>()
                                    .select("enrollment_id", "COUNT(*) AS n")
                                    .in("enrollment_id", ids)
                                    .groupBy("enrollment_id"))
                    .forEach(
                            row ->
                                    notesByEnrollment.put(
                                            ((Number) row.get("enrollment_id")).longValue(),
                                            ((Number) row.get("n")).intValue()));
        }
        return Views.page(
                rows.stream()
                        .map(
                                e -> {
                                    int completed = completedByEnrollment.getOrDefault(e.id, 0);
                                    var percent =
                                            c.chapterCount() == 0
                                                    ? BigDecimal.ZERO
                                                    : BigDecimal.valueOf(completed * 100L)
                                                            .divide(
                                                                    BigDecimal.valueOf(
                                                                            c.chapterCount()),
                                                                    2,
                                                                    RoundingMode.HALF_UP);
                                    return Views.map(
                                            "studentId",
                                            e.studentId,
                                            "displayName",
                                            names.get(e.studentId),
                                            "enrollmentId",
                                            e.id,
                                            "joinedAt",
                                            e.joinedAt,
                                            "completed",
                                            completed,
                                            "chapters",
                                            c.chapterCount(),
                                            "percent",
                                            percent,
                                            "noteCount",
                                            notesByEnrollment.getOrDefault(e.id, 0));
                                })
                        .toList(),
                page,
                size,
                total);
    }

    public List<Long> activeCourses() {
        Views.role("STUDENT");
        var ids =
                enrollments
                        .selectList(
                                new QueryWrapper<Enrollment>()
                                        .eq("student_id", Actor.current().id()))
                        .stream()
                        .map(e -> e.courseId)
                        .distinct()
                        .toList();
        var courses = learning.courseBatch(ids);
        var result = new ArrayList<Long>();
        for (long id : ids) {
            var c = courses.get(id);
            if (c == null) LOG.warn("选课课程缺失 studentId={} courseId={}", Actor.current().id(), id);
            else if (c.status().equals("PUBLISHED")) result.add(id);
        }
        return result;
    }
}
