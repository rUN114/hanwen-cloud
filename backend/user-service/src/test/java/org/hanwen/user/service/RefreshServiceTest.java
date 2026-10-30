package org.hanwen.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.common.*;
import org.hanwen.user.mapper.UserMapper;
import org.hanwen.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

class RefreshServiceTest {
    final JdbcTemplate db = mock(JdbcTemplate.class);
    final UserMapper users = mock(UserMapper.class);
    final JwtCodec jwt = mock(JwtCodec.class);
    final UserAudit audit = mock(UserAudit.class);
    final RefreshService service = new RefreshService(db, users, jwt, audit, "unused");

    User account() {
        var user = new User();
        user.id = 3L;
        user.role = "STUDENT";
        user.status = "ENABLED";
        user.authVersion = 2;
        return user;
    }

    @Test
    void newLoginLastsSevenDays() {
        when(users.lock(3)).thenReturn(account());
        var before = Views.now().plusDays(7);
        var cookie = service.open(new Actor(3, "STUDENT", 2));
        assertFalse(cookie.expiresAt().isBefore(before));
        assertFalse(cookie.expiresAt().isAfter(Views.now().plusDays(7)));
        assertEquals(43, cookie.token().length());
    }

    @Test
    void wrongAccountCannotRotateAnotherAccountsCredential() {
        String token = "a".repeat(43);
        when(db.query(
                        anyString(),
                        org.mockito.ArgumentMatchers.<RowMapper<RefreshService.Stored>>any(),
                        eq(RefreshService.hash(token))))
                .thenReturn(List.of(new RefreshService.Stored(1, 3, 2, Views.now().plusDays(7))));
        var error = assertThrows(ApiException.class, () -> service.renew(token, 4L));
        assertEquals(401, error.status);
        verifyNoInteractions(users, jwt, audit);
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void renewalKeepsAbsoluteExpiryAndShortAccessToken() throws Exception {
        String token = "a".repeat(43);
        var end = Views.now().plusDays(6);
        when(db.query(
                        anyString(),
                        org.mockito.ArgumentMatchers.<RowMapper<RefreshService.Stored>>any(),
                        eq(RefreshService.hash(token))))
                .thenReturn(List.of(new RefreshService.Stored(1, 3, 2, end)));
        when(users.lock(3)).thenReturn(account());
        when(db.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jwt.issue(eq(3L), eq("STUDENT"), eq(2), eq("unused"), any())).thenReturn("access");
        var before = Views.now();
        var result = service.renew(token, 3L);
        assertEquals(3, result.userId());
        assertEquals(end, result.cookie().expiresAt());
        assertNotEquals(token, result.cookie().token());
        var data = (Map<?, ?>) result.data();
        var expiry =
                java.time.OffsetDateTime.parse((String) data.get("expiresAt")).toLocalDateTime();
        assertTrue(Duration.between(before, expiry).toMinutes() <= 15);
        assertTrue(expiry.isAfter(before.plusMinutes(14)));
    }
}
