package org.hanwen.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.common.*;
import org.hanwen.learning.client.CourseClient;
import org.hanwen.learning.mapper.*;
import org.hanwen.learning.model.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.util.*;

@Service
public class ClassroomService {
    final ClassroomMapper rooms;
    final ClassMemberMapper members;
    final EnrollmentMapper enrollments;
    final CourseClient courses;
    final DirectoryClient directory;
    final Remote remote;
    final TransactionTemplate transaction;
    final TransactionTemplate membershipTransaction;

    public ClassroomService(
            ClassroomMapper r,
            ClassMemberMapper m,
            EnrollmentMapper e,
            CourseClient c,
            DirectoryClient d,
            Remote rm,
            PlatformTransactionManager manager) {
        rooms = r;
        members = m;
        enrollments = e;
        courses = c;
        directory = d;
        remote = rm;
        transaction = new TransactionTemplate(manager);
        membershipTransaction = new TransactionTemplate(manager);
        membershipTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public record Create(Long courseId, String name, String semester, String description) {}

    public record Write(
            String name,
            String semester,
            String description,
            String status,
            Integer expectedVersion) {}

    public record Import(java.util.List<String> usernames) {}

    public record Join(String code) {}

    Contracts.CourseValidation course(Classroom r) {
        return remote.call("class-course", () -> courses.course(r.courseId));
    }

    Classroom required(long id, boolean lock) {
        var r = lock ? rooms.lock(id) : rooms.selectById(id);
        if (r == null) throw ApiException.missing();
        return r;
    }

    void owner(Classroom r) {
        Views.role("TEACHER");
        if (!course(r).canGrade()) throw ApiException.forbidden();
    }

    Contracts.CourseValidation editor(Classroom r) {
        Views.role("TEACHER");
        var c = course(r);
        if (!c.canTeach()) throw ApiException.forbidden();
        return c;
    }

    void active(Classroom r, Contracts.CourseValidation c) {
        if (!r.status.equals("ACTIVE") || !c.status().equals("PUBLISHED"))
            throw ApiException.conflict("班级已结课或课程已归档，不能加入学生");
    }

    private Classroom locked(Classroom initial) {
        var current = required(initial.id, true);
        if (!current.courseId.equals(initial.courseId)) throw ApiException.conflict("所属课程已改变，请刷新");
        return current;
    }

    ClassMember member(long id, long student) {
        return members.selectOne(
                new QueryWrapper<ClassMember>().eq("class_id", id).eq("student_id", student));
    }

    Map<String, Object> view(Classroom r) {
        var c = course(r);
        return view(r, c);
    }

    Map<String, Object> view(Classroom r, Contracts.CourseValidation c) {
        long count =
                members.selectCount(
                        new QueryWrapper<ClassMember>()
                                .eq("class_id", r.id)
                                .eq("status", "ACTIVE"));
        var membership =
                Actor.current().role().equals("TEACHER")
                        ? null
                        : member(r.id, Actor.current().id());
        return view(r, c, count, membership);
    }

    private Map<String, Object> view(
            Classroom r, Contracts.CourseValidation c, long memberCount, ClassMember membership) {
        var v =
                Views.map(
                        "id",
                        r.id,
                        "name",
                        r.name,
                        "semester",
                        r.semester,
                        "description",
                        r.description,
                        "courseId",
                        r.courseId,
                        "courseName",
                        c.name(),
                        "courseStatus",
                        c.status(),
                        "canEdit",
                        c.canTeach(),
                        "teacherId",
                        r.teacherId,
                        "status",
                        r.status,
                        "version",
                        r.version,
                        "createdAt",
                        r.createdAt);
        v.put("memberCount", memberCount);
        if (Actor.current().role().equals("TEACHER")) v.put("joinCode", r.joinCode);
        else {
            v.put("enrollmentId", membership == null ? null : membership.enrollmentId.toString());
        }
        return v;
    }

    public Object list(int page, int size, boolean teacher, String semester, String status) {
        int offset = Views.offset(page, size);
        var q = new QueryWrapper<Classroom>();
        if (teacher) {
            Views.role("TEACHER");
            var ids = remote.call("class-staff-courses", () -> courses.staffIds(false));
            if (ids.isEmpty()) return Views.page(List.of(), page, size, 0);
            q.in("course_id", ids);
        } else {
            Views.role("STUDENT");
            var ids =
                    members
                            .selectList(
                                    new QueryWrapper<ClassMember>()
                                            .eq("student_id", Actor.current().id())
                                            .eq("status", "ACTIVE"))
                            .stream()
                            .map(m -> m.classId)
                            .toList();
            if (ids.isEmpty()) return Views.page(List.of(), page, size, 0);
            q.in("id", ids);
        }
        if (semester != null && !semester.isBlank())
            q.eq("semester", Views.text(semester, 40, "学期"));
        if (status != null && !status.isBlank()) {
            if (!Set.of("ACTIVE", "CLOSED").contains(status)) throw ApiException.bad("班级状态不合法");
            q.eq("status", status);
        }
        long total = rooms.selectCount(q);
        var rows =
                rooms.selectList(q.orderByDesc("id").last("LIMIT " + size + " OFFSET " + offset));
        var courseById = new HashMap<Long, Contracts.CourseValidation>();
        var counts = new HashMap<Long, Long>();
        var memberships = new HashMap<Long, ClassMember>();
        if (!rows.isEmpty()) {
            String ids =
                    rows.stream()
                            .map(r -> r.courseId)
                            .distinct()
                            .map(Object::toString)
                            .collect(java.util.stream.Collectors.joining(","));
            for (var c : remote.call("class-courses", () -> courses.courses(ids)))
                courseById.put(c.id(), c);
            var classes = rows.stream().map(r -> r.id).toList();
            members.selectMaps(
                            new QueryWrapper<ClassMember>()
                                    .select("class_id", "COUNT(*) AS n")
                                    .in("class_id", classes)
                                    .eq("status", "ACTIVE")
                                    .groupBy("class_id"))
                    .forEach(
                            row ->
                                    counts.put(
                                            ((Number) row.get("class_id")).longValue(),
                                            ((Number) row.get("n")).longValue()));
            if (!teacher)
                members.selectList(
                                new QueryWrapper<ClassMember>()
                                        .in("class_id", classes)
                                        .eq("student_id", Actor.current().id()))
                        .forEach(m -> memberships.put(m.classId, m));
        }
        return Views.page(
                rows.stream()
                        .map(
                                r -> {
                                    var c = courseById.get(r.courseId);
                                    if (c == null) throw ApiException.missing();
                                    return view(
                                            r,
                                            c,
                                            counts.getOrDefault(r.id, 0L),
                                            memberships.get(r.id));
                                })
                        .toList(),
                page,
                size,
                total);
    }

    public Object detail(long id) {
        var r = required(id, false);
        if (Actor.current().role().equals("TEACHER")) owner(r);
        else {
            Views.role("STUDENT");
            var m = member(id, Actor.current().id());
            if (m == null || !m.status.equals("ACTIVE")) throw ApiException.forbidden();
        }
        return view(r);
    }

    void fields(Classroom r, String name, String semester, String description) {
        r.name = Views.text(name, 100, "班级名称");
        r.semester = Views.text(semester, 40, "学期");
        r.description = Views.optional(description, 1000, "班级说明");
        if (r.description == null) r.description = "";
        r.updatedAt = Views.now();
    }

    static String code() {
        byte[] b = new byte[9];
        new SecureRandom().nextBytes(b);
        return HexFormat.of().formatHex(b).toUpperCase(Locale.ROOT);
    }

    public Object create(Create in) {
        Views.role("TEACHER");
        if (in.courseId() == null || in.courseId() < 1) throw ApiException.bad("请选择课程");
        var c = remote.call("class-course", () -> courses.course(in.courseId()));
        if (!c.canTeach()) throw ApiException.forbidden();
        if (!c.status().equals("PUBLISHED")) throw ApiException.conflict("请先发布课程再开设班级");
        Classroom r = new Classroom();
        r.courseId = in.courseId();
        r.teacherId = Actor.current().id();
        fields(r, in.name(), in.semester(), in.description());
        r.joinCode = code();
        r.status = "ACTIVE";
        r.version = 0;
        r.createdAt = r.updatedAt;
        return transaction.execute(
                _ -> {
                    rooms.insert(r);
                    return view(r, c);
                });
    }

    public Object update(long id, Write in) {
        var initial = required(id, false);
        var c = editor(initial);
        return transaction.execute(_ -> updateLocal(initial, in, c));
    }

    private Object updateLocal(Classroom initial, Write in, Contracts.CourseValidation c) {
        var r = locked(initial);
        Names.version(r.version, in.expectedVersion());
        if (!Set.of("ACTIVE", "CLOSED").contains(String.valueOf(in.status())))
            throw ApiException.bad("班级状态不合法");
        if (in.status().equals("ACTIVE") && !c.status().equals("PUBLISHED"))
            throw ApiException.conflict("归档课程不能开放班级");
        fields(r, in.name(), in.semester(), in.description());
        r.status = in.status();
        r.version++;
        rooms.updateById(r);
        return view(r, c);
    }

    public Object rotate(long id, int version) {
        var initial = required(id, false);
        var c = editor(initial);
        return transaction.execute(_ -> rotateLocal(initial, version, c));
    }

    private Object rotateLocal(Classroom initial, int version, Contracts.CourseValidation c) {
        var r = locked(initial);
        active(r, c);
        Names.version(r.version, version);
        r.joinCode = code();
        r.version++;
        r.updatedAt = Views.now();
        rooms.updateById(r);
        return view(r, c);
    }

    ClassMember add(Classroom r, long student) {
        var eq =
                new QueryWrapper<Enrollment>()
                        .eq("course_id", r.courseId)
                        .eq("student_id", student);
        var e = enrollments.selectOne(eq);
        if (e == null) {
            e = new Enrollment();
            e.courseId = r.courseId;
            e.studentId = student;
            e.joinedAt = e.createdAt = e.updatedAt = Views.now();
            try {
                enrollments.insert(e);
            } catch (DuplicateKeyException ex) {
                e = enrollments.selectOne(eq);
                if (e == null) throw ex;
            }
        }
        var m = member(r.id, student);
        if (m == null) {
            m = new ClassMember();
            m.classId = r.id;
            m.studentId = student;
            m.enrollmentId = e.id;
            m.joinedAt = Views.now();
            m.status = "ACTIVE";
            members.insert(m);
        } else if (m.status.equals("LEFT")) {
            m.status = "ACTIVE";
            m.joinedAt = Views.now();
            members.updateById(m);
        }
        return m;
    }

    public Object importMembers(long id, Import in) {
        var initial = required(id, false);
        var c = editor(initial);
        active(initial, c);
        var accounts = remote.call("class-students", () -> directory.students(in.usernames()));
        return membershipTransaction.execute(_ -> importLocal(initial, c, accounts));
    }

    private Object importLocal(
            Classroom initial,
            Contracts.CourseValidation c,
            List<Contracts.StudentAccount> accounts) {
        var r = locked(initial);
        active(r, c);
        int added = 0;
        for (var a : accounts) {
            var previous = member(r.id, a.id());
            if (previous == null || previous.status.equals("LEFT")) added++;
            add(r, a.id());
        }
        return Views.map("processed", accounts.size(), "added", added);
    }

    public Object join(Join in) {
        Views.role("STUDENT");
        String code = Views.text(in.code(), 24, "邀请码").toUpperCase(Locale.ROOT);
        var found = rooms.selectOne(new QueryWrapper<Classroom>().eq("join_code", code));
        if (found == null) throw ApiException.bad("邀请码无效");
        var c = course(found);
        active(found, c);
        return membershipTransaction.execute(_ -> joinLocal(found, code, c));
    }

    private Object joinLocal(Classroom initial, String code, Contracts.CourseValidation c) {
        var r = locked(initial);
        if (!r.joinCode.equals(code)) throw ApiException.conflict("邀请码已更新");
        active(r, c);
        add(r, Actor.current().id());
        return view(r, c);
    }

    public Object roster(long id, int page, int size) {
        var r = required(id, false);
        owner(r);
        int offset = Views.offset(page, size);
        var q = new QueryWrapper<ClassMember>().eq("class_id", id).eq("status", "ACTIVE");
        long total = members.selectCount(q);
        var rows =
                members.selectList(
                        q.orderByAsc("joined_at", "id")
                                .last("LIMIT " + size + " OFFSET " + offset));
        var names = Names.read(rows.stream().map(m -> m.studentId).toList(), directory, remote);
        return Views.page(
                rows.stream()
                        .map(
                                m ->
                                        Views.map(
                                                "id",
                                                m.id,
                                                "studentId",
                                                m.studentId,
                                                "displayName",
                                                names.getOrDefault(m.studentId, "学生"),
                                                "enrollmentId",
                                                m.enrollmentId,
                                                "joinedAt",
                                                m.joinedAt))
                        .toList(),
                page,
                size,
                total);
    }

    public void remove(long id, long memberId) {
        var initial = required(id, false);
        editor(initial);
        transaction.executeWithoutResult(
                _ -> {
                    locked(initial);
                    var m = members.lock(memberId);
                    if (m == null || m.classId != id) throw ApiException.missing();
                    m.status = "LEFT";
                    members.updateById(m);
                });
    }
}
