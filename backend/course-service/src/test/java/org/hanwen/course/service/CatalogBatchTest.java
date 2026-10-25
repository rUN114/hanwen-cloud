package org.hanwen.course.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.common.*;
import org.hanwen.course.client.LearningClient;
import org.hanwen.course.mapper.*;
import org.hanwen.course.model.*;
import org.junit.jupiter.api.*;

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

    @Test
    void quizListUsesOneAggregateWithoutLoadingAnswers() {
        Actor.set(new Actor(3, "STUDENT", 0));
        var course = mock(CourseService.class);
        var quizzes = mock(QuizMapper.class);
        var questions = mock(QuizQuestionMapper.class);
        when(course.required(11)).thenReturn(row(11, 1));
        var a = new Quiz();
        a.id = 7L;
        a.title = "古诗";
        var b = new Quiz();
        b.id = 8L;
        b.title = "文言";
        when(quizzes.selectCount(any())).thenReturn(2L);
        when(quizzes.selectList(any())).thenReturn(List.of(a, b));
        when(questions.selectMaps(any()))
                .thenReturn(List.of(Map.of("quiz_id", 7L, "n", 2L, "points", 15)));
        var service = new QuizService(course, quizzes, questions, mock(QuestionBankService.class));
        var page = (Map<?, ?>) service.list(11, null, 1, 10);
        var rows = (List<Map<?, ?>>) page.get("items");
        assertEquals(2L, rows.getFirst().get("questionCount"));
        assertEquals(15, rows.getFirst().get("totalPoints"));
        assertEquals(0, rows.get(1).get("totalPoints"));
        verify(questions, times(1)).selectMaps(any());
        verify(questions, never()).selectList(any());
        verify(questions, never()).selectCount(any());
    }

    @Test
    void replyCountsAreBatchedAndMissingRepliesMeanZero() {
        Actor.set(new Actor(3, "STUDENT", 0));
        var course = mock(CourseService.class);
        var questions = mock(QuestionMapper.class);
        var replies = mock(ReplyMapper.class);
        var directory = mock(DirectoryClient.class);
        when(course.required(11)).thenReturn(row(11, 1));
        when(directory.names(anyString())).thenReturn(List.of());
        var a = new Question();
        a.id = 1L;
        a.studentId = 3L;
        var b = new Question();
        b.id = 2L;
        b.studentId = 3L;
        when(questions.selectCount(any())).thenReturn(2L);
        when(questions.selectList(any())).thenReturn(List.of(a, b));
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
        assertEquals(List.of(2, 0), rows.stream().map(q -> q.get("replyCount")).toList());
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
