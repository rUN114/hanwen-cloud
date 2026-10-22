package org.hanwen.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.common.*;
import org.hanwen.learning.client.CourseClient;
import org.hanwen.learning.mapper.*;
import org.hanwen.learning.model.*;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
public class InsightsService {
    final InsightsMapper mapper;
    final ClassroomMapper rooms;
    final ClassMemberMapper members;
    final CourseClient courses;
    final DirectoryClient directory;
    final Remote remote;

    public InsightsService(
            InsightsMapper m,
            ClassroomMapper c,
            ClassMemberMapper cm,
            CourseClient cc,
            DirectoryClient d,
            Remote r) {
        mapper = m;
        rooms = c;
        members = cm;
        courses = cc;
        directory = d;
        remote = r;
    }

    static long number(Object x) {
        return x instanceof Number n ? n.longValue() : 0;
    }

    static LocalDateTime date(Object x) {
        return x instanceof LocalDateTime d
                ? d
                : x instanceof java.sql.Timestamp t ? t.toLocalDateTime() : null;
    }

    static Map<String, Object> safe(Map<String, Object> row) {
        var out = new LinkedHashMap<String, Object>();
        for (var entry : row.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            LocalDateTime time = date(value);
            if (key.endsWith("Id") && value != null) {
                value = value.toString();
            } else if (time != null) {
                value = time.atOffset(ZoneOffset.ofHours(8)).toString();
            } else if (value instanceof java.math.BigDecimal decimal) {
                if (key.endsWith("Percent") || key.equals("percent")) {
                    // Percentage queries already use ROUND(..., 2).
                    value = decimal.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
                } else {
                    value = decimal.longValueExact();
                }
            }
            out.put(key, value);
        }
        return out;
    }

    public Object course(long id, Long classId) {
        Views.role("TEACHER");
        var course = remote.call("insights-course", () -> courses.course(id));
        if (!course.canGrade()) throw ApiException.forbidden();
        var roomsList =
                rooms.selectList(
                        new QueryWrapper<Classroom>()
                                .eq("course_id", id)
                                .orderByAsc("id")
                                .last("LIMIT 101"));
        if (roomsList.size() > 100) throw ApiException.bad("课程学舍超过100个，请分课程管理");
        if (classId != null && roomsList.stream().noneMatch(c -> c.id.equals(classId)))
            throw ApiException.bad("所选学舍不属于此课程");
        var roomIds = roomsList.stream().map(c -> c.id).toList();
        var ms =
                roomIds.isEmpty()
                        ? List.<ClassMember>of()
                        : members.selectList(
                                new QueryWrapper<ClassMember>()
                                        .in("class_id", roomIds)
                                        .eq("status", "ACTIVE")
                                        .last("LIMIT 50001"));
        if (ms.size() > 50000) throw ApiException.bad("班级成员超过处理上限");
        var all = mapper.learners(id);
        if (all.size() > 500) throw ApiException.bad("学情单课程最多500位学生，请分班分课程管理");
        var map = new HashMap<Long, Map<String, Object>>();
        for (var row : mapper.practice(id)) map.put(number(row.get("studentId")), row);
        Set<Long> selected =
                classId == null
                        ? null
                        : new HashSet<>(
                                ms.stream()
                                        .filter(m -> m.classId.equals(classId))
                                        .map(m -> m.studentId)
                                        .toList());
        var rows =
                all.stream()
                        .filter(
                                r ->
                                        selected == null
                                                || selected.contains(number(r.get("studentId"))))
                        .toList();
        var names =
                Names.read(
                        rows.stream().map(r -> number(r.get("studentId"))).toList(),
                        directory,
                        remote);
        var learners = new ArrayList<Object>();
        var now = Views.now();
        for (var r : rows) {
            long student = number(r.get("studentId"));
            var p = map.get(student);
            var v = safe(r);
            v.put("displayName", names.getOrDefault(student, "学生"));
            v.put("chapters", course.chapterCount());
            v.put(
                    "practice",
                    p == null
                            ? Views.map(
                                    "latestAttempts",
                                    0,
                                    "pending",
                                    0,
                                    "graded",
                                    0,
                                    "averagePercent",
                                    null)
                            : safe(p));
            v.put(
                    "classes",
                    ms.stream()
                            .filter(m -> m.studentId == student)
                            .map(m -> m.classId.toString())
                            .toList());
            var last = date(r.get("lastRecordedAt"));
            v.put("inactive14Days", last != null && last.isBefore(now.minusDays(14)));
            learners.add(v);
        }
        var comparison = new ArrayList<Object>();
        for (var room : roomsList) {
            var ids =
                    ms.stream()
                            .filter(m -> m.classId.equals(room.id))
                            .map(m -> m.studentId)
                            .collect(java.util.stream.Collectors.toSet());
            var enrolled =
                    all.stream().filter(r -> ids.contains(number(r.get("studentId")))).toList();
            int completed = enrolled.stream().mapToInt(r -> (int) number(r.get("completed"))).sum();
            var scores =
                    enrolled.stream()
                            .map(r -> map.get(number(r.get("studentId"))))
                            .filter(Objects::nonNull)
                            .map(r -> r.get("averagePercent"))
                            .filter(Objects::nonNull)
                            .mapToDouble(v -> Double.parseDouble(v.toString()))
                            .toArray();
            comparison.add(
                    Views.map(
                            "id",
                            room.id,
                            "name",
                            room.name,
                            "semester",
                            room.semester,
                            "status",
                            room.status,
                            "students",
                            enrolled.size(),
                            "completed",
                            completed,
                            "completionPercent",
                            enrolled.isEmpty() || course.chapterCount() == 0
                                    ? null
                                    : Math.round(
                                                    completed
                                                            * 10000d
                                                            / enrolled.size()
                                                            / course.chapterCount())
                                            / 100d,
                            "gradedStudents",
                            scores.length,
                            "averagePercent",
                            scores.length == 0
                                    ? null
                                    : Math.round(Arrays.stream(scores).average().orElse(0) * 100)
                                            / 100d));
        }
        var ids =
                selected == null
                        ? null
                        : rows.stream().map(r -> number(r.get("studentId"))).toList();
        boolean empty = ids != null && ids.isEmpty();
        var points = empty ? List.<Map<String, Object>>of() : mapper.points(id, ids);
        if (points.size() > 200) throw ApiException.bad("知识点超过200类，请统一标签或分课程管理");
        var from =
                now.toLocalDate()
                        .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .minusWeeks(7)
                        .atStartOfDay();
        var weeks = empty ? List.<Map<String, Object>>of() : mapper.weeks(id, from, ids);
        return Views.map(
                "courseId",
                id,
                "classId",
                classId,
                "learners",
                learners,
                "classes",
                comparison,
                "knowledgePoints",
                points.stream().map(InsightsService::safe).toList(),
                "weeks",
                weeks.stream().map(InsightsService::safe).toList(),
                "from",
                from,
                "calculatedAt",
                now,
                "scope",
                "知识点按每人每卷最近一次已完成批阅的答卷汇总；最近一次待批时该卷不进入知识点统计。班级均分先算学生的已批卷均分，再按学生平均；趋势包含重复尝试。完成记录不等于掌握程度。活动只含选课、章节完成修改、札记修改、练习交卷，不统计页面停留和线下学习。");
    }
}
