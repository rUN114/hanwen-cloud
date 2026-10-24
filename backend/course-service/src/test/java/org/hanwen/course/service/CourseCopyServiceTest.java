package org.hanwen.course.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.common.*;
import org.hanwen.course.mapper.*;
import org.hanwen.course.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;

class CourseCopyServiceTest {
    @AfterEach
    void clear() {
        Actor.clear();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"实词活用"})
    void copiedQuestionsPreserveKnowledgePoint(String knowledgePoint) {
        Actor.set(new Actor(7, "TEACHER", 0));
        var access = mock(CourseService.class);
        var courses = mock(CourseMapper.class);
        var chapters = mock(ChapterMapper.class);
        var quizzes = mock(QuizMapper.class);
        var questions = mock(QuizQuestionMapper.class);
        var source = new Course();
        source.id = 1L;
        source.teacherId = 7L;
        source.name = "原课程";
        when(access.locked(1)).thenReturn(source);
        when(courses.insert(any(Course.class)))
                .thenAnswer(
                        call -> {
                            ((Course) call.getArgument(0)).id = 2L;
                            return 1;
                        });
        var quiz = new Quiz();
        quiz.id = 30L;
        when(quizzes.selectList(any())).thenReturn(List.of(quiz));
        var question = new QuizQuestion();
        question.knowledgePoint = knowledgePoint;
        when(questions.selectList(any())).thenReturn(List.of(question));

        new CourseCopyService(access, courses, chapters, quizzes, questions)
                .copy(1, new CourseCopyService.Copy("新课程"));

        var copied = ArgumentCaptor.forClass(QuizQuestion.class);
        verify(questions).insert(copied.capture());
        assertEquals(knowledgePoint, copied.getValue().knowledgePoint);
        verify(access).principal(source);
    }
}
