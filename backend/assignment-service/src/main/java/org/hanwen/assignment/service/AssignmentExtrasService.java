package org.hanwen.assignment.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.assignment.mapper.*;
import org.hanwen.assignment.model.*;
import org.hanwen.common.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AssignmentExtrasService {
    final AssignmentService assignment;
    final DraftMapper drafts;
    final TaskMapper tasks;
    final SubmissionMapper submissions;
    final GradeHistoryMapper history;
    final DirectoryClient directory;
    final Remote remote;

    public AssignmentExtrasService(
            AssignmentService a,
            DraftMapper d,
            TaskMapper t,
            SubmissionMapper s,
            GradeHistoryMapper h,
            DirectoryClient n,
            Remote r) {
        assignment = a;
        drafts = d;
        tasks = t;
        submissions = s;
        history = h;
        directory = n;
        remote = r;
    }

    public record Write(String answerText, Integer expectedVersion) {}

    Draft mine(long id) {
        return drafts.selectOne(
                new QueryWrapper<Draft>()
                        .eq("assignment_id", id)
                        .eq("student_id", Actor.current().id()));
    }

    static Object view(Draft d) {
        return d == null
                ? null
                : Views.map(
                        "answerText", d.answerText, "version", d.version, "updatedAt", d.updatedAt);
    }

    public Object read(long id) {
        Views.role("STUDENT");
        assignment.student(assignment.task(id), false);
        return view(mine(id));
    }

    @Transactional
    public Object save(long id, Write in) {
        Views.role("STUDENT");
        var t = tasks.lock(id);
        if (t == null) throw ApiException.missing();
        assignment.student(t, true);
        assignment.writable(t);
        var sub =
                submissions.selectOne(
                        new QueryWrapper<Submission>()
                                .eq("assignment_id", id)
                                .eq("student_id", Actor.current().id()));
        if (sub != null && sub.status.equals("GRADED")) throw ApiException.conflict("已评分答案不再接收草稿");
        if (in.answerText() == null
                || in.answerText().codePointCount(0, in.answerText().length()) > 10000)
            throw ApiException.bad("草稿不能超过10000字");
        var d = mine(id);
        if (d == null) {
            if (in.expectedVersion() != null && in.expectedVersion() != -1)
                throw ApiException.conflict("草稿已变化，请刷新");
            d = new Draft();
            d.assignmentId = id;
            d.studentId = Actor.current().id();
            d.version = 0;
        } else {
            Names.version(in.expectedVersion(), d.version);
            d.version++;
        }
        d.answerText = in.answerText();
        d.updatedAt = Views.now();
        if (d.id == null) drafts.insert(d);
        else drafts.updateById(d);
        return view(d);
    }

    public Object history(long id, int page, int size) {
        assignment.submissionDetail(id);
        int offset = Views.offset(page, size);
        var q = new QueryWrapper<GradeHistory>().eq("submission_id", id);
        long total = history.selectCount(q);
        var rows =
                history.selectList(
                        q.orderByDesc("submission_version")
                                .last("LIMIT " + size + " OFFSET " + offset));
        var names = Names.read(rows.stream().map(h -> h.teacherId).toList(), directory, remote);
        return Views.page(
                rows.stream()
                        .map(
                                h ->
                                        Views.map(
                                                "id",
                                                h.id,
                                                "submissionVersion",
                                                h.submissionVersion,
                                                "teacherName",
                                                names.getOrDefault(h.teacherId, "教师"),
                                                "score",
                                                h.score,
                                                "rubricScores",
                                                Rubrics.scores(h.rubricScores),
                                                "comment",
                                                h.comment,
                                                "gradedAt",
                                                h.gradedAt))
                        .toList(),
                page,
                size,
                total);
    }
}
