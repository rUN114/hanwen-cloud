package org.hanwen.course.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.common.*;
import org.hanwen.course.mapper.*;
import org.hanwen.course.model.*;
import org.junit.jupiter.api.*;

class QuizVersionInputTest {
    @AfterEach
    void clear() {
        Actor.clear();
    }

    @Test
    void omittedVersionIsBadInputAndNotAFalseConflict() {
        Actor.set(new Actor(1, "TEACHER", 0));
        var course = mock(CourseService.class);
        var quizzes = mock(QuizMapper.class);
        var questions = mock(QuizQuestionMapper.class);
        var c = new Course();
        c.status = "PUBLISHED";
        when(course.locked(11)).thenReturn(c);
        var q = new Quiz();
        q.courseId = 11L;
        q.status = "DRAFT";
        q.version = 0;
        when(quizzes.selectById(7L)).thenReturn(q);
        when(quizzes.lock(7)).thenReturn(q);
        var input =
                new QuizService.QuestionWrite(
                        null, null, null, null, null, null, null, null, null, null, null, null);
        var error =
                assertThrows(
                        ApiException.class,
                        () ->
                                new QuizService(course, quizzes, questions, null)
                                        .question(7, null, input));
        assertEquals(400, error.status);
        verifyNoInteractions(questions);
    }
}
