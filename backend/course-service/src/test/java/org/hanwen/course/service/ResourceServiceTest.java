package org.hanwen.course.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.common.*;
import org.hanwen.course.mapper.*;
import org.hanwen.course.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.List;

class ResourceServiceTest {
    @TempDir Path storage;

    @AfterEach
    void clear() {
        Actor.clear();
    }

    ResourceFile resource() {
        var f = new ResourceFile();
        f.id = 7L;
        f.courseId = 11L;
        f.status = "ACTIVE";
        f.storageName = "file.bin";
        return f;
    }

    @Test
    void referencedResourceCannotBeRemoved() throws Exception {
        Actor.set(new Actor(1, "TEACHER", 0));
        var course = mock(CourseService.class);
        var files = mock(ResourceFileMapper.class);
        var chapters = mock(ChapterMapper.class);
        var c = new Course();
        c.status = "PUBLISHED";
        when(course.locked(11)).thenReturn(c);
        var f = resource();
        when(files.selectById(7L)).thenReturn(f);
        when(files.lock(7)).thenReturn(f);
        var h = new Chapter();
        h.lessonDocument = "![插图](resource:7)";
        when(chapters.selectList(any())).thenReturn(List.of(h));
        var service = new ResourceService(course, files, chapters, storage.toString());
        assertEquals(409, assertThrows(ApiException.class, () -> service.remove(7)).status);
        assertEquals("ACTIVE", f.status);
        verify(files, never()).updateById(any(ResourceFile.class));
    }

    @Test
    void repeatedRemovalDoesNotRestartGracePeriod() throws Exception {
        Actor.set(new Actor(1, "TEACHER", 0));
        var course = mock(CourseService.class);
        var files = mock(ResourceFileMapper.class);
        var chapters = mock(ChapterMapper.class);
        var c = new Course();
        c.status = "PUBLISHED";
        when(course.locked(11)).thenReturn(c);
        var f = resource();
        f.status = "REMOVED";
        f.removedAt = Views.now().minusDays(3);
        var original = f.removedAt;
        when(files.selectById(7L)).thenReturn(f);
        when(files.lock(7)).thenReturn(f);
        new ResourceService(course, files, chapters, storage.toString()).remove(7);
        assertEquals(original, f.removedAt);
        verifyNoInteractions(chapters);
        verify(files, never()).updateById(any(ResourceFile.class));
    }

    @Test
    void purgeDeletesPhysicalFileAndFinalizesOnlyRemovedRecord() throws Exception {
        var files = mock(ResourceFileMapper.class);
        var service =
                new ResourceService(
                        mock(CourseService.class),
                        files,
                        mock(ChapterMapper.class),
                        storage.toString());
        var f = resource();
        f.status = "REMOVED";
        f.removedAt = Views.now().minusDays(31);
        Files.writeString(storage.resolve(f.storageName), "fixture");
        when(files.selectList(any())).thenReturn(List.of(f));
        service.purgeRemovedResources();
        assertFalse(Files.exists(storage.resolve("file.bin")));
        verify(files).selectList(argThat(q -> q.getSqlSegment().contains("removed_at <")));
        verify(files)
                .update(
                        isNull(),
                        argThat(
                                q ->
                                        q.getSqlSegment().contains("status =")
                                                && q.getSqlSet().contains("storage_name")));
    }
}
