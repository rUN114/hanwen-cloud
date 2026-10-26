package org.hanwen.learning.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.common.*;
import org.hanwen.learning.client.CourseClient;
import org.hanwen.learning.mapper.*;
import org.hanwen.learning.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.*;

class ListQueryBudgetTest {
    @AfterEach
    void clear() {
        Actor.clear();
    }

    @ParameterizedTest
    @CsvSource({"TEACHER,1", "TEACHER,30", "TEACHER,100", "STUDENT,1", "STUDENT,30", "STUDENT,100"})
    void classroomQueriesStayBoundedAsPageGrows(String role, int count) {
        boolean teacher = role.equals("TEACHER");
        Actor.set(new Actor(teacher ? 1 : 3, role, 0));
        var rooms = mock(ClassroomMapper.class);
        var members = mock(ClassMemberMapper.class);
        var courses = mock(CourseClient.class);
        var rows = new ArrayList<Classroom>();
        var memberships = new ArrayList<ClassMember>();
        var counts = new ArrayList<Map<String, Object>>();
        for (long id = 1; id <= count; id++) {
            var room = new Classroom();
            room.id = id;
            room.courseId = 1000 + id;
            room.teacherId = 1L;
            room.name = "班级" + id;
            room.status = "ACTIVE";
            room.joinCode = "CODE" + id;
            room.version = 0;
            rows.add(room);
            var member = new ClassMember();
            member.classId = id;
            member.studentId = 3L;
            member.enrollmentId = 2000 + id;
            member.status = "ACTIVE";
            memberships.add(member);
            if (id % 3 != 0) counts.add(Map.of("class_id", id, "n", id % 3));
        }
        when(rooms.selectCount(any())).thenReturn((long) count);
        when(rooms.selectList(any())).thenReturn(rows);
        when(members.selectList(any())).thenReturn(memberships);
        when(members.selectMaps(any())).thenReturn(counts);
        when(courses.staffIds(false)).thenReturn(rows.stream().map(r -> r.courseId).toList());
        when(courses.courses(anyString()))
                .thenAnswer(
                        call ->
                                Arrays.stream(call.<String>getArgument(0).split(","))
                                        .map(Long::parseLong)
                                        .map(
                                                id ->
                                                        new Contracts.CourseValidation(
                                                                id,
                                                                1,
                                                                "PUBLISHED",
                                                                1,
                                                                "课程" + id,
                                                                teacher ? "OWNER" : "NONE"))
                                        .toList());
        var service =
                new ClassroomService(
                        rooms,
                        members,
                        mock(EnrollmentMapper.class),
                        courses,
                        mock(DirectoryClient.class),
                        new Remote(),
                        mock(PlatformTransactionManager.class));
        var page = (Map<?, ?>) service.list(1, 100, teacher, null, null);
        var output = (List<Map<?, ?>>) page.get("items");
        assertEquals(count, output.size());
        assertEquals((long) count, page.get("total"));
        for (int index = 0; index < count; index++) {
            var row = output.get(index);
            long id = index + 1;
            assertEquals(Long.toString(id), row.get("id"));
            assertEquals(id % 3, row.get("memberCount"));
            assertEquals(teacher, row.containsKey("joinCode"));
            if (!teacher) assertEquals(Long.toString(2000 + id), row.get("enrollmentId"));
        }
        verify(rooms, times(1)).selectCount(any());
        verify(rooms, times(1)).selectList(any());
        verify(courses, times(1)).courses(anyString());
        verify(courses, never()).course(anyLong());
        verify(members, times(1)).selectMaps(any());
        verify(members, times(teacher ? 0 : 2)).selectList(any());
        verify(members, never()).selectCount(any());
        verify(members, never()).selectOne(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100, 101, 201})
    void reviewQueriesGrowByBatchRatherThanByCard(int count) {
        Actor.set(new Actor(3, "STUDENT", 0));
        var cards = mock(ReviewCardMapper.class);
        var items = mock(AttemptItemMapper.class);
        var runs = mock(QuizRunMapper.class);
        var attempts = mock(QuizAttemptMapper.class);
        var courses = mock(CourseClient.class);
        var rows = new ArrayList<ReviewCard>();
        for (long id = 1; id <= count; id++) {
            var card = new ReviewCard();
            card.id = id;
            card.quizId = 1000 + id;
            card.attemptItemId = 2000 + id;
            card.mastered = false;
            card.nextReviewAt = Views.now().minusDays(1);
            rows.add(card);
        }
        when(cards.selectList(any())).thenReturn(rows);
        when(courses.releases(anyString()))
                .thenAnswer(
                        call ->
                                Arrays.stream(call.<String>getArgument(0).split(","))
                                        .map(Long::parseLong)
                                        .map(
                                                id ->
                                                        new Contracts.QuizRelease(
                                                                id,
                                                                "ARCHIVED",
                                                                "PUBLISHED",
                                                                null,
                                                                1))
                                        .toList());
        when(items.selectByIds(anyCollection()))
                .thenAnswer(
                        call ->
                                call.<Collection<Long>>getArgument(0).stream()
                                        .map(
                                                id -> {
                                                    var item = new AttemptItem();
                                                    item.id = id;
                                                    item.prompt = "冻结题面" + id;
                                                    item.questionType = "SINGLE";
                                                    return item;
                                                })
                                        .toList());
        var manager = mock(PlatformTransactionManager.class);
        var practice =
                new PracticeService(
                        attempts,
                        items,
                        runs,
                        mock(RunQuestionMapper.class),
                        mock(EnrollmentMapper.class),
                        courses,
                        mock(DirectoryClient.class),
                        new Remote(),
                        cards,
                        manager);
        var service = new ReviewService(cards, items, practice, manager);
        var page = (Map<?, ?>) service.list(null, "DUE", 1, 20);
        var output = (List<Map<?, ?>>) page.get("items");
        assertEquals(Math.min(count, 20), output.size());
        assertEquals(count, page.get("total"));
        assertEquals(count, ((Map<?, ?>) page.get("summary")).get("total"));
        assertEquals("冻结题面2001", output.getFirst().get("prompt"));
        int batches = (count + 99) / 100;
        verify(courses, times(batches)).releases(anyString());
        verify(courses, never()).snapshot(anyLong());
        verify(runs, times(batches)).selectMaps(any());
        verify(attempts, times(batches)).selectMaps(any());
        verify(items, times(1)).selectByIds(argThat(ids -> ids.size() == Math.min(count, 20)));
        verify(items, never()).selectById(anyLong());
        verify(runs, never()).selectCount(any());
        verify(attempts, never()).selectCount(any());
    }
}
