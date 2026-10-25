package org.hanwen.assignment.service;

import org.hanwen.assignment.model.Task;
import org.hanwen.common.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

@Service
public class BatchTaskService {
    final AssignmentService assignment;
    final TransactionTemplate transaction;

    public BatchTaskService(AssignmentService a, PlatformTransactionManager manager) {
        assignment = a;
        transaction = new TransactionTemplate(manager);
    }

    public record Item(Long id, String status, String updatedAt) {}

    public record Write(Long courseId, String action, List<Item> items) {}

    public Object apply(Write in) {
        Views.role("TEACHER");
        if (in.courseId() == null
                || in.courseId() < 1
                || in.action() == null
                || !Set.of("PUBLISH", "CLOSE").contains(in.action()))
            throw ApiException.bad("课程或批量操作无效");
        if (in.items() == null || in.items().isEmpty() || in.items().size() > 50)
            throw ApiException.bad("每批请选择1至50份作业");
        var ids = new HashSet<Long>();
        for (var x : in.items())
            if (x == null || x.id() == null || x.id() < 1 || !ids.add(x.id()))
                throw ApiException.bad("作业编号重复或无效");
        var c = assignment.course(in.courseId());
        if (!c.canTeach()) throw ApiException.forbidden();
        if (!c.status().equals("PUBLISHED")) throw ApiException.conflict("归档课程不能批量改变作业");
        var ordered = in.items().stream().sorted(Comparator.comparing(Item::id)).toList();
        boolean publish = in.action().equals("PUBLISH");
        return transaction.execute(_ -> applyLocal(in, ordered, publish));
    }

    private Object applyLocal(Write in, List<Item> ordered, boolean publish) {
        String required = publish ? "DRAFT" : "PUBLISHED";
        var locked = new ArrayList<Task>();
        for (var x : ordered) {
            var t = assignment.tasks().lock(x.id());
            if (t == null) throw ApiException.missing();
            if (!t.courseId.equals(in.courseId())) throw ApiException.forbidden();
            java.time.LocalDateTime expected;
            try {
                expected = Views.deadline(x.updatedAt());
            } catch (ApiException ex) {
                throw ApiException.bad("作业更新时间格式无效，请刷新列表");
            }
            if (!required.equals(x.status())
                    || !required.equals(t.status)
                    || expected == null
                    || !expected.equals(t.updatedAt))
                throw ApiException.conflict("所选作业已更新，请刷新后重新核对");
            if (publish && t.deadlineAt != null && !t.deadlineAt.isAfter(Views.now()))
                throw ApiException.bad("所选作业已截止，不能发布");
            locked.add(t);
        }
        // All rows are locked and checked before any state change; a failed batch is rolled back in
        // full.
        var result = locked.stream().map(t -> assignment.transitionLocked(t, publish)).toList();
        return Views.map("changed", result.size(), "items", result);
    }
}
