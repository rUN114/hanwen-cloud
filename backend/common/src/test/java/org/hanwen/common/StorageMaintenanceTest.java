package org.hanwen.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

class StorageMaintenanceTest {
    @TempDir Path storage;

    @Test
    void competingMaintenanceIsSkippedAndLockIsReleased() throws Exception {
        var competingRan = new AtomicBoolean();
        assertTrue(
                StorageMaintenance.tryRunExclusive(
                        storage,
                        () -> {
                            try {
                                assertFalse(
                                        StorageMaintenance.tryRunExclusive(
                                                storage, () -> competingRan.set(true)));
                            } catch (java.io.IOException e) {
                                throw new java.io.UncheckedIOException(e);
                            }
                        }));
        assertFalse(competingRan.get());
        assertTrue(StorageMaintenance.tryRunExclusive(storage, () -> competingRan.set(true)));
        assertTrue(competingRan.get());
    }

    @Test
    void failureIsPropagatedAndDoesNotKeepTheLock() throws Exception {
        var failure = new IllegalStateException("maintenance failure");
        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                StorageMaintenance.tryRunExclusive(
                                        storage,
                                        () -> {
                                            throw failure;
                                        })));
        assertTrue(StorageMaintenance.tryRunExclusive(storage, () -> {}));
    }
}
