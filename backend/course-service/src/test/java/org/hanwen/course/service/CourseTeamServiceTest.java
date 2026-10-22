package org.hanwen.course.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.hanwen.common.*;
import org.hanwen.course.mapper.CourseTeamEventMapper;
import org.hanwen.course.mapper.CourseTeamMapper;
import org.hanwen.course.mapper.CourseTeamStateMapper;
import org.hanwen.course.model.Course;
import org.hanwen.course.model.CourseTeamState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CourseTeamServiceTest {
    private final CourseService courses = mock(CourseService.class);
    private final CourseTeamMapper members = mock(CourseTeamMapper.class);
    private final CourseTeamStateMapper states = mock(CourseTeamStateMapper.class);
    private final CourseTeamEventMapper events = mock(CourseTeamEventMapper.class);
    private final DirectoryClient directory = mock(DirectoryClient.class);
    private final Remote remote = mock(Remote.class);
    private final CourseTeamService service =
            new CourseTeamService(courses, members, states, events, directory, remote);
    private Course course;

    @BeforeEach
    void prepareCourse() {
        course = new Course();
        course.id = 5000L;
        course.teacherId = Long.valueOf("1000");
        course.status = "PUBLISHED";
        var state = new CourseTeamState();
        state.id = course.id;
        state.version = 3;
        when(courses.locked(course.id)).thenReturn(course);
        when(states.selectById(course.id)).thenReturn(state);
        doCallRealMethod().when(courses).principal(course);
        Actor.set(new Actor(1000L, "TEACHER", 0));
    }

    @AfterEach
    void clearActor() {
        Actor.clear();
    }

    @Test
    void sameTeacherOutsideLongCacheIsRejectedBeforeTeamLookup() {
        Long targetId = Long.valueOf("1000");
        assertNotSame(course.teacherId, targetId);

        var error =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.transfer(
                                        course.id,
                                        new CourseTeamService.Transfer(targetId, true, "交接", 3)));

        assertEquals(400, error.status);
        assertEquals("请选择交接教师与原主讲安排", error.getMessage());
        verifyNoInteractions(members, events, directory, remote);
        verify(courses, never()).savePrincipal(any());
    }

    @Test
    void differentTeacherStillRequiresActiveCoTeacherMembership() {
        var error =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.transfer(
                                        course.id,
                                        new CourseTeamService.Transfer(1001L, true, "交接", 3)));

        assertEquals(400, error.status);
        assertEquals("须交接给当前协同教师", error.getMessage());
        verify(members).selectOne(any());
        verifyNoInteractions(events, directory, remote);
        verify(courses, never()).savePrincipal(any());
    }

    @Test
    void onlyCurrentPrincipalCanTransferTheCourse() {
        Actor.set(new Actor(1001L, "TEACHER", 0));

        var error =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.transfer(
                                        course.id,
                                        new CourseTeamService.Transfer(1002L, true, "交接", 3)));

        assertEquals(403, error.status);
        verifyNoInteractions(states, members, events, directory, remote);
        verify(courses, never()).savePrincipal(any());
    }

    @Test
    void staleTeamVersionIsRejectedBeforeTargetLookup() {
        var error =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.transfer(
                                        course.id,
                                        new CourseTeamService.Transfer(1001L, true, "交接", 2)));

        assertEquals(409, error.status);
        assertEquals("VERSION_CONFLICT", error.code);
        verifyNoInteractions(members, events, directory, remote);
        verify(courses, never()).savePrincipal(any());
    }
}
