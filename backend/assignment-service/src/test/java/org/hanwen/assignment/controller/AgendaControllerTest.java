package org.hanwen.assignment.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.assignment.client.*;
import org.hanwen.assignment.mapper.*;
import org.hanwen.assignment.model.*;
import org.hanwen.common.*;
import org.junit.jupiter.api.*;

import java.util.*;

class AgendaControllerTest {
    @AfterEach
    void clear() {
        Actor.clear();
    }

    Task task(long id) {
        var t = new Task();
        t.id = id;
        t.courseId = 11L;
        t.title = "作业" + id;
        t.status = "PUBLISHED";
        return t;
    }

    @Test
    void studentAgendaBatchesSubmissionStateAndRetainsUnsubmittedTasks() {
        Actor.set(new Actor(3, "STUDENT", 0));
        var tasks = mock(TaskMapper.class);
        var submissions = mock(SubmissionMapper.class);
        var learning = mock(LearningClient.class);
        when(learning.activeCourses()).thenReturn(List.of(11L));
        when(tasks.selectCount(any())).thenReturn(201L);
        when(tasks.selectList(any())).thenReturn(List.of(task(1), task(2)));
        var submitted = new Submission();
        submitted.id = 99L;
        submitted.assignmentId = 1L;
        submitted.status = "GRADED";
        when(submissions.selectList(any())).thenReturn(List.of(submitted));
        var response =
                (ApiResponse<?>)
                        new AgendaController(tasks, submissions, learning, new Remote(), null)
                                .month("2026-10");
        var data = (Map<?, ?>) response.data();
        var rows = (List<Map<?, ?>>) data.get("items");
        assertEquals("GRADED", rows.getFirst().get("submissionStatus"));
        assertEquals("99", rows.getFirst().get("submissionId"));
        assertNull(rows.get(1).get("submissionId"));
        assertEquals(true, data.get("hasMore"));
        verify(submissions, times(1))
                .selectList(
                        argThat(
                                q ->
                                        q.getSqlSegment().contains("student_id")
                                                && q.getSqlSegment().contains("assignment_id IN")));
        verify(submissions, never()).selectOne(any());
    }

    @Test
    void teacherAgendaDoesNotQueryStudentSubmissions() {
        Actor.set(new Actor(1, "TEACHER", 0));
        var tasks = mock(TaskMapper.class);
        var submissions = mock(SubmissionMapper.class);
        var courses = mock(CourseClient.class);
        when(courses.staffIds(false)).thenReturn(List.of(11L));
        when(tasks.selectList(any())).thenReturn(List.of(task(1)));
        new AgendaController(tasks, submissions, null, new Remote(), courses).month("2026-10");
        verifyNoInteractions(submissions);
    }
}
