package org.hanwen.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.common.*;
import org.hanwen.course.mapper.*;
import org.hanwen.course.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

import javax.imageio.ImageIO;

@Service
public class ResourceService {
    final CourseService course;
    final ResourceFileMapper files;
    final Path storage;

    public ResourceService(
            CourseService c,
            ResourceFileMapper f,
            @Value("${HW_STORAGE_DIR:${user.dir}/.local/uploads}") String dir)
            throws IOException {
        course = c;
        files = f;
        storage = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(storage);
    }

    static Map<String, Object> view(ResourceFile f) {
        var v =
                Views.map(
                        "id",
                        f.id,
                        "courseId",
                        f.courseId,
                        "chapterId",
                        f.chapterId,
                        "title",
                        f.title,
                        "description",
                        f.description,
                        "originalName",
                        f.originalName,
                        "mimeType",
                        f.mimeType,
                        "sha256",
                        f.sha256,
                        "createdAt",
                        f.createdAt);
        v.put("bytes", f.bytes);
        return v;
    }

    public Object list(long id, Long chapterId, int page, int size) {
        course.readable(course.required(id), true);
        int offset = Views.offset(page, size);
        if (chapterId != null) course.chapter(id, chapterId);
        var q = new QueryWrapper<ResourceFile>().eq("course_id", id).eq("status", "ACTIVE");
        if (chapterId != null) q.and(w -> w.eq("chapter_id", chapterId).or().isNull("chapter_id"));
        long total = files.selectCount(q);
        return Views.page(
                files
                        .selectList(q.orderByDesc("id").last("LIMIT " + size + " OFFSET " + offset))
                        .stream()
                        .map(ResourceService::view)
                        .toList(),
                page,
                size,
                total);
    }

    @Transactional
    public Object upload(
            long id, Long chapterId, String title, String description, MultipartFile file)
            throws IOException {
        Views.role("TEACHER");
        if (file == null || file.isEmpty() || file.getSize() > 20971520)
            throw ApiException.bad("文件须为1字节至20MB");
        var c = course.locked(id);
        course.owner(c);
        if (c.status.equals("ARCHIVED")) throw ApiException.conflict("归档课程资源只读");
        if (chapterId != null) course.chapter(id, chapterId);
        byte[] data = file.getBytes();
        String name = String.valueOf(file.getOriginalFilename()).replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N}._() -]", "_");
        if (name.isBlank() || name.length() > 160) throw ApiException.bad("文件名须为1至160字");
        String mime = FileRules.type(name, data);
        ResourceFile f = new ResourceFile();
        f.courseId = id;
        f.chapterId = chapterId;
        f.title = Views.text(title, 100, "资源标题");
        f.description = Views.optional(description, 1000, "资源说明");
        f.originalName = name;
        f.mimeType = mime;
        f.bytes = (long) data.length;
        f.storageName = UUID.randomUUID() + ".bin";
        f.status = "ACTIVE";
        f.createdAt = Views.now();
        try {
            f.sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
        Path path = storage.resolve(f.storageName);
        Files.write(path, data, StandardOpenOption.CREATE_NEW);
        try {
            files.insert(f);
        } catch (RuntimeException e) {
            Files.deleteIfExists(path);
            throw e;
        }
        return view(f);
    }

    public ResourceFile readable(long id) {
        var f = files.selectById(id);
        if (f == null || !f.status.equals("ACTIVE")) throw ApiException.missing();
        course.readable(course.required(f.courseId), true);
        return f;
    }

    public Path file(ResourceFile f) {
        Path p = storage.resolve(f.storageName).normalize();
        if (!p.startsWith(storage) || !Files.isRegularFile(p)) throw ApiException.missing();
        return p;
    }

    @Transactional
    public void remove(long id) {
        var initial = files.selectById(id);
        if (initial == null) throw ApiException.missing();
        var c = course.locked(initial.courseId);
        course.owner(c);
        if (c.status.equals("ARCHIVED")) throw ApiException.conflict("归档资源只读");
        var f = files.lock(id);
        f.status = "REMOVED";
        files.updateById(f);
    }
}
