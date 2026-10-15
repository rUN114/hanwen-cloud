package org.hanwen.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.hanwen.common.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
public class UserController {
    final UserService service;
    final RefreshService refresh;
    final JwtCodec jwt;
    final UserAudit audit;
    final boolean secureCookie;
    final String origin;

    public UserController(
            UserService s,
            RefreshService refresh,
            JwtCodec jwt,
            UserAudit audit,
            @Value("${hw.cookie-secure:false}") boolean secureCookie,
            @Value("${HW_FRONTEND_ORIGIN:http://127.0.0.1:5173}") String origin) {
        service = s;
        this.refresh = refresh;
        this.jwt = jwt;
        this.audit = audit;
        this.secureCookie = secureCookie;
        this.origin = origin;
    }

    void checkOrigin(HttpServletRequest r, boolean required) {
        String provided = r.getHeader("Origin");
        if ((required && provided == null) || (provided != null && !provided.equals(origin)))
            throw ApiException.forbidden();
    }

    void cookie(HttpServletResponse response, RefreshService.CookieToken value) {
        response.setHeader("Cache-Control", "no-store");
        response.addHeader(
                "Set-Cookie",
                ResponseCookie.from("hw_refresh", value == null ? "" : value.token())
                        .httpOnly(true)
                        .secure(secureCookie)
                        .sameSite("Strict")
                        .path("/api/auth")
                        .maxAge(
                                value == null
                                        ? Duration.ZERO
                                        : Duration.between(Views.now(), value.expiresAt()))
                        .build()
                        .toString());
    }

    @Bean
    ApplicationRunner initialize(@Value("${hw.admin-password}") String password) {
        return args -> service.bootstrap(password);
    }

    @PostMapping("/api/auth/login")
    public Object login(
            @RequestBody UserService.Login in, HttpServletRequest r, HttpServletResponse response)
            throws Exception {
        checkOrigin(r, false);
        @SuppressWarnings("unchecked")
        var result = (Map<String, Object>) service.login(in, r.getRemoteAddr());
        var value = refresh.open(jwt.parse("Bearer " + result.get("accessToken")));
        cookie(response, value);
        result.put(
                "sessionExpiresAt",
                value.expiresAt().atOffset(java.time.ZoneOffset.ofHours(8)).toString());
        return ApiResponse.ok(result);
    }

    @PostMapping("/api/auth/refresh")
    public Object renew(
            @CookieValue(name = "hw_refresh", required = false) String token,
            HttpServletRequest r,
            HttpServletResponse response)
            throws Exception {
        checkOrigin(r, true);
        var renewed = refresh.renew(token);
        cookie(response, renewed.cookie());
        return ApiResponse.ok(renewed.data());
    }

    @PostMapping("/api/auth/logout")
    public Object logout(HttpServletResponse response) {
        service.logout();
        cookie(response, null);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/users/me")
    public Object me() {
        return ApiResponse.ok(service.profile());
    }

    @GetMapping("/api/admin/users")
    public Object users(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role) {
        return ApiResponse.ok(service.list(page, size, keyword, role));
    }

    @PostMapping("/api/admin/users")
    public Object create(@RequestBody UserService.Create in) {
        return ApiResponse.ok(service.create(in));
    }

    @PostMapping("/api/admin/users/import")
    public Object importUsers(
            @RequestBody UserService.ImportUsers in, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(service.importUsers(in));
    }

    @GetMapping("/api/admin/audit")
    public Object audit(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(audit.list(page, size));
    }

    @PutMapping("/api/admin/users/{id}/access")
    public Object access(@PathVariable long id, @RequestBody UserService.Access in) {
        return ApiResponse.ok(service.access(id, in));
    }

    @PostMapping("/api/admin/users/{id}/password-reset")
    public Object reset(@PathVariable long id, @RequestBody UserService.Reset in) {
        service.reset(id, in);
        return ApiResponse.ok(null);
    }

    @GetMapping("/internal/users/{id}/auth-state")
    public Contracts.AuthState state(@PathVariable long id) {
        var u = service.required(id);
        return new Contracts.AuthState(u.id, u.role, u.status, u.authVersion);
    }

    @PutMapping("/api/users/me")
    public Object profile(@RequestBody UserService.ProfileWrite in) {
        return ApiResponse.ok(service.profile(in));
    }

    @PutMapping("/api/users/me/password")
    public Object password(@RequestBody UserService.PasswordWrite in) {
        service.password(in);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/users/summary")
    public Object overview() {
        return ApiResponse.ok(service.overview());
    }

    @GetMapping("/internal/users/display-names")
    public java.util.List<Contracts.PublicUser> names(@RequestParam String ids) {
        return service.names(ids);
    }

    @GetMapping("/internal/users/teachers/{id}")
    public Contracts.StudentAccount teacher(@PathVariable long id) {
        Views.role("TEACHER");var u=service.required(id);
        if(!u.role.equals("TEACHER"))throw ApiException.bad("目标账号不是教师");
        return new Contracts.StudentAccount(u.id,u.username,u.displayName,u.status);
    }

    @PostMapping("/internal/users/teachers-by-username")
    public java.util.List<Contracts.StudentAccount> teachers(@RequestBody java.util.List<String> usernames) {
        return service.teachers(usernames);
    }

    @PostMapping("/internal/users/students-by-username")
    public java.util.List<Contracts.StudentAccount> students(
            @RequestBody java.util.List<String> usernames) {
        return service.students(usernames);
    }
}
