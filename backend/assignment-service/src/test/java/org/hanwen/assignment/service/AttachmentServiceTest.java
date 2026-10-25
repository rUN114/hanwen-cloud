package org.hanwen.assignment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.assignment.mapper.AttachmentMapper;
import org.hanwen.assignment.model.Attachment;
import org.hanwen.common.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.stream.LongStream;

class AttachmentServiceTest {
    @TempDir Path dir;

    Attachment item(long id, String name) {
        var f = new Attachment();
        f.id = id;
        f.storageName = name;
        f.status = "REMOVED";
        return f;
    }

    @AfterEach
    void clear() {
        Actor.clear();
    }

    @Test
    void purgeRequiresExpiredRemovalAndCanRepeatAfterTheFileDisappears() throws Exception {
        var mapper = mock(AttachmentMapper.class);
        var service =
                new AttachmentService(
                        mock(AssignmentService.class),
                        mapper,
                        dir.toString(),
                        mock(org.springframework.transaction.PlatformTransactionManager.class));
        var f = item(1, "old.bin");
        Files.writeString(dir.resolve(f.storageName), "附件");
        when(mapper.selectList(any())).thenReturn(List.of(f));
        service.purgeRemovedAttachments();
        service.purgeRemovedAttachments();
        assertFalse(Files.exists(dir.resolve("old.bin")));
        verify(mapper, times(2))
                .selectList(
                        argThat(
                                q -> {
                                    String sql = q.getSqlSegment();
                                    return sql.contains("status =")
                                            && sql.contains("storage_name IS NOT NULL")
                                            && sql.contains("removed_at <")
                                            && sql.contains("ORDER BY id ASC")
                                            && ((com.baomidou.mybatisplus.core.conditions.query
                                                                            .QueryWrapper<
                                                                    Attachment>)
                                                            q)
                                                    .getParamNameValuePairs()
                                                    .containsValue("REMOVED");
                                }));
        verify(mapper, times(2))
                .update(isNull(), argThat(q -> q.getSqlSet().contains("storage_name")));
    }

    @Test
    void traversalAndFailedDeletionAreNeverRecordedAsSuccess() throws Exception {
        var mapper = mock(AttachmentMapper.class);
        var storage = dir.resolve("uploads");
        var service =
                new AttachmentService(
                        mock(AssignmentService.class),
                        mapper,
                        storage.toString(),
                        mock(org.springframework.transaction.PlatformTransactionManager.class));
        Files.writeString(dir.resolve("outside.bin"), "保留");
        Files.createDirectory(storage.resolve("blocked.bin"));
        Files.writeString(storage.resolve("blocked.bin/child"), "保留");
        when(mapper.selectList(any()))
                .thenReturn(List.of(item(1, "../outside.bin"), item(2, "blocked.bin")));
        service.purgeRemovedAttachments();
        verify(mapper, never()).update(any(), any());
        assertTrue(Files.exists(dir.resolve("outside.bin")));
        assertTrue(Files.exists(storage.resolve("blocked.bin/child")));
    }

    @Test
    void purgeProcessesMoreThanOnePageWithoutRequeryingFailedRows() throws Exception {
        var mapper = mock(AttachmentMapper.class);
        var service =
                new AttachmentService(
                        mock(AssignmentService.class),
                        mapper,
                        dir.toString(),
                        mock(org.springframework.transaction.PlatformTransactionManager.class));
        var first = LongStream.rangeClosed(1, 200).mapToObj(id -> item(id, id + ".bin")).toList();
        when(mapper.selectList(any())).thenReturn(first, List.of(item(201, "201.bin")));
        service.purgeRemovedAttachments();
        verify(mapper, times(2)).selectList(any());
        verify(mapper, times(201)).update(isNull(), any());
    }

    @Test
    void removingAnAttachmentStartsItsGracePeriod() throws Exception {
        Actor.set(new Actor(3, "STUDENT", 0));
        var mapper = mock(AttachmentMapper.class);
        var service =
                spy(
                        new AttachmentService(
                                mock(AssignmentService.class),
                                mapper,
                                dir.toString(),
                                mock(
                                        org.springframework.transaction.PlatformTransactionManager
                                                .class)));
        var f = item(1, "file.bin");
        f.assignmentId = 7L;
        f.studentId = 3L;
        f.status = "ACTIVE";
        when(mapper.selectById(1L)).thenReturn(f);
        doReturn(f).when(service).readable(1);
        doNothing().when(service).writable(7);
        var before = Views.now();
        service.remove(1);
        assertEquals("REMOVED", f.status);
        assertFalse(f.removedAt.isBefore(before));
        verify(mapper).updateById(f);
    }
}
