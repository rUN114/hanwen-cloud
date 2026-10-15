package org.hanwen.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.slf4j.*;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

public class AuthFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(AuthFilter.class);
    private final JwtCodec jwt;
    private final AuthLookup lookup;
    private final String key;

    public AuthFilter(JwtCodec j, AuthLookup l, String k) {
        jwt = j;
        lookup = l;
        key = k;
    }

    protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain chain)
            throws ServletException, IOException {
        String tid = r.getHeader("X-Trace-Id");
        if (tid == null || !tid.matches("[A-Za-z0-9-]{8,64}")) tid = UUID.randomUUID().toString();
        Trace.set(tid);
        s.setHeader("X-Trace-Id", tid);
        try {
            String p = r.getRequestURI();
            boolean internal = p.startsWith("/internal/");
            if (internal
                    && !MessageDigest.isEqual(
                            key.getBytes(StandardCharsets.UTF_8),
                            String.valueOf(r.getHeader("X-Service-Key"))
                                    .getBytes(StandardCharsets.UTF_8)))
                throw ApiException.forbidden();
            if (p.startsWith("/api/")
                            && !p.equals("/api/auth/login")
                            && !p.equals("/api/auth/refresh")
                    || internal && !p.matches("/internal/users/[0-9]+/auth-state")) {
                Actor a = jwt.parse(r.getHeader("Authorization"));
                if (p.endsWith("/has-data")) {
                    if (!a.role().equals("ADMIN")) throw ApiException.forbidden();
                } else {
                    var state = lookup.find(a.id());
                    if (state == null
                            || !state.status().equals("ENABLED")
                            || state.authVersion() != a.version()
                            || !state.role().equals(a.role()))
                        throw new ApiException(401, "AUTH_INVALID", "登录已失效，请重新登录");
                }
                Actor.set(a);
            }
            chain.doFilter(r, s);
        } catch (ApiException e) {
            s.setStatus(e.status);
            s.setContentType("application/json;charset=UTF-8");
            s.getWriter()
                    .write(
                            "{\"code\":\""
                                    + e.code
                                    + "\",\"message\":\""
                                    + e.getMessage()
                                    + "\",\"data\":null,\"traceId\":\""
                                    + Trace.id()
                                    + "\"}");
        } finally {
            LOG.info(
                    "traceId={} method={} path={} status={} port={}",
                    Trace.id(),
                    r.getMethod(),
                    r.getRequestURI(),
                    s.getStatus(),
                    r.getLocalPort());
            Actor.clear();
            Trace.clear();
        }
    }
}
