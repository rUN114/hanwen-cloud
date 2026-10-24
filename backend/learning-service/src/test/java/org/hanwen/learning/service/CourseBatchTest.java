package org.hanwen.learning.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.common.*;
import org.hanwen.learning.client.CourseClient;
import org.hanwen.learning.mapper.*;
import org.hanwen.learning.model.Enrollment;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.*;

class CourseBatchTest {
    @AfterEach
    void clear() {
        Actor.clear();
    }

    @Test
    void historyOverOneHundredCoursesUsesChunksWithoutAnArtificialLimit() {
        Actor.set(new Actor(3, "STUDENT", 0));
        var rows = new ArrayList<Enrollment>();
        for (long id = 1; id <= 105; id++) rows.add(enrollment(id));
        var mapper = mock(EnrollmentMapper.class);
        var client = mock(CourseClient.class);
        when(mapper.selectList(any())).thenReturn(rows);
        when(client.courses(anyString()))
                .thenAnswer(
                        call ->
                                Arrays.stream(((String) call.getArgument(0)).split(","))
                                        .map(Long::parseLong)
                                        .filter(id -> id != 2)
                                        .map(id -> course(id, id == 1 ? "ARCHIVED" : "PUBLISHED"))
                                        .toList());
        var learning = new LearningService(mapper, null, client, new Remote(), null);
        var study = new StudyService(learning, null, null, null, null, client, mapper, null);
        var result = study.activeCourses();
        assertEquals(103, result.size());
        assertFalse(result.contains(1L));
        assertFalse(result.contains(2L));
        assertEquals(3L, result.getFirst());
        verify(client, times(3)).courses(anyString());
        verify(client, never()).course(anyLong());
    }

    @Test
    void missingCourseKeepsEnrollmentAndPageTotal() {
        Actor.set(new Actor(3, "STUDENT", 0));
        var mapper = mock(EnrollmentMapper.class);
        var client = mock(CourseClient.class);
        when(mapper.selectCount(any())).thenReturn(2L);
        when(mapper.selectList(any())).thenReturn(List.of(enrollment(11), enrollment(12)));
        when(client.courses("11,12")).thenReturn(List.of(course(11, "PUBLISHED")));
        var result =
                (Map<?, ?>)
                        new LearningService(mapper, null, client, new Remote(), null)
                                .list(1, 10, null);
        assertEquals(2L, result.get("total"));
        var items = (List<?>) result.get("items");
        assertEquals(2, items.size());
        var missing = (Map<?, ?>) items.get(1);
        assertEquals("MISSING", missing.get("courseStatus"));
        assertEquals("12", missing.get("courseId"));
    }

    @Test
    void dependencyFailureDoesNotMasqueradeAsMissingCourse() {
        Actor.set(new Actor(3, "STUDENT", 0));
        var mapper = mock(EnrollmentMapper.class);
        var client = mock(CourseClient.class);
        when(mapper.selectCount(any())).thenReturn(1L);
        when(mapper.selectList(any())).thenReturn(List.of(enrollment(11)));
        when(client.courses(anyString())).thenThrow(ApiException.unavailable());
        var service = new LearningService(mapper, null, client, new Remote(), null);
        assertEquals(503, assertThrows(ApiException.class, () -> service.list(1, 10, null)).status);
    }

    @Test
    void emptyEnrollmentDoesNotCallCourseService() {
        Actor.set(new Actor(3, "STUDENT", 0));
        var mapper = mock(EnrollmentMapper.class);
        var client = mock(CourseClient.class);
        var learning = new LearningService(mapper, null, client, new Remote(), null);
        var study = new StudyService(learning, null, null, null, null, client, mapper, null);
        assertEquals(List.of(), study.activeCourses());
        verifyNoInteractions(client);
    }

    private Enrollment enrollment(long id) {
        var e = new Enrollment();
        e.id = id;
        e.courseId = id;
        e.studentId = 3L;
        e.joinedAt = LocalDateTime.now();
        return e;
    }

    private Contracts.CourseValidation course(long id, String status) {
        return new Contracts.CourseValidation(id, 1, status, 0, "课程" + id, "NONE");
    }
}
