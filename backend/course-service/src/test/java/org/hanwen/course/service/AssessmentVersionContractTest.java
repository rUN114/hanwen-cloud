package org.hanwen.course.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.common.*;
import org.hanwen.course.mapper.*;
import org.hanwen.course.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.List;
import java.util.stream.Stream;

class AssessmentVersionContractTest {
    enum Operation {
        QUIZ_WRITE,
        QUESTION_WRITE,
        QUESTION_DELETE,
        QUIZ_STATE,
        IMPORT_QUIZ_VERSION,
        IMPORT_BANK_VERSION,
        BANK_WRITE,
        BANK_STATE
    }

    final CourseService course = mock(CourseService.class);
    final QuizMapper quizzes = mock(QuizMapper.class);
    final QuizQuestionMapper questions = mock(QuizQuestionMapper.class);
    final BankQuestionMapper bankMapper = mock(BankQuestionMapper.class);
    final QuestionBankService bank = new QuestionBankService(bankMapper);
    final QuizService service = new QuizService(course, quizzes, questions, bank);
    Quiz quiz;
    BankQuestion source;

    @BeforeEach
    void setup() {
        Actor.set(new Actor(1, "TEACHER", 0));
        var c = new Course();
        c.id = 11L;
        c.teacherId = 1L;
        c.status = "PUBLISHED";
        when(course.required(11)).thenReturn(c);
        when(course.locked(11)).thenReturn(c);
        when(course.staff(c)).thenReturn(true);
        quiz = new Quiz();
        quiz.id = 7L;
        quiz.courseId = 11L;
        quiz.chapterId = 19L;
        quiz.status = "DRAFT";
        quiz.version = 3;
        when(quizzes.selectById(7L)).thenReturn(quiz);
        when(quizzes.lock(7)).thenReturn(quiz);
        var question = new QuizQuestion();
        question.id = 29L;
        question.quizId = 7L;
        question.points = 1;
        question.sortNo = 1;
        when(questions.lock(29)).thenReturn(question);
        when(questions.selectList(any())).thenReturn(List.of(question));
        source = new BankQuestion();
        source.id = 37L;
        source.teacherId = 1L;
        source.status = "ACTIVE";
        source.version = 9;
        source.questionType = "SINGLE";
        source.points = 2;
        source.prompt = "题目";
        source.optionA = "甲";
        source.optionB = "乙";
        source.optionC = "丙";
        source.optionD = "丁";
        source.correctOption = "A";
        source.explanation = "解析";
        source.topic = "诗歌意象";
        when(bankMapper.lock(37)).thenReturn(source);
        when(bankMapper.selectById(37L)).thenReturn(source);
    }

    @AfterEach
    void clear() {
        Actor.clear();
    }

    static Stream<Arguments> cases() {
        return Stream.of(Operation.values())
                .flatMap(
                        op -> {
                            int actual =
                                    (op == Operation.IMPORT_BANK_VERSION
                                                    || op == Operation.BANK_WRITE
                                                    || op == Operation.BANK_STATE)
                                            ? 9
                                            : 3;
                            var normal =
                                    Stream.of(
                                            Arguments.of(op, -1, 400, "VALIDATION_ERROR"),
                                            Arguments.of(op, actual - 1, 409, "VERSION_CONFLICT"),
                                            Arguments.of(op, actual, 200, null));
                            return op == Operation.QUESTION_DELETE
                                    ? normal
                                    : Stream.concat(
                                            Stream.of(
                                                    Arguments.of(
                                                            op, null, 400, "VALIDATION_ERROR")),
                                            normal);
                        });
    }

    @ParameterizedTest(name = "{0}, expected={1}, status={2}")
    @MethodSource("cases")
    void eachWriteDistinguishesMissingStaleAndCurrentVersion(
            Operation op, Integer expected, int status, String code) {
        Runnable action =
                () -> {
                    switch (op) {
                        case QUIZ_WRITE ->
                                service.write(
                                        11,
                                        7L,
                                        new QuizService.Write(
                                                19L, "新标题", "说明", expected, null, null, 0, 1));
                        case QUESTION_WRITE ->
                                service.question(
                                        7,
                                        null,
                                        new QuizService.QuestionWrite(
                                                "题目", "甲", "乙", "丙", "丁", "A", "解析", 2, expected,
                                                "SINGLE", 1, "知识点"));
                        case QUESTION_DELETE -> service.deleteQuestion(7, 29, expected);
                        case QUIZ_STATE ->
                                service.state(7, new QuizService.State("PUBLISHED", expected));
                        case IMPORT_QUIZ_VERSION ->
                                service.importQuestion(
                                        7, new QuizService.ImportQuestion(37L, 9, expected));
                        case IMPORT_BANK_VERSION ->
                                service.importQuestion(
                                        7, new QuizService.ImportQuestion(37L, expected, 3));
                        case BANK_WRITE ->
                                bank.write(
                                        37L,
                                        new QuestionBankService.Write(
                                                "SINGLE", "新题目", "甲", "乙", "丙", "丁", "A", "解析", 2,
                                                "知识点", expected));
                        case BANK_STATE ->
                                bank.state(37, new QuestionBankService.State("ARCHIVED", expected));
                    }
                };
        if (status != 200) {
            var error = assertThrows(ApiException.class, action::run);
            assertEquals(status, error.status);
            assertEquals(code, error.code);
            verify(quizzes, never()).updateById(any(Quiz.class));
            verify(questions, never()).insert(any(QuizQuestion.class));
            verify(questions, never()).updateById(any(QuizQuestion.class));
            verify(questions, never()).deleteById(anyLong());
            verify(bankMapper, never()).updateById(any(BankQuestion.class));
            assertEquals(3, quiz.version);
            assertEquals(9, source.version);
        } else {
            assertDoesNotThrow(action::run);
            if (op == Operation.BANK_WRITE || op == Operation.BANK_STATE) {
                assertEquals(10, source.version);
                verify(bankMapper).updateById(source);
            } else {
                assertEquals(4, quiz.version);
                verify(quizzes).updateById(quiz);
            }
            if (op == Operation.IMPORT_BANK_VERSION || op == Operation.IMPORT_QUIZ_VERSION)
                verify(questions)
                        .insert(
                                argThat(
                                        (QuizQuestion q) ->
                                                "诗歌意象".equals(q.knowledgePoint)
                                                        && q.bankVersion == 9
                                                        && q.points == 2));
        }
    }
}
