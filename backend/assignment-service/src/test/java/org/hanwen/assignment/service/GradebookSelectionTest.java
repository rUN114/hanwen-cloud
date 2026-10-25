package org.hanwen.assignment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.assignment.client.*;
import org.hanwen.assignment.mapper.*;
import org.hanwen.assignment.model.*;
import org.hanwen.common.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.*;

class GradebookSelectionTest {
    @AfterEach
    void clear() {
        Actor.clear();
    }

    List<Map<?, ?>> book(String rule, List<Contracts.ScoreBrief> attempts) {
        Actor.set(new Actor(1, "TEACHER", 0));
        var assignment = mock(AssignmentService.class);
        var learning = mock(LearningClient.class);
        var courses = mock(CourseClient.class);
        when(assignment.course(1))
                .thenReturn(new Contracts.CourseValidation(1, 1, "PUBLISHED", 1, "课程", "OWNER"));
        when(assignment.remote()).thenReturn(new Remote());
        when(assignment.learning()).thenReturn(learning);
        when(assignment.courses()).thenReturn(courses);
        when(assignment.tasks()).thenReturn(mock(TaskMapper.class));
        when(assignment.submissions()).thenReturn(mock(SubmissionMapper.class));
        when(assignment.directory()).thenReturn(mock(DirectoryClient.class));
        when(learning.grades(1)).thenReturn(new Contracts.GradeFeed(List.of(3L, 4L), attempts));
        when(courses.quizzes(1))
                .thenReturn(List.of(new Contracts.QuizBrief(11, "练习", Views.now().plusDays(1))));
        var policies = mock(GradePolicyMapper.class);
        var policy = new GradePolicy();
        policy.id = 1L;
        policy.assignmentWeight = 0;
        policy.missingRule = "EXCLUDE";
        policy.attemptRule = rule;
        when(policies.selectById(1L)).thenReturn(policy);
        var result = (Map<?, ?>) new GradebookService(assignment, policies).book(1);
        return (List<Map<?, ?>>) result.get("students");
    }

    @ParameterizedTest
    @CsvSource({"HIGHEST,90.000000", "LATEST,70.000000"})
    void groupedSelectionPreservesAttemptRuleAndStudentBoundary(String rule, String expected) {
        var rows =
                book(
                        rule,
                        List.of(
                                new Contracts.ScoreBrief(3, 11, 90, 100, 1, "GRADED"),
                                new Contracts.ScoreBrief(3, 11, 70, 100, 2, "GRADED"),
                                new Contracts.ScoreBrief(4, 11, 30, 100, 3, "GRADED"),
                                new Contracts.ScoreBrief(3, 11, 100, 0, 99, "GRADED")));
        var first = (List<GradeRules.Item>) rows.getFirst().get("items");
        var second = (List<GradeRules.Item>) rows.get(1).get("items");
        assertEquals(new BigDecimal(expected), first.getFirst().percent());
        assertEquals(new BigDecimal("30.000000"), second.getFirst().percent());
    }

    @ParameterizedTest
    @CsvSource({"HIGHEST,0,PENDING", "LATEST,0,GRADED", "HIGHEST,5,PENDING", "LATEST,5,PENDING"})
    void pendingWrittenAttemptsKeepExistingReleaseSemantics(
            String rule, long pendingId, String expected) {
        var rows =
                book(
                        rule,
                        List.of(
                                new Contracts.ScoreBrief(3, 11, 80, 100, 2, "GRADED"),
                                new Contracts.ScoreBrief(3, 11, 0, 100, pendingId, "PENDING")));
        var items = (List<GradeRules.Item>) rows.getFirst().get("items");
        assertEquals(expected, items.getFirst().status());
        if (expected.equals("PENDING")) assertNull(items.getFirst().percent());
    }
}
