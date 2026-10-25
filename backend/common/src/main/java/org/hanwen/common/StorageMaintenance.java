package org.hanwen.common;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;

/** 备份工具与各服务的物理回收共用此文件的第一个字节锁。 */
public final class StorageMaintenance {
    public static final String LOCK_FILE = ".hanwen-maintenance.lock";

    private StorageMaintenance() {}

    public static boolean tryRunExclusive(Path storage, Runnable work) throws IOException {
        try (var channel =
                FileChannel.open(
                        storage.resolve(LOCK_FILE),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE)) {
            FileLock lock;
            try {
                lock = channel.tryLock(0, 1, false);
            } catch (OverlappingFileLockException e) {
                return false;
            }
            if (lock == null) return false;
            try (lock) {
                work.run();
                return true;
            }
        }
    }
}
