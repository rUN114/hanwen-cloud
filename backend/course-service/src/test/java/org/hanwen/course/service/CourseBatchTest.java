package org.hanwen.course.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.common.*;
import org.hanwen.course.mapper.*;
import org.hanwen.course.model.*;
import org.junit.jupiter.api.*;

import java.util.*;

class CourseBatchTest {
    @AfterEach
    void clear() {
        Actor.clear();
    }

    @Test
    void batchesChapterCountsAndPreservesZeroChapterCourses() {
        Actor.set(new Actor(3, "STUDENT", 0));
        var courses = mock(CourseMapper.class);
        var chapters = mock(ChapterMapper.class);
        var service = new CourseService(courses, chapters, null, null, null, null, null, null);
        var a = new Course();
        a.id = 11L;
        a.teacherId = 1L;
        a.status = "PUBLISHED";
        a.name = "甲";
        var b = new Course();
        b.id = 12L;
        b.teacherId = 1L;
        b.status = "ARCHIVED";
        b.name = "乙";
        when(courses.selectByIds(anyCollection())).thenReturn(List.of(a, b));
        when(chapters.selectMaps(any()))
                .thenReturn(List.of(Map.<String, Object>of("course_id", 11L, "n", 2L)));
        var result = service.validations(List.of(11L, 12L));
        assertEquals(2, result.size());
        assertEquals(2, result.get(0).chapterCount());
        assertEquals(0, result.get(1).chapterCount());
        verify(chapters, times(1))
                .selectMaps(argThat(q -> q.getSqlSegment().contains("GROUP BY course_id")));
        verify(chapters, never()).selectCount(any());
    }

    @Test
    void missingOrEmptyBatchesDoNotCreateEmptyInQueries() {
        Actor.set(new Actor(3, "STUDENT", 0));
        var courses = mock(CourseMapper.class);
        var chapters = mock(ChapterMapper.class);
        var service = new CourseService(courses, chapters, null, null, null, null, null, null);
        assertEquals(List.of(), service.validations(List.of()));
        assertEquals(List.of(), service.validations(List.of(99L)));
        verifyNoInteractions(chapters);
        assertThrows(ApiException.class, () -> service.validations(List.of(-1L)));
        assertThrows(ApiException.class, () -> service.validations(Collections.nCopies(101, 1L)));
    }
}
