package org.hanwen.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

class RemoteTest {
    @Test
    void repeatedDependencyFailuresOpenCircuit() {
        Remote remote = new Remote();
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 4; i++)
            assertEquals(
                    503,
                    assertThrows(
                                    ApiException.class,
                                    () ->
                                            remote.call(
                                                    "test",
                                                    () -> {
                                                        calls.incrementAndGet();
                                                        throw new IllegalStateException();
                                                    }))
                            .status);
        assertEquals(3, calls.get(), "Fourth call must be blocked by the circuit");
    }

    @Test
    void businessRejectionDoesNotOpenCircuit() {
        Remote remote = new Remote();
        for (int i = 0; i < 10; i++)
            assertEquals(
                    403,
                    assertThrows(
                                    ApiException.class,
                                    () ->
                                            remote.call(
                                                    "test",
                                                    () -> {
                                                        throw ApiException.forbidden();
                                                    }))
                            .status);
        assertEquals("ok", remote.call("test", () -> "ok"));
    }
}
