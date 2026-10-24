package org.hanwen.learning.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.common.*;
import org.hanwen.learning.mapper.*;
import org.hanwen.learning.model.*;
import org.junit.jupiter.api.*;

import java.util.*;

class StudyRosterTest {
    @AfterEach
    void clear() {
        Actor.clear();
    }

    @Test
    void rosterGroupsCountsAndPreservesStudentsWithoutActivity() {
        Actor.set(new Actor(1, "TEACHER", 0));
        var learning = mock(LearningService.class);
        var enrollments = mock(EnrollmentMapper.class);
        var progress = mock(ProgressMapper.class);
        var notes = mock(NoteMapper.class);
        var directory = mock(DirectoryClient.class);
        when(learning.course(11))
                .thenReturn(new Contracts.CourseValidation(11, 1, "PUBLISHED", 2, "课程", "OWNER"));
        var a = new Enrollment();
        a.id = 20L;
        a.studentId = 3L;
        var b = new Enrollment();
        b.id = 21L;
        b.studentId = 4L;
        when(enrollments.selectList(any())).thenReturn(List.of(a, b));
        when(enrollments.selectCount(any())).thenReturn(2L);
        when(directory.names(anyString()))
                .thenReturn(
                        List.of(
                                new Contracts.PublicUser(3, "甲"),
                                new Contracts.PublicUser(4, "乙")));
        when(progress.selectMaps(any())).thenReturn(List.of(Map.of("enrollment_id", 20L, "n", 1L)));
        when(notes.selectMaps(any())).thenReturn(List.of(Map.of("enrollment_id", 20L, "n", 3L)));
        var service =
                new StudyService(
                        learning,
                        notes,
                        null,
                        directory,
                        new Remote(),
                        null,
                        enrollments,
                        progress);
        var result = (Map<?, ?>) service.roster(11, 1, 10);
        var rows = (List<Map<?, ?>>) result.get("items");
        assertEquals(2L, result.get("total"));
        assertEquals("50.00", rows.getFirst().get("percent"));
        assertEquals(3, rows.getFirst().get("noteCount"));
        assertEquals(0, rows.get(1).get("completed"));
        assertEquals(0, rows.get(1).get("noteCount"));
        verify(progress)
                .selectMaps(argThat(q -> q.getSqlSegment().contains("GROUP BY enrollment_id")));
        verify(notes)
                .selectMaps(argThat(q -> q.getSqlSegment().contains("GROUP BY enrollment_id")));
        verify(progress, never()).selectCount(any());
        verify(notes, never()).selectCount(any());
    }

    @Test
    void teacherOutsideTheCourseCannotReadCounts() {
        Actor.set(new Actor(1, "TEACHER", 0));
        var learning = mock(LearningService.class);
        var enrollments = mock(EnrollmentMapper.class);
        var progress = mock(ProgressMapper.class);
        when(learning.course(11))
                .thenReturn(new Contracts.CourseValidation(11, 9, "PUBLISHED", 2, "课程", "NONE"));
        var service =
                new StudyService(learning, null, null, null, null, null, enrollments, progress);
        assertEquals(403, assertThrows(ApiException.class, () -> service.roster(11, 1, 10)).status);
        verifyNoInteractions(enrollments, progress);
    }
}
