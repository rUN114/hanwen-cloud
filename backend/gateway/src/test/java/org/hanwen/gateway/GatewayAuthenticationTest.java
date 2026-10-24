package org.hanwen.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.hanwen.common.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.*;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

class GatewayAuthenticationTest {
    private GatewayAuthentication filter(ExchangeFunction exchange) {
        var jwt = mock(JwtCodec.class);
        when(jwt.parse("Bearer test")).thenReturn(new Actor(3, "STUDENT", 0));
        return new GatewayAuthentication(
                jwt, WebClient.builder().exchangeFunction(exchange), "test-key");
    }

    private MockServerWebExchange request() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/courses").header("Authorization", "Bearer test"));
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 500})
    void distinguishesMissingAccountFromDependencyFailure(int status) {
        var exchange = request();
        var forwarded = new AtomicBoolean();
        filter(_ -> Mono.just(ClientResponse.create(HttpStatus.valueOf(status)).build()))
                .filter(
                        exchange,
                        _ -> {
                            forwarded.set(true);
                            return Mono.empty();
                        })
                .block(Duration.ofSeconds(5));
        assertEquals(status == 404 ? 401 : 503, exchange.getResponse().getStatusCode().value());
        assertFalse(forwarded.get());
    }

    @Test
    void connectionFailureDoesNotExpireSession() {
        var exchange = request();
        filter(_ -> Mono.error(new java.io.IOException("unavailable")))
                .filter(exchange, _ -> Mono.error(new AssertionError("must not forward")))
                .block(Duration.ofSeconds(5));
        assertEquals(503, exchange.getResponse().getStatusCode().value());
    }

    @Test
    void businessNotFoundIsNotReclassifiedAsExpiredIdentity() {
        var exchange = request();
        filter(
                        _ ->
                                Mono.just(
                                        ClientResponse.create(HttpStatus.OK)
                                                .header("Content-Type", "application/json")
                                                .body(
                                                        "{\"id\":3,\"role\":\"STUDENT\",\"status\":\"ENABLED\",\"authVersion\":0}")
                                                .build()))
                .filter(exchange, _ -> Mono.error(ApiException.missing()))
                .block(Duration.ofSeconds(5));
        assertEquals(404, exchange.getResponse().getStatusCode().value());
    }
}
