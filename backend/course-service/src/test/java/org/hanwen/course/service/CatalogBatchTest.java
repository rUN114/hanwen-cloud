package org.hanwen.course.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.common.*;
import org.hanwen.course.client.LearningClient;
import org.hanwen.course.mapper.*;
import org.hanwen.course.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

class CatalogBatchTest {
    @AfterEach
    void clear() {
        Actor.clear();
    }

    Course row(long id, long owner) {
        var c = new Course();
        c.id = id;
        c.teacherId = owner;
        c.status = "PUBLISHED";
        c.name = "课程";
        return c;
    }

    @Test
    void teacherCatalogLoadsDelegatedRolesOnceAndStudentSkipsThem() {
        var mapper = mock(CourseMapper.class);
        var team = mock(CourseTeamMapper.class);
        var directory = mock(DirectoryClient.class);
        var service =
                new CourseService(
                        mapper,
                        mock(ChapterMapper.class),
                        mock(LearningClient.class),
                        new Remote(),
                        mock(WorkMapper.class),
                        directory,
                        mock(ResourceFileMapper.class),
                        team);
        when(mapper.selectCount(any())).thenReturn(3L);
        when(mapper.selectList(any())).thenReturn(List.of(row(1, 1), row(2, 2), row(3, 2)));
        when(directory.names(anyString())).thenReturn(List.of());
        var delegated = new CourseTeam();
        delegated.courseId = 2L;
        delegated.role = "ASSISTANT";
        when(team.selectList(any())).thenReturn(List.of(delegated));
        Actor.set(new Actor(1, "TEACHER", 0));
        var page = (Map<?, ?>) service.list(1, 10, null, null, null, false);
        var rows = (List<Map<?, ?>>) page.get("items");
        assertEquals(
                List.of("OWNER", "ASSISTANT", "NONE"),
                rows.stream().map(c -> c.get("accessRole")).toList());
        verify(team, times(1)).selectList(any());
        verify(team, never()).selectOne(any());
        clearInvocations(team);
        Actor.set(new Actor(3, "STUDENT", 0));
        service.list(1, 10, null, null, null, false);
        verifyNoInteractions(team);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 30, 100})
    void teacherCatalogRolesUseOneQueryRegardlessOfPageSize(int count) {
        Actor.set(new Actor(1, "TEACHER", 0));
        var courses = mock(CourseMapper.class);
        var team = mock(CourseTeamMapper.class);
        var directory = mock(DirectoryClient.class);
        when(courses.selectCount(any())).thenReturn((long) count);
        when(courses.selectList(any()))
                .thenReturn(
                        java.util.stream.LongStream.rangeClosed(1, count)
                                .mapToObj(id -> row(id, 2))
                                .toList());
        when(directory.names(anyString())).thenReturn(List.of(new Contracts.PublicUser(2, "主讲")));
        var service =
                new CourseService(
                        courses,
                        mock(ChapterMapper.class),
                        mock(LearningClient.class),
                        new Remote(),
                        mock(WorkMapper.class),
                        directory,
                        mock(ResourceFileMapper.class),
                        team);
        var result = (Map<?, ?>) service.list(1, 100, null, null, null, false);
        var rows = (List<Map<?, ?>>) result.get("items");
        assertEquals(count, rows.size());
        assertTrue(
                rows.stream()
                        .allMatch(
                                c ->
                                        "NONE".equals(c.get("accessRole"))
                                                && "主讲".equals(c.get("teacherName"))));
        verify(team, times(1)).selectList(any());
        verify(team, never()).selectOne(any());
        verify(directory, times(1)).names(anyString());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 30, 100})
    void quizListUsesOneAggregateWithoutLoadingAnswers(int count) {
        Actor.set(new Actor(3, "STUDENT", 0));
        var course = mock(CourseService.class);
        var quizzes = mock(QuizMapper.class);
        var questions = mock(QuizQuestionMapper.class);
        when(course.required(11)).thenReturn(row(11, 1));
        var input = new ArrayList<Quiz>();
        for (long id = 7; id < 7 + count; id++) {
            var quiz = new Quiz();
            quiz.id = id;
            quiz.title = "练习" + id;
            input.add(quiz);
        }
        when(quizzes.selectCount(any())).thenReturn((long) count);
        when(quizzes.selectList(any())).thenReturn(input);
        when(questions.selectMaps(any()))
                .thenReturn(List.of(Map.of("quiz_id", 7L, "n", 2L, "points", 15)));
        var service = new QuizService(course, quizzes, questions, mock(QuestionBankService.class));
        var page = (Map<?, ?>) service.list(11, null, 1, 10);
        var rows = (List<Map<?, ?>>) page.get("items");
        assertEquals(2L, rows.getFirst().get("questionCount"));
        assertEquals(15, rows.getFirst().get("totalPoints"));
        assertEquals(count, rows.size());
        if (count > 1) assertEquals(0, rows.get(1).get("totalPoints"));
        verify(questions, times(1)).selectMaps(any());
        verify(questions, never()).selectList(any());
        verify(questions, never()).selectCount(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 30, 100})
    void replyCountsAreBatchedAndMissingRepliesMeanZero(int count) {
        Actor.set(new Actor(3, "STUDENT", 0));
        var course = mock(CourseService.class);
        var questions = mock(QuestionMapper.class);
        var replies = mock(ReplyMapper.class);
        var directory = mock(DirectoryClient.class);
        when(course.required(11)).thenReturn(row(11, 1));
        when(directory.names(anyString())).thenReturn(List.of());
        var input = new ArrayList<Question>();
        for (long id = 1; id <= count; id++) {
            var q = new Question();
            q.id = id;
            q.studentId = 3L;
            input.add(q);
        }
        when(questions.selectCount(any())).thenReturn((long) count);
        when(questions.selectList(any())).thenReturn(input);
        when(replies.selectMaps(any())).thenReturn(List.of(Map.of("question_id", 1L, "n", 2L)));
        var service =
                new CommunityService(
                        course,
                        mock(NoticeMapper.class),
                        questions,
                        replies,
                        directory,
                        new Remote());
        var page = (Map<?, ?>) service.questions(11, 1, 10, null, null);
        var rows = (List<Map<?, ?>>) page.get("items");
        assertEquals(count, rows.size());
        assertEquals(2, rows.getFirst().get("replyCount"));
        assertTrue(rows.stream().skip(1).allMatch(q -> q.get("replyCount").equals(0)));
        verify(replies, times(1)).selectMaps(any());
        verify(replies, never()).selectCount(any());
    }

    @Test
    void releaseBatchRejectsDraftsAndDoesNotFetchQuestionBodies() {
        Actor.set(new Actor(3, "STUDENT", 0));
        var course = mock(CourseService.class);
        var quizzes = mock(QuizMapper.class);
        var questions = mock(QuizQuestionMapper.class);
        var a = new Quiz();
        a.id = 7L;
        a.courseId = 11L;
        a.status = "CLOSED";
        a.maxAttempts = 2;
        var b = new Quiz();
        b.id = 8L;
        b.courseId = 11L;
        b.status = "PUBLISHED";
        b.maxAttempts = 1;
        when(quizzes.selectByIds(anyCollection())).thenReturn(List.of(a, b));
        when(course.required(11)).thenReturn(row(11, 1));
        var service = new QuizService(course, quizzes, questions, mock(QuestionBankService.class));
        assertEquals(2, service.releases(List.of(7L, 8L)).size());
        verify(course, times(1)).readable(any(), eq(true));
        verifyNoInteractions(questions);
        b.status = "DRAFT";
        assertEquals(
                403,
                assertThrows(ApiException.class, () -> service.releases(List.of(7L, 8L))).status);
    }
}
