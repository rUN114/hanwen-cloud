package org.hanwen.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.hanwen.common.*;
import org.hanwen.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;

class LoginCapacityTest {
    private final UserMapper users = mock(UserMapper.class);
    private final UserService service =
            new UserService(users, null, "unused", null, null, null, new Remote(), null);

    void fill(long expires) {
        for (int i = 0; i < UserService.MAX_ATTEMPTS; i++)
            service.attempts.put("127.0.0.1:user" + i, new UserService.Attempt(5, expires));
    }

    @Test
    void capacityDoesNotGrowOrEvictExistingLocks() {
        fill(System.currentTimeMillis() + 300000);
        var error =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.login(
                                        new UserService.Login("new-user", "invalid"), "127.0.0.1"));
        assertEquals("LOGIN_BUSY", error.code);
        assertEquals(UserService.MAX_ATTEMPTS, service.attempts.size());
        assertEquals(5, service.attempts.get("127.0.0.1:user0").count());
        verifyNoInteractions(users);
    }

    @Test
    void existingLockedAccountKeepsItsOriginalLimitAtCapacity() {
        fill(System.currentTimeMillis() + 300000);
        var error =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.login(
                                        new UserService.Login("user0", "invalid"), "127.0.0.1"));
        assertEquals("LOGIN_LIMITED", error.code);
        verifyNoInteractions(users);
    }

    @Test
    void expiredEntriesReleaseCapacityBeforeNextFailure() {
        fill(System.currentTimeMillis() - 1000);
        var error =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.login(
                                        new UserService.Login("new-user", "x".repeat(73)),
                                        "127.0.0.1"));
        assertEquals(401, error.status);
        assertEquals(1, service.attempts.size());
        assertEquals(1, service.attempts.get("127.0.0.1:new-user").count());
    }

    @Test
    void oversizedUsernameIsNotRetainedInFailureState() {
        var error =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.login(
                                        new UserService.Login("x".repeat(100000), "x".repeat(73)),
                                        "127.0.0.1"));
        assertEquals(401, error.status);
        assertEquals(java.util.Set.of("127.0.0.1:"), service.attempts.keySet());
    }
}
