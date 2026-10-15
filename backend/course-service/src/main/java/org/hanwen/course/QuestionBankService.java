package org.hanwen.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.hanwen.common.*;
import org.hanwen.course.mapper.BankQuestionMapper;
import org.hanwen.course.model.BankQuestion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionBankService {
    final BankQuestionMapper mapper;
    public QuestionBankService(BankQuestionMapper mapper) { this.mapper = mapper; }
    public record Write(String questionType, String prompt, String optionA, String optionB,
            String optionC, String optionD, String correctOption, String explanation,
            Integer points, String topic, Integer expectedVersion) {}
    public record State(String status, Integer expectedVersion) {}

    BankQuestion owned(long id, boolean lock) {
        Views.role("TEACHER");
        var q = lock ? mapper.lock(id) : mapper.selectById(id);
        if (q == null) throw ApiException.missing();
        if (q.teacherId != Actor.current().id()) throw ApiException.forbidden();
        return q;
    }

    Object view(BankQuestion q) {
        return Views.map("id", q.id, "questionType", q.questionType, "prompt", q.prompt,
                "optionA", q.optionA, "optionB", q.optionB, "optionC", q.optionC, "optionD", q.optionD,
                "correctOption", q.correctOption, "explanation", q.explanation, "points", q.points,
                "topic", q.topic, "status", q.status, "version", q.version, "updatedAt", q.updatedAt);
    }

    public Object list(String keyword, String type, String topic, String status, int page, int size) {
        Views.role("TEACHER");
        int offset = Views.offset(page, size);
        var w = new QueryWrapper<BankQuestion>().eq("teacher_id", Actor.current().id());
        if (keyword != null && !keyword.isBlank()) w.like("prompt", Views.text(keyword, 100, "关键词"));
        if (type != null && !type.isBlank()) w.eq("question_type", QuestionRules.type(type));
        if (topic != null && !topic.isBlank()) w.eq("topic", Views.text(topic, 60, "主题"));
        if (status != null && !java.util.Set.of("ACTIVE", "ARCHIVED").contains(status))
            throw ApiException.bad("题库状态不合法");
        w.eq("status", status == null ? "ACTIVE" : status);
        long total = mapper.selectCount(w);
        return Views.page(mapper.selectList(w.orderByDesc("id").last("LIMIT " + size + " OFFSET " + offset))
                .stream().map(this::view).toList(), page, size, total);
    }

    @Transactional
    public Object write(Long id, Write in) {
        Views.role("TEACHER");
        var q = id == null ? new BankQuestion() : owned(id, true);
        if (id != null) {
            Names.version(in.expectedVersion(), q.version);
            if (!q.status.equals("ACTIVE")) throw ApiException.conflict("请先恢复归档题目");
            q.version++;
        } else {
            q.teacherId = Actor.current().id(); q.version = 0; q.status = "ACTIVE"; q.createdAt = Views.now();
        }
        q.questionType = QuestionRules.type(in.questionType());
        q.points = QuestionRules.points(in.points());
        q.prompt = Views.text(in.prompt(), 2000, "题目");
        boolean tf = q.questionType.equals("TRUE_FALSE"), manual = QuestionRules.manual(q.questionType);
        q.optionA = manual ? "" : tf ? "正确" : Views.text(in.optionA(), 500, "选项A");
        q.optionB = manual ? "" : tf ? "错误" : Views.text(in.optionB(), 500, "选项B");
        q.optionC = manual ? "" : tf ? "" : Views.text(in.optionC(), 500, "选项C");
        q.optionD = manual ? "" : tf ? "" : Views.text(in.optionD(), 500, "选项D");
        q.correctOption = QuestionRules.choice(q.questionType, in.correctOption(), true);
        q.explanation = Views.optional(in.explanation(), 2000, "解析");
        q.topic = Views.optional(in.topic(), 60, "主题");
        q.updatedAt = Views.now();
        if (id == null) mapper.insert(q); else mapper.updateById(q);
        return view(q);
    }

    @Transactional
    public Object state(long id, State in) {
        var q = owned(id, true);
        Names.version(in.expectedVersion(), q.version);
        if (!java.util.Set.of("ACTIVE", "ARCHIVED").contains(String.valueOf(in.status())))
            throw ApiException.bad("题库状态不合法");
        q.status = in.status(); q.version++; q.updatedAt = Views.now(); mapper.updateById(q);
        return view(q);
    }
}
