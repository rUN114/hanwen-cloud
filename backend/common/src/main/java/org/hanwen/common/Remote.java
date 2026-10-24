package org.hanwen.common;

import io.github.resilience4j.circuitbreaker.*;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class Remote {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Remote.class);
    private final CircuitBreakerRegistry registry =
            CircuitBreakerRegistry.of(
                    CircuitBreakerConfig.custom()
                            .slidingWindowSize(5)
                            .minimumNumberOfCalls(3)
                            .failureRateThreshold(50)
                            .waitDurationInOpenState(Duration.ofSeconds(5))
                            .permittedNumberOfCallsInHalfOpenState(1)
                            .ignoreException(e -> e instanceof ApiException a && a.status < 500)
                            .build());

    public <T> T call(String name, Supplier<T> action) {
        try {
            return registry.circuitBreaker(name).executeSupplier(action);
        } catch (ApiException e) {
            if (e.status >= 500) log.warn("Remote {} returned status {}", name, e.status);
            throw e;
        } catch (Exception e) {
            log.warn(
                    "Remote {} failed: {} / {}",
                    name,
                    e.getClass().getSimpleName(),
                    e.getCause() == null ? "none" : e.getCause().getClass().getSimpleName());
            log.debug("Remote {} failure detail", name, e);
            throw ApiException.unavailable();
        }
    }
}
