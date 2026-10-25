package org.hanwen.assignment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.assignment.client.LearningClient;
import org.hanwen.assignment.mapper.*;
import org.hanwen.assignment.model.*;
import org.hanwen.common.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

class BatchQueriesTest {
    @AfterEach
    void clearActor() {
        Actor.clear();
    }

    @Test
    void gradePolicyUsesOneCourseSnapshot() {
        Actor.set(new Actor(1, "TEACHER", 0));
        var assignment = mock(AssignmentService.class);
        var tasks = mock(TaskMapper.class);
        var policies = mock(GradePolicyMapper.class);
        when(assignment.course(11))
                .thenReturn(new Contracts.CourseValidation(11, 1, "PUBLISHED", 1, "课程", "OWNER"));
        when(assignment.tasks()).thenReturn(tasks);
        var policy = new GradePolicy();
        policy.id = 11L;
        policy.version = 0;
        when(policies.lock(11)).thenReturn(policy);
        new GradebookService(assignment, policies)
                .save(11, new GradebookService.Policy(70, "ZERO", "LATEST", 0));
        verify(assignment, times(1)).course(11);
        verify(policies).updateById(policy);
    }

    @Test
    void assistantCannotChangeGradePolicy() {
        Actor.set(new Actor(2, "TEACHER", 0));
        var assignment = mock(AssignmentService.class);
        var policies = mock(GradePolicyMapper.class);
        when(assignment.course(11))
                .thenReturn(
                        new Contracts.CourseValidation(11, 1, "PUBLISHED", 1, "课程", "ASSISTANT"));
        assertEquals(
                403,
                assertThrows(
                                ApiException.class,
                                () ->
                                        new GradebookService(assignment, policies)
                                                .save(
                                                        11,
                                                        new GradebookService.Policy(
                                                                70, "ZERO", "LATEST", 0)))
                        .status);
        verifyNoInteractions(policies);
    }

    @Test
    void deadlineFeedQueriesSubmittedTaskIdsOnce() {
        Actor.set(new Actor(3, "STUDENT", 0));
        var assignment = mock(AssignmentService.class);
        var learning = mock(LearningClient.class);
        var tasks = mock(TaskMapper.class);
        var submissions = mock(SubmissionMapper.class);
        var reminders = mock(ReminderMapper.class);
        when(assignment.remote()).thenReturn(new Remote());
        when(assignment.learning()).thenReturn(learning);
        when(assignment.tasks()).thenReturn(tasks);
        when(assignment.submissions()).thenReturn(submissions);
        when(learning.activeCourses()).thenReturn(List.of(11L));
        Task first = task(1), second = task(2);
        when(tasks.selectList(any())).thenReturn(List.of(first, second));
        var submitted = new Submission();
        submitted.assignmentId = 1L;
        when(submissions.selectList(any())).thenReturn(List.of(submitted));
        var result =
                (Map<?, ?>)
                        new ReminderService(
                                        assignment,
                                        reminders,
                                        mock(
                                                org.springframework.transaction
                                                        .PlatformTransactionManager.class))
                                .feed();
        var items = (List<?>) result.get("items");
        assertEquals(1, items.size());
        assertEquals("习作2：48小时内截止", ((Map<?, ?>) items.getFirst()).get("title"));
        verify(submissions, times(1))
                .selectList(argThat(q -> q.getSqlSelect().equals("assignment_id")));
        verify(submissions, never()).selectCount(any());
    }

    @Test
    void noEnrollmentDoesNotQueryTasksOrSubmissions() {
        Actor.set(new Actor(3, "STUDENT", 0));
        var assignment = mock(AssignmentService.class);
        var learning = mock(LearningClient.class);
        var reminders = mock(ReminderMapper.class);
        when(assignment.remote()).thenReturn(new Remote());
        when(assignment.learning()).thenReturn(learning);
        when(learning.activeCourses()).thenReturn(List.of());
        var result =
                (Map<?, ?>)
                        new ReminderService(
                                        assignment,
                                        reminders,
                                        mock(
                                                org.springframework.transaction
                                                        .PlatformTransactionManager.class))
                                .feed();
        assertEquals(List.of(), result.get("items"));
        verify(assignment, never()).tasks();
        verify(assignment, never()).submissions();
        verifyNoInteractions(reminders);
    }

    private Task task(long id) {
        var t = new Task();
        t.id = id;
        t.title = "习作" + id;
        t.createdAt = LocalDateTime.now();
        t.deadlineAt = t.createdAt.plusHours(1);
        return t;
    }
}
