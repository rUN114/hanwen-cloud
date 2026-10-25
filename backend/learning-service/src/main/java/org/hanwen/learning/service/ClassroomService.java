package org.hanwen.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.common.*;
import org.hanwen.learning.client.CourseClient;
import org.hanwen.learning.mapper.*;
import org.hanwen.learning.model.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

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

    public ClassroomService(
            ClassroomMapper r,
            ClassMemberMapper m,
            EnrollmentMapper e,
            CourseClient c,
            DirectoryClient d,
            Remote rm) {
        rooms = r;
        members = m;
        enrollments = e;
        courses = c;
        directory = d;
        remote = rm;
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

    void editor(Classroom r) {
        owner(r);
        if (!course(r).canTeach()) throw ApiException.forbidden();
    }

    void active(Classroom r) {
        if (!r.status.equals("ACTIVE") || !course(r).status().equals("PUBLISHED"))
            throw ApiException.conflict("班级已结课或课程已归档，不能加入学生");
    }

    ClassMember member(long id, long student) {
        return members.selectOne(
                new QueryWrapper<ClassMember>().eq("class_id", id).eq("student_id", student));
    }

    Map<String, Object> view(Classroom r) {
        var c = course(r);
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
        v.put(
                "memberCount",
                members.selectCount(
                        new QueryWrapper<ClassMember>()
                                .eq("class_id", r.id)
                                .eq("status", "ACTIVE")));
        if (Actor.current().role().equals("TEACHER")) v.put("joinCode", r.joinCode);
        else {
            var m = member(r.id, Actor.current().id());
            v.put("enrollmentId", m == null ? null : m.enrollmentId.toString());
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
        return Views.page(
                rooms
                        .selectList(q.orderByDesc("id").last("LIMIT " + size + " OFFSET " + offset))
                        .stream()
                        .map(this::view)
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

    @Transactional
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
        rooms.insert(r);
        return view(r);
    }

    @Transactional
    public Object update(long id, Write in) {
        var r = required(id, true);
        editor(r);
        Names.version(r.version, in.expectedVersion());
        if (!Set.of("ACTIVE", "CLOSED").contains(String.valueOf(in.status())))
            throw ApiException.bad("班级状态不合法");
        if (in.status().equals("ACTIVE") && !course(r).status().equals("PUBLISHED"))
            throw ApiException.conflict("归档课程不能开放班级");
        fields(r, in.name(), in.semester(), in.description());
        r.status = in.status();
        r.version++;
        rooms.updateById(r);
        return view(r);
    }

    @Transactional
    public Object rotate(long id, int version) {
        var r = required(id, true);
        editor(r);
        active(r);
        Names.version(r.version, version);
        r.joinCode = code();
        r.version++;
        r.updatedAt = Views.now();
        rooms.updateById(r);
        return view(r);
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Object importMembers(long id, Import in) {
        var r = required(id, true);
        editor(r);
        active(r);
        var accounts = remote.call("class-students", () -> directory.students(in.usernames()));
        int added = 0;
        for (var a : accounts) {
            var previous = member(id, a.id());
            if (previous == null || previous.status.equals("LEFT")) added++;
            add(r, a.id());
        }
        return Views.map("processed", accounts.size(), "added", added);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Object join(Join in) {
        Views.role("STUDENT");
        String code = Views.text(in.code(), 24, "邀请码").toUpperCase(Locale.ROOT);
        var found = rooms.selectOne(new QueryWrapper<Classroom>().eq("join_code", code));
        if (found == null) throw ApiException.bad("邀请码无效");
        var r = required(found.id, true);
        if (!r.joinCode.equals(code)) throw ApiException.conflict("邀请码已更新");
        active(r);
        add(r, Actor.current().id());
        return view(r);
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

    @Transactional
    public void remove(long id, long memberId) {
        var r = required(id, true);
        editor(r);
        var m = members.lock(memberId);
        if (m == null || m.classId != id) throw ApiException.missing();
        m.status = "LEFT";
        members.updateById(m);
    }
}
