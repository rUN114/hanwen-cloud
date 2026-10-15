package org.hanwen.gateway;

import org.hanwen.common.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Component
public class GatewayAuthentication implements GlobalFilter, Ordered {
    private final JwtCodec jwt;
    private final WebClient web;
    private final String key;

    public GatewayAuthentication(
            JwtCodec j, WebClient.Builder b, @Value("${hw.service-key}") String k) {
        jwt = j;
        web = b.build();
        key = k;
    }

    public int getOrder() {
        return -100;
    }

    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String trace = UUID.randomUUID().toString();
        var request =
                exchange.getRequest()
                        .mutate()
                        .headers(
                                h -> {
                                    for (String n :
                                            new String[] {
                                                "X-User-Id",
                                                "X-Role",
                                                "X-Internal-Origin",
                                                "X-Service-Key",
                                                "X-Trace-Id"
                                            }) h.remove(n);
                                    h.set("X-Trace-Id", trace);
                                })
                        .build();
        var clean = exchange.mutate().request(request).build();
        clean.getResponse().getHeaders().set("X-Trace-Id", trace);
        if (java.util.Set.of("/api/auth/login", "/api/auth/refresh")
                .contains(request.getPath().value())) return chain.filter(clean);
        Actor actor;
        try {
            actor = jwt.parse(request.getHeaders().getFirst("Authorization"));
        } catch (ApiException e) {
            return error(clean, e, trace);
        }
        return web.get()
                .uri("http://hw-user-service/internal/users/" + actor.id() + "/auth-state")
                .header("X-Service-Key", key)
                .header("X-Trace-Id", trace)
                .retrieve()
                .bodyToMono(Contracts.AuthState.class)
                .timeout(Duration.ofSeconds(3))
                .flatMap(
                        state -> {
                            if (!state.status().equals("ENABLED")
                                    || state.authVersion() != actor.version()
                                    || !state.role().equals(actor.role()))
                                return error(
                                        clean,
                                        new ApiException(401, "AUTH_INVALID", "登录已失效，请重新登录"),
                                        trace);
                            return chain.filter(clean);
                        })
                .onErrorResume(e -> error(clean, ApiException.unavailable(), trace));
    }

    private Mono<Void> error(ServerWebExchange x, ApiException e, String trace) {
        x.getResponse().setStatusCode(HttpStatusCode.valueOf(e.status));
        x.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] b =
                ("{\"code\":\""
                                + e.code
                                + "\",\"message\":\""
                                + e.getMessage()
                                + "\",\"data\":null,\"traceId\":\""
                                + trace
                                + "\"}")
                        .getBytes(StandardCharsets.UTF_8);
        return x.getResponse().writeWith(Mono.just(x.getResponse().bufferFactory().wrap(b)));
    }
}
