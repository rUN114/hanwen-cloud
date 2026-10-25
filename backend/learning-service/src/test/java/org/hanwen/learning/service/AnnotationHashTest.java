package org.hanwen.learning.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.common.*;
import org.hanwen.learning.client.CourseClient;
import org.hanwen.learning.mapper.AnnotationMapper;
import org.hanwen.learning.model.*;
import org.junit.jupiter.api.*;

import java.util.*;

class AnnotationHashTest {
    @AfterEach
    void clear() {
        Actor.clear();
    }

    @Test
    void listingHashesCurrentChapterOnceAndMarksStaleAnchors() {
        Actor.set(new Actor(3, "STUDENT", 0));
        var learning = mock(LearningService.class);
        var courses = mock(CourseClient.class);
        var mapper = mock(AnnotationMapper.class);
        var enrollment = new Enrollment();
        enrollment.courseId = 11L;
        when(learning.own(1)).thenReturn(enrollment);
        String text = "同一章正文";
        when(courses.text(11, 7)).thenReturn(new Contracts.ChapterText(11, 7, text, "PUBLISHED"));
        var a = new Annotation();
        a.id = 1L;
        a.sourceHash = AnnotationService.hash(text);
        var b = new Annotation();
        b.id = 2L;
        b.sourceHash = AnnotationService.hash("旧正文");
        when(mapper.selectList(any())).thenReturn(List.of(a, b));
        when(mapper.selectCount(any())).thenReturn(2L);
        try (var hashes = mockStatic(AnnotationService.class, CALLS_REAL_METHODS)) {
            var result =
                    (Map<?, ?>)
                            new AnnotationService(learning, courses, mapper, new Remote())
                                    .list(1, 7, 1, 10);
            var rows = (List<Map<?, ?>>) result.get("items");
            assertEquals(true, rows.getFirst().get("anchorValid"));
            assertEquals(false, rows.get(1).get("anchorValid"));
            hashes.verify(() -> AnnotationService.hash(text), times(1));
        }
    }
}
