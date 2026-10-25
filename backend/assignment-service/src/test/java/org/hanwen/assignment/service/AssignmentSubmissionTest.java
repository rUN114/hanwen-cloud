package org.hanwen.assignment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.assignment.client.*;
import org.hanwen.assignment.mapper.*;
import org.hanwen.assignment.model.*;
import org.hanwen.common.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

class AssignmentSubmissionTest {
    TaskMapper tasks = mock(TaskMapper.class);
    SubmissionMapper submissions = mock(SubmissionMapper.class);
    CourseClient courses = mock(CourseClient.class);
    LearningClient learning = mock(LearningClient.class);
    DraftMapper drafts = mock(DraftMapper.class);
    Task task;
    AssignmentService service;

    @BeforeEach
    void setup() {
        Actor.set(new Actor(3, "STUDENT", 0));
        task = new Task();
        task.id = 1L;
        task.courseId = 11L;
        task.status = "PUBLISHED";
        task.deadlineAt = Views.now().plusHours(1);
        when(tasks.selectById(1L)).thenReturn(task);
        when(tasks.lock(1)).thenReturn(task);
        when(courses.course(11))
                .thenReturn(new Contracts.CourseValidation(11, 1, "PUBLISHED", 1, "课程", "NONE"));
        when(learning.exists(11, 3)).thenReturn(new Contracts.Exists(true));
        service =
                new AssignmentService(
                        tasks,
                        submissions,
                        mock(GradeMapper.class),
                        courses,
                        learning,
                        new Remote(),
                        drafts,
                        mock(GradeHistoryMapper.class),
                        mock(DirectoryClient.class),
                        mock(org.springframework.transaction.PlatformTransactionManager.class));
    }

    @AfterEach
    void clear() {
        Actor.clear();
    }

    @Test
    void sameNormalizedAnswerReturnsExistingSubmission() {
        when(submissions.selectOne(any())).thenReturn(existing());
        var result = (Map<?, ?>) service.submit(1, new AssignmentService.Answer("　原答案 \n"));
        assertEquals("9", result.get("id"));
        verify(submissions, never()).insert(any(Submission.class));
        verify(submissions).selectOne(argThat(q -> q.getSqlSegment().contains("FOR UPDATE")));
        verifyNoInteractions(drafts);
    }

    @Test
    void differentAnswerDoesNotReportFalseSuccess() {
        when(submissions.selectOne(any())).thenReturn(existing());
        var error =
                assertThrows(
                        ApiException.class,
                        () -> service.submit(1, new AssignmentService.Answer("新答案")));
        assertEquals(409, error.status);
        assertEquals("SUBMISSION_EXISTS", error.code);
        verify(submissions, never()).insert(any(Submission.class));
        verifyNoInteractions(drafts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLOSED", "DEADLINE", "ARCHIVED"})
    void retriesCannotBypassCourseAndTaskRestrictions(String state) {
        if (state.equals("CLOSED")) task.status = "CLOSED";
        if (state.equals("DEADLINE")) task.deadlineAt = Views.now().minusSeconds(1);
        if (state.equals("ARCHIVED"))
            when(courses.course(11))
                    .thenReturn(new Contracts.CourseValidation(11, 1, "ARCHIVED", 1, "课程", "NONE"));
        assertEquals(
                409,
                assertThrows(
                                ApiException.class,
                                () -> service.submit(1, new AssignmentService.Answer("原答案")))
                        .status);
        verifyNoInteractions(submissions);
    }

    @Test
    void firstSubmissionPersistsNormalizedAnswerThenRemovesDraft() {
        when(submissions.insert(any(Submission.class)))
                .thenAnswer(
                        call -> {
                            Submission s = call.getArgument(0);
                            s.id = 9L;
                            return 1;
                        });
        var result = (Map<?, ?>) service.submit(1, new AssignmentService.Answer(" 原答案 "));
        assertEquals("原答案", result.get("answerText"));
        var order = inOrder(submissions, drafts);
        order.verify(submissions).selectOne(any());
        order.verify(submissions).insert(any(Submission.class));
        order.verify(drafts).delete(any());
    }

    private Submission existing() {
        var s = new Submission();
        s.id = 9L;
        s.assignmentId = 1L;
        s.studentId = 3L;
        s.answerText = "原答案";
        s.status = "SUBMITTED";
        s.version = 0;
        return s;
    }
}
