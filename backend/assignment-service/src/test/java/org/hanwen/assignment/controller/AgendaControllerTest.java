package org.hanwen.assignment.controller;

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

    @ParameterizedTest
    @ValueSource(ints = {1, 50, 200})
    void studentAgendaBatchesSubmissionStateAndRetainsUnsubmittedTasks(int count) {
        Actor.set(new Actor(3, "STUDENT", 0));
        var tasks = mock(TaskMapper.class);
        var submissions = mock(SubmissionMapper.class);
        var learning = mock(LearningClient.class);
        when(learning.activeCourses()).thenReturn(List.of(11L));
        when(tasks.selectCount(any())).thenReturn(201L);
        when(tasks.selectList(any()))
                .thenReturn(
                        java.util.stream.LongStream.rangeClosed(1, count)
                                .mapToObj(this::task)
                                .toList());
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
        assertEquals(count, rows.size());
        if (count > 1) assertNull(rows.get(1).get("submissionId"));
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
