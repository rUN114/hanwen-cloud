package org.hanwen.assignment.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IntegrationIsolationTest {
    @Test
    void wrongPortIsRejectedBeforeOpeningAConnection() {
        String url = System.getProperty("hanwen.test.jdbcUrl");
        String directory = System.getProperty("hanwen.test.dataDir");
        try {
            System.setProperty(
                    "hanwen.test.jdbcUrl",
                    "jdbc:mysql://127.0.0.1:3307/hw_assignment?connectionTimeZone=%2B08:00");
            System.setProperty("hanwen.test.dataDir", "/unused/test-path");
            var error =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new SubmitConcurrencyIT.Config().dataSource());
            assertTrue(error.getMessage().contains("isolated port"));
        } finally {
            if (url == null) System.clearProperty("hanwen.test.jdbcUrl");
            else System.setProperty("hanwen.test.jdbcUrl", url);
            if (directory == null) System.clearProperty("hanwen.test.dataDir");
            else System.setProperty("hanwen.test.dataDir", directory);
        }
    }
}
