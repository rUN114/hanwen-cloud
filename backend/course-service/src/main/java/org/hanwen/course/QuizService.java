package org.hanwen.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.common.*;
import org.hanwen.course.mapper.*;
import org.hanwen.course.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class QuizService {
    final CourseService course;
    final QuizMapper quizzes;
    final QuizQuestionMapper questions;
    final QuestionBankService bank;

    public QuizService(CourseService c, QuizMapper q, QuizQuestionMapper i, QuestionBankService bank) {
        course = c;
        quizzes = q;
        questions = i;
        this.bank = bank;
    }

    public record Write(
            Long chapterId, String title, String instructions, Integer expectedVersion,
            String opensAt, String closesAt, Integer timeLimitMinutes, Integer maxAttempts) {}

    public record QuestionWrite(
            String prompt,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            String correctOption,
            String explanation,
            Integer sortNo,
            Integer expectedVersion,
            String questionType, Integer points, String knowledgePoint) {}

    public record ImportQuestion(Long bankId, Integer bankVersion, Integer expectedVersion) {}

    public record State(String status, Integer expectedVersion) {}

    Quiz required(long id) {
        var q = quizzes.selectById(id);
        if (q == null) throw ApiException.missing();
        return q;
    }

    Quiz ownedLock(long id) {
        var initial = required(id);
        var c = course.locked(initial.courseId);
        course.owner(c);
        if (c.status.equals("ARCHIVED")) throw ApiException.conflict("归档课程练习只读");
        var q = quizzes.lock(id);
        return q;
    }

    List<QuizQuestion> items(long id) {
        return questions.selectList(
                new QueryWrapper<QuizQuestion>().eq("quiz_id", id).orderByAsc("sort_no"));
    }

    Map<String, Object> view(Quiz q) {
        var v =
                Views.map(
                        "id",
                        q.id,
                        "courseId",
                        q.courseId,
                        "chapterId",
                        q.chapterId,
                        "title",
                        q.title,
                        "instructions",
                        q.instructions,
                        "status",
                        q.status,
                        "version",
                        q.version,
                        "createdAt",
                        q.createdAt);
        v.put(
                "questionCount",
                questions.selectCount(new QueryWrapper<QuizQuestion>().eq("quiz_id", q.id)));
        v.putAll(Views.map("opensAt", q.opensAt, "closesAt", q.closesAt,
                "timeLimitMinutes", q.timeLimitMinutes, "maxAttempts", q.maxAttempts));
        v.put("totalPoints", items(q.id).stream().mapToInt(x -> x.points).sum());
        return v;
    }

    Object questionView(QuizQuestion q, boolean owner) {
        var v =
                Views.map(
                        "id", q.id, "prompt", q.prompt, "optionA", q.optionA, "optionB", q.optionB,
                        "optionC", q.optionC, "optionD", q.optionD, "sortNo", q.sortNo,
                        "questionType", q.questionType, "points", q.points, "knowledgePoint", q.knowledgePoint);
        if (owner) {
            v.put("correctOption", q.correctOption);
            v.put("explanation", q.explanation);
            v.put("bankId", Views.map("id", q.bankId).get("id"));
            v.put("bankVersion", q.bankVersion);
        }
        return v;
    }

    public Object list(long id, Long chapterId, int page, int size) {
        var c = course.required(id);
        course.readable(c, true);
        if (chapterId != null) course.chapter(id, chapterId);
        int offset = Views.offset(page, size);
        var w = new QueryWrapper<Quiz>().eq("course_id", id);
        if (chapterId != null) w.eq("chapter_id", chapterId);
        if (Actor.current().role().equals("STUDENT")) w.ne("status", "DRAFT");
        long total = quizzes.selectCount(w);
        return Views.page(
                quizzes
                        .selectList(w.orderByDesc("id").last("LIMIT " + size + " OFFSET " + offset))
                        .stream()
                        .map(this::view)
                        .toList(),
                page,
                size,
                total);
    }

    public Object detail(long id) {
        var q = required(id);
        var c = course.required(q.courseId);
        course.readable(c, true);
        boolean owner =
                course.staff(c);
        if (!owner && q.status.equals("DRAFT")) throw ApiException.forbidden();
        var v = view(q);
        // Student questions are released only after starting a server-timed attempt.
        if (owner) v.put("questions", items(id).stream().map(x -> questionView(x, true)).toList());
        return v;
    }

    @Transactional
    public Object write(long courseId, Long quizId, Write in) {
        var c = course.locked(courseId);
        course.owner(c);
        if (c.status.equals("ARCHIVED")) throw ApiException.conflict("归档课程练习只读");
        if (in.chapterId() == null) throw ApiException.bad("请选择章节");
        course.chapter(courseId, in.chapterId());
        Quiz q;
        if (quizId == null) {
            q = new Quiz();
            q.courseId = courseId;
            q.chapterId = in.chapterId();
            q.status = "DRAFT";
            q.version = 0;
            q.createdAt = Views.now();
        } else {
            q = quizzes.lock(quizId);
            if (q == null || q.courseId != courseId) throw ApiException.missing();
            Names.version(in.expectedVersion(), q.version);
            if (!q.status.equals("DRAFT")) throw ApiException.conflict("已发布练习的内容冻结");
            if (!q.chapterId.equals(in.chapterId())) throw ApiException.bad("练习不能移换章节");
            q.version++;
        }
        q.title = Views.text(in.title(), 100, "练习标题");
        q.instructions = Views.optional(in.instructions(), 2000, "练习说明");
        if (q.instructions == null) q.instructions = "";
        q.updatedAt = Views.now();
        q.opensAt = Views.deadline(in.opensAt());
        q.closesAt = Views.deadline(in.closesAt());
        if (q.opensAt != null && q.closesAt != null && !q.opensAt.isBefore(q.closesAt))
            throw ApiException.bad("开放时间须早于截止时间");
        q.timeLimitMinutes = in.timeLimitMinutes() == null ? 0 : in.timeLimitMinutes();
        q.maxAttempts = in.maxAttempts() == null ? 1 : in.maxAttempts();
        if (q.timeLimitMinutes < 0 || q.timeLimitMinutes > 180 || q.maxAttempts < 1 || q.maxAttempts > 5)
            throw ApiException.bad("限时须为0至180分钟，尝试次数须为1至5");
        if (quizId == null) quizzes.insert(q);
        else quizzes.updateById(q);
        return view(q);
    }

    @Transactional
    public Object question(long id, Long questionId, QuestionWrite in) {
        var q = ownedLock(id);
        Names.version(in.expectedVersion(), q.version);
        if (!q.status.equals("DRAFT")) throw ApiException.conflict("只能编辑草稿练习");
        QuizQuestion x;
        if (questionId == null) {
            if (questions.selectCount(new QueryWrapper<QuizQuestion>().eq("quiz_id", id)) >= 100)
                throw ApiException.bad("每份练习最多100题");
            x = new QuizQuestion();
            x.quizId = id;
        } else {
            x = questions.lock(questionId);
            if (x == null || x.quizId != id) throw ApiException.missing();
        }
        x.prompt = Views.text(in.prompt(), 2000, "题目");
        x.questionType = QuestionRules.type(in.questionType());
        x.points = QuestionRules.points(in.points());
        boolean tf = x.questionType.equals("TRUE_FALSE"), manual = QuestionRules.manual(x.questionType);
        x.optionA = manual ? "" : tf ? "正确" : Views.text(in.optionA(), 500, "选项A");
        x.optionB = manual ? "" : tf ? "错误" : Views.text(in.optionB(), 500, "选项B");
        x.optionC = manual ? "" : tf ? "" : Views.text(in.optionC(), 500, "选项C");
        x.optionD = manual ? "" : tf ? "" : Views.text(in.optionD(), 500, "选项D");
        x.correctOption = QuestionRules.choice(x.questionType, in.correctOption(), true);
        x.explanation = Views.optional(in.explanation(), 2000, "解析");
        x.knowledgePoint = Views.optional(in.knowledgePoint(),60,"知识点");
        if (x.explanation == null) x.explanation = "";
        if (in.sortNo() == null || in.sortNo() < 1 || in.sortNo() > 100000)
            throw ApiException.bad("排序号须为1至100000");
        x.sortNo = in.sortNo();
        if (questionId == null) questions.insert(x);
        else questions.updateById(x);
        q.version++;
        q.updatedAt = Views.now();
        quizzes.updateById(q);
        return detail(id);
    }

    @Transactional
    public void deleteQuestion(long id, long questionId, int version) {
        var q = ownedLock(id);
        Names.version(version, q.version);
        if (!q.status.equals("DRAFT")) throw ApiException.conflict("只能编辑草稿练习");
        var x = questions.lock(questionId);
        if (x == null || x.quizId != id) throw ApiException.missing();
        questions.deleteById(questionId);
        q.version++;
        q.updatedAt = Views.now();
        quizzes.updateById(q);
    }

    @Transactional
    public Object state(long id, State in) {
        var q = ownedLock(id);
        Names.version(in.expectedVersion(), q.version);
        if (!(q.status.equals("DRAFT") && "PUBLISHED".equals(in.status())
                || q.status.equals("PUBLISHED") && "ARCHIVED".equals(in.status())))
            throw ApiException.conflict("练习状态已变化");
        if (in.status().equals("PUBLISHED")
                && (items(id).isEmpty() || !course.required(q.courseId).status.equals("PUBLISHED")))
            throw ApiException.conflict("发布前须发布课程并添加题目");
        q.status = in.status();
        q.version++;
        q.updatedAt = Views.now();
        quizzes.updateById(q);
        return view(q);
    }

    @Transactional
    public Object importQuestion(long id, ImportQuestion in) {
        var q = ownedLock(id);
        Names.version(in.expectedVersion(), q.version);
        if (!q.status.equals("DRAFT")) throw ApiException.conflict("只能向草稿组卷");
        if (in.bankId() == null) throw ApiException.bad("请选择题库题目");
        var source = bank.owned(in.bankId(), true);
        Names.version(in.bankVersion(), source.version);
        if (!source.status.equals("ACTIVE")) throw ApiException.conflict("题库题目已归档");
        if (items(id).size() >= 100) throw ApiException.bad("每份练习最多100题");
        var x = new QuizQuestion();
        x.quizId = id; x.questionType = source.questionType; x.points = source.points;
        x.prompt = source.prompt; x.optionA = source.optionA; x.optionB = source.optionB;
        x.optionC = source.optionC; x.optionD = source.optionD; x.correctOption = source.correctOption;
        x.explanation = source.explanation; x.knowledgePoint = source.topic; x.bankId = source.id; x.bankVersion = source.version;
        x.sortNo = items(id).stream().mapToInt(t -> t.sortNo).max().orElse(0) + 1;
        questions.insert(x); q.version++; q.updatedAt = Views.now(); quizzes.updateById(q);
        return detail(id);
    }

    public Contracts.QuizSnapshot snapshot(long id) {
        Views.role("STUDENT");
        var q = required(id);
        var c = course.required(q.courseId);
        course.readable(c, true);
        if (q.status.equals("DRAFT")) throw ApiException.forbidden();
        return new Contracts.QuizSnapshot(q.id, q.courseId, q.chapterId, q.version, q.status, c.status,
                q.opensAt, q.closesAt, q.timeLimitMinutes, q.maxAttempts,
                items(id).stream().map(x -> new Contracts.QuizQuestionSnapshot(x.id, x.prompt,
                        x.optionA, x.optionB, x.optionC, x.optionD, x.correctOption, x.explanation,
                        x.questionType, x.points, x.knowledgePoint)).toList());
    }

    @Transactional
    public Contracts.QuizResult grade(long id, Contracts.QuizAnswers in) {
        Views.role("STUDENT");
        var initial = required(id);
        var c = course.locked(initial.courseId);
        course.readable(c, true);
        var q = quizzes.lock(id);
        if (!q.status.equals("PUBLISHED") || !c.status.equals("PUBLISHED"))
            throw ApiException.conflict("练习或课程已归档，不能提交");
        var frozen = snapshot(id);
        PracticeRules.available(frozen, Views.now());
        return QuestionRules.grade(frozen, in);
    }
}
