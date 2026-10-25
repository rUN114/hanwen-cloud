package org.hanwen.assignment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.assignment.client.*;
import org.hanwen.assignment.mapper.*;
import org.hanwen.assignment.model.*;
import org.hanwen.common.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.*;

class AssignmentBatchTest {
    @AfterEach
    void clear() {
        Actor.clear();
    }

    Task task() {
        var t = new Task();
        t.id = 7L;
        t.courseId = 11L;
        t.title = "习作";
        t.maxScore = new BigDecimal("100");
        t.createdAt = Views.now();
        return t;
    }

    @Test
    void ownSubmissionsBatchTasksAndGradesWhileKeepingOrderAndUngradedRows() {
        Actor.set(new Actor(3, "STUDENT", 0));
        var tasks = mock(TaskMapper.class);
        var submissions = mock(SubmissionMapper.class);
        var grades = mock(GradeMapper.class);
        var first = new Submission();
        first.id = 12L;
        first.assignmentId = 7L;
        first.status = "GRADED";
        var second = new Submission();
        second.id = 10L;
        second.assignmentId = 7L;
        second.status = "SUBMITTED";
        when(submissions.selectCount(any())).thenReturn(2L);
        when(submissions.selectList(any())).thenReturn(List.of(first, second));
        when(tasks.selectByIds(anyCollection())).thenReturn(List.of(task()));
        var grade = new Grade();
        grade.submissionId = 12L;
        grade.score = new BigDecimal("80");
        when(grades.selectList(any())).thenReturn(List.of(grade));
        var service =
                new AssignmentService(
                        tasks,
                        submissions,
                        grades,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        mock(org.springframework.transaction.PlatformTransactionManager.class));
        var result = (Map<?, ?>) service.mine(1, 10, null, null);
        var rows = (List<Map<?, ?>>) result.get("items");
        assertEquals(2L, result.get("total"));
        assertEquals("12", rows.getFirst().get("submissionId"));
        assertEquals("80.00", rows.getFirst().get("score"));
        assertNull(rows.get(1).get("score"));
        verify(tasks).selectByIds(List.of(7L));
        verify(tasks, never()).selectById(any());
        verify(grades, times(1)).selectList(any());
        verify(grades, never()).selectOne(any());
    }

    @Test
    void emptySubmissionPageDoesNotQueryTaskOrGradeBatches() {
        Actor.set(new Actor(3, "STUDENT", 0));
        var tasks = mock(TaskMapper.class);
        var submissions = mock(SubmissionMapper.class);
        var grades = mock(GradeMapper.class);
        new AssignmentService(
                        tasks,
                        submissions,
                        grades,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        mock(org.springframework.transaction.PlatformTransactionManager.class))
                .mine(1, 10, null, null);
        verifyNoInteractions(tasks, grades);
    }

    @Test
    void reminderFeedLoadsRepeatedTaskOnceAndKeepsBothMessages() {
        Actor.set(new Actor(3, "STUDENT", 0));
        var assignment = mock(AssignmentService.class);
        var learning = mock(LearningClient.class);
        var tasks = mock(TaskMapper.class);
        var reminders = mock(ReminderMapper.class);
        when(assignment.remote()).thenReturn(new Remote());
        when(assignment.learning()).thenReturn(learning);
        when(assignment.tasks()).thenReturn(tasks);
        when(learning.activeCourses()).thenReturn(List.of(11L));
        var a = new Reminder();
        a.id = 1L;
        a.assignmentId = 7L;
        a.content = "请提交";
        var b = new Reminder();
        b.id = 2L;
        b.assignmentId = 7L;
        b.content = "请检查";
        when(reminders.selectList(any())).thenReturn(List.of(a, b));
        when(tasks.selectByIds(anyCollection())).thenReturn(List.of(task()));
        var result =
                (Map<?, ?>)
                        new ReminderService(
                                        assignment,
                                        reminders,
                                        mock(
                                                org.springframework.transaction
                                                        .PlatformTransactionManager.class))
                                .feed();
        assertEquals(2, ((List<?>) result.get("items")).size());
        verify(tasks).selectByIds(List.of(7L));
        verify(assignment, never()).task(anyLong());
    }

    @Test
    void sendingRemindersBatchesExistingRecipientsWithoutLosingDailyDeduplication() {
        Actor.set(new Actor(1, "TEACHER", 0));
        var assignment = mock(AssignmentService.class);
        var learning = mock(LearningClient.class);
        var submissions = mock(SubmissionMapper.class);
        var reminders = mock(ReminderMapper.class);
        when(assignment.lockedTask(7)).thenReturn(task());
        when(assignment.task(7)).thenReturn(task());
        when(assignment.course(11))
                .thenReturn(new Contracts.CourseValidation(11, 1, "PUBLISHED", 1, "课程", "OWNER"));
        when(assignment.remote()).thenReturn(new Remote());
        when(assignment.learning()).thenReturn(learning);
        when(assignment.submissions()).thenReturn(submissions);
        when(learning.grades(11)).thenReturn(new Contracts.GradeFeed(List.of(3L, 4L), List.of()));
        var prior = new Reminder();
        prior.studentId = 3L;
        when(reminders.selectList(any())).thenReturn(List.of(prior));
        var result =
                (Map<?, ?>)
                        new ReminderService(
                                        assignment,
                                        reminders,
                                        mock(
                                                org.springframework.transaction
                                                        .PlatformTransactionManager.class))
                                .send(7, new ReminderService.Send(List.of(3L, 4L, 4L), "请提交"));
        assertEquals(1, result.get("sent"));
        assertEquals(1, result.get("alreadySentToday"));
        var inserted = ArgumentCaptor.forClass(Reminder.class);
        verify(reminders).insert(inserted.capture());
        assertEquals(4L, inserted.getValue().studentId);
        assertEquals(Views.now().toLocalDate(), inserted.getValue().dayKey);
        verify(reminders, times(1)).selectList(any());
        verify(reminders, never()).selectCount(any());
        verify(assignment).lockedTask(7);
    }
}
