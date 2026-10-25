package org.hanwen.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.common.*;
import org.hanwen.user.mapper.UserMapper;
import org.hanwen.user.model.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

class UserAccessTest {
    private final UserMapper users = mock(UserMapper.class);
    private final UserAudit audit = mock(UserAudit.class);
    private final UserService service =
            new UserService(users, null, "unused", null, null, null, new Remote(), audit);

    @BeforeEach
    void signIn() {
        Actor.set(new Actor(1, "ADMIN", 0));
    }

    @AfterEach
    void clear() {
        Actor.clear();
    }

    private User administrator(long id) {
        var user = new User();
        user.id = id;
        user.role = "ADMIN";
        user.status = "ENABLED";
        user.authVersion = 0;
        return user;
    }

    @ParameterizedTest
    @CsvSource(
            value = {"NULL,ENABLED", "ADMIN,NULL"},
            nullValues = "NULL")
    void absentAccessFieldsAreRejectedBeforeReadingUsers(String role, String status) {
        var error =
                assertThrows(
                        ApiException.class,
                        () -> service.access(1, new UserService.Access(role, status)));
        assertEquals(400, error.status);
        verifyNoInteractions(users, audit);
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,DISABLED", "TEACHER,ENABLED"})
    void lastEnabledAdministratorCannotBeDisabledOrDemoted(String role, String status) {
        var user = administrator(1);
        when(users.selectList(any())).thenReturn(List.of(user));
        when(users.lock(1)).thenReturn(user);
        var error =
                assertThrows(
                        ApiException.class,
                        () -> service.access(1, new UserService.Access(role, status)));
        assertEquals(409, error.status);
        verify(users, never()).updateById(any(User.class));
        verifyNoInteractions(audit);
    }

    @Test
    void anotherEnabledAdministratorAllowsDisablingAndRevokesOldTokens() {
        var user = administrator(2);
        when(users.selectList(any())).thenReturn(List.of(administrator(1), user));
        when(users.lock(2)).thenReturn(user);
        service.access(2, new UserService.Access("ADMIN", "DISABLED"));
        assertEquals("DISABLED", user.status);
        assertEquals(1, user.authVersion);
        verify(users).updateById(user);
        verify(audit).write(eq(1L), eq(2L), eq("ACCESS_CHANGE"), anyString());
    }
}
