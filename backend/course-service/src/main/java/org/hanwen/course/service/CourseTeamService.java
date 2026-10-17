package org.hanwen.course.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.common.*;
import org.hanwen.course.mapper.*;
import org.hanwen.course.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class CourseTeamService {
    final CourseService course;
    final CourseTeamMapper members;
    final CourseTeamStateMapper states;
    final CourseTeamEventMapper events;
    final DirectoryClient directory;
    final Remote remote;

    public CourseTeamService(
            CourseService c,
            CourseTeamMapper m,
            CourseTeamStateMapper s,
            CourseTeamEventMapper e,
            DirectoryClient d,
            Remote r) {
        course = c;
        members = m;
        states = s;
        events = e;
        directory = d;
        remote = r;
    }

    public record MemberWrite(String username, String role, Integer expectedVersion) {}

    public record Remove(Integer expectedVersion) {}

    public record Transfer(
            Long targetId, Boolean retainPrevious, String reason, Integer expectedVersion) {}

    CourseTeam member(long id, long user) {
        return members.selectOne(
                new QueryWrapper<CourseTeam>().eq("course_id", id).eq("user_id", user));
    }

    int version(long id) {
        var s = states.selectById(id);
        return s == null ? 0 : s.version;
    }

    void check(Course c, Integer expected) {
        course.principal(c);
        if (c.status.equals("ARCHIVED")) throw ApiException.conflict("归档课程团队只读");
        Names.version(version(c.id), expected);
    }

    void bump(long id) {
        var s = states.selectById(id);
        if (s == null) {
            s = new CourseTeamState();
            s.id = id;
            s.version = 1;
            states.insert(s);
        } else {
            s.version++;
            states.updateById(s);
        }
    }

    void event(long id, long target, String action, String detail) {
        var e = new CourseTeamEvent();
        e.courseId = id;
        e.actorId = Actor.current().id();
        e.targetId = target;
        e.action = action;
        e.detail = detail;
        e.createdAt = Views.now();
        events.insert(e);
    }

    void put(long courseId, long user, String role, String status) {
        var m = member(courseId, user);
        boolean fresh = m == null;
        if (fresh) {
            m = new CourseTeam();
            m.courseId = courseId;
            m.userId = user;
            m.createdAt = Views.now();
        }
        m.role = role;
        m.status = status;
        m.updatedAt = Views.now();
        if (fresh) members.insert(m);
        else members.updateById(m);
    }

    public Object list(long id) {
        Views.role("TEACHER");
        var c = course.required(id);
        if (!course.staff(c)) throw ApiException.forbidden();
        var rows =
                members.selectList(
                        new QueryWrapper<CourseTeam>()
                                .eq("course_id", id)
                                .eq("status", "ACTIVE")
                                .orderByAsc("id"));
        var log =
                events.selectList(
                        new QueryWrapper<CourseTeamEvent>()
                                .eq("course_id", id)
                                .orderByDesc("id")
                                .last("LIMIT 200"));
        var ids = new HashSet<Long>();
        ids.add(c.teacherId);
        rows.forEach(m -> ids.add(m.userId));
        log.forEach(
                e -> {
                    ids.add(e.actorId);
                    ids.add(e.targetId);
                });
        var names = Names.read(new ArrayList<>(ids), directory, remote);
        var items = new ArrayList<Object>();
        items.add(
                Views.map(
                        "userId",
                        c.teacherId,
                        "displayName",
                        names.getOrDefault(c.teacherId, "教师"),
                        "role",
                        "OWNER"));
        rows.forEach(
                m ->
                        items.add(
                                Views.map(
                                        "userId",
                                        m.userId,
                                        "displayName",
                                        names.getOrDefault(m.userId, "教师"),
                                        "role",
                                        m.role,
                                        "updatedAt",
                                        m.updatedAt)));
        return Views.map(
                "courseId",
                id,
                "version",
                version(id),
                "accessRole",
                course.accessRole(c),
                "members",
                items,
                "events",
                log.stream()
                        .map(
                                e ->
                                        Views.map(
                                                "id",
                                                e.id,
                                                "actorName",
                                                names.getOrDefault(e.actorId, "教师"),
                                                "targetName",
                                                names.getOrDefault(e.targetId, "教师"),
                                                "action",
                                                e.action,
                                                "detail",
                                                e.detail,
                                                "createdAt",
                                                e.createdAt))
                        .toList(),
                "eventsLimited",
                log.size() == 200);
    }

    @Transactional
    public Object save(long id, MemberWrite in) {
        var c = course.locked(id);
        check(c, in.expectedVersion());
        if (!Set.of("CO_TEACHER", "ASSISTANT").contains(String.valueOf(in.role())))
            throw ApiException.bad("请选择协同教师或助教");
        String username = Views.text(in.username(), 50, "教师账号");
        var account =
                remote.call("team-teacher", () -> directory.teachers(List.of(username))).getFirst();
        if (account.id() == c.teacherId) throw ApiException.bad("主讲身份不能在协作成员中修改");
        var existing = member(id, account.id());
        if ((existing == null || !existing.status.equals("ACTIVE"))
                && members.selectCount(
                                new QueryWrapper<CourseTeam>()
                                        .eq("course_id", id)
                                        .eq("status", "ACTIVE"))
                        >= 10) throw ApiException.bad("每门课程最多10位协作成员");
        if (existing != null && existing.status.equals("ACTIVE") && existing.role.equals(in.role()))
            throw ApiException.conflict("该成员已具有此身份");
        put(id, account.id(), in.role(), "ACTIVE");
        event(id, account.id(), "MEMBER_SET", in.role());
        bump(id);
        return list(id);
    }

    @Transactional
    public Object remove(long id, long user, Remove in) {
        var c = course.locked(id);
        check(c, in.expectedVersion());
        var m = member(id, user);
        if (m == null || !m.status.equals("ACTIVE")) throw ApiException.conflict("成员已移除，请刷新");
        m.status = "REMOVED";
        m.updatedAt = Views.now();
        members.updateById(m);
        event(id, user, "MEMBER_REMOVED", m.role);
        bump(id);
        return list(id);
    }

    @Transactional
    public Object transfer(long id, Transfer in) {
        var c = course.locked(id);
        check(c, in.expectedVersion());
        if (in.targetId() == null || in.retainPrevious() == null || in.targetId() == c.teacherId)
            throw ApiException.bad("请选择交接教师与原主讲安排");
        var m = member(id, in.targetId());
        if (m == null || !m.status.equals("ACTIVE") || !m.role.equals("CO_TEACHER"))
            throw ApiException.bad("须交接给当前协同教师");
        var names = Names.read(List.of(in.targetId()), directory, remote);
        if (!names.containsKey(in.targetId())) throw ApiException.bad("目标教师不存在");
        // Resolve an enabled teacher by account id, rather than trusting display names or a client
        // role.
        var target = remote.call("team-target", () -> directory.teacher(in.targetId()));
        if (!target.status().equals("ENABLED")) throw ApiException.bad("目标教师已禁用");
        String reason = Views.text(in.reason(), 500, "交接说明");
        long previous = c.teacherId;
        m.status = "REMOVED";
        m.updatedAt = Views.now();
        members.updateById(m);
        put(id, previous, "CO_TEACHER", in.retainPrevious() ? "ACTIVE" : "REMOVED");
        c.teacherId = in.targetId();
        c.updatedAt = Views.now();
        course.savePrincipal(c);
        event(
                id,
                in.targetId(),
                "TRANSFER",
                reason + "；原主讲" + (in.retainPrevious() ? "保留为协同教师" : "退出团队"));
        bump(id);
        return Views.map(
                "courseId",
                id,
                "teacherId",
                c.teacherId,
                "version",
                version(id),
                "retained",
                in.retainPrevious());
    }
}
