package org.hanwen.common;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ErrorsTest {
    @Test
    void unexpectedFailureLogsCauseWithoutExposingItToCaller() {
        var logger = (Logger) LoggerFactory.getLogger(Errors.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        Trace.set("errors-test-trace");
        try {
            var failure = new IllegalStateException("private database detail");
            var response = new Errors().unexpected(failure);
            assertEquals(500, response.getStatusCode().value());
            assertEquals("INTERNAL_ERROR", response.getBody().code());
            assertEquals("errors-test-trace", response.getBody().traceId());
            assertFalse(response.getBody().message().contains(failure.getMessage()));
            var event = appender.list.getFirst();
            assertTrue(event.getFormattedMessage().contains("errors-test-trace"));
            assertNotNull(event.getThrowableProxy());
            assertEquals(failure.getMessage(), event.getThrowableProxy().getMessage());
        } finally {
            Trace.clear();
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
