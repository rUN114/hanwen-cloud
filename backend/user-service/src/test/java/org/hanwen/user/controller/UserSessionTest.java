package org.hanwen.user.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.Cookie;

import org.hanwen.common.*;
import org.hanwen.user.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

class UserSessionTest {
    final RefreshService refresh = mock(RefreshService.class);
    final UserService service = mock(UserService.class);
    final UserController controller =
            new UserController(service, refresh, null, null, false, "http://127.0.0.1:5173");

    @Test
    void selectsNamedCookieInsteadOfOtherAccountsLegacyCookie() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("Origin", "http://127.0.0.1:5173");
        request.setCookies(
                new Cookie("hw_refresh_3", "student"), new Cookie("hw_refresh_4", "teacher"));
        var response = new MockHttpServletResponse();
        when(refresh.renew("student", 3L))
                .thenReturn(
                        new RefreshService.Renewed(
                                Map.of(),
                                new RefreshService.CookieToken("next", Views.now().plusDays(7)),
                                3));
        controller.renew("teacher", 3L, request, response);
        verify(refresh).renew("student", 3L);
        var cookies = response.getHeaders("Set-Cookie");
        assertEquals(2, cookies.size());
        assertTrue(cookies.stream().anyMatch(c -> c.startsWith("hw_refresh_3=next;")));
        assertTrue(
                cookies.stream()
                        .allMatch(
                                c ->
                                        c.contains("HttpOnly")
                                                && c.contains("SameSite=Strict")
                                                && c.contains("Path=/api/auth")));
    }

    @Test
    void legacyCookieStillWorksForExistingClients() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("Origin", "http://127.0.0.1:5173");
        when(refresh.renew("old", null))
                .thenReturn(
                        new RefreshService.Renewed(
                                Map.of(),
                                new RefreshService.CookieToken("next", Views.now().plusHours(1)),
                                3));
        controller.renew("old", null, request, new MockHttpServletResponse());
        verify(refresh).renew("old", null);
    }

    @Test
    void rejectedOriginCannotUseNamedCookie() {
        var request = new MockHttpServletRequest();
        request.addHeader("Origin", "https://other.invalid");
        var error =
                assertThrows(
                        ApiException.class,
                        () ->
                                controller.renew(
                                        "token", 3L, request, new MockHttpServletResponse()));
        assertEquals(403, error.status);
        verifyNoInteractions(refresh);
    }

    @Test
    void logoutOnlyClearsCurrentAccountsCookieAndLegacyCookie() {
        Actor.set(new Actor(3, "STUDENT", 2));
        try {
            var response = new MockHttpServletResponse();
            controller.logout(response);
            verify(service).logout();
            var cookies = response.getHeaders("Set-Cookie");
            assertEquals(2, cookies.size());
            assertTrue(cookies.stream().allMatch(c -> c.contains("Max-Age=0")));
            assertTrue(cookies.stream().noneMatch(c -> c.startsWith("hw_refresh_4=")));
        } finally {
            Actor.clear();
        }
    }
}
