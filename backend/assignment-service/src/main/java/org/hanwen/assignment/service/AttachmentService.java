package org.hanwen.assignment.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.assignment.mapper.*;
import org.hanwen.assignment.model.*;
import org.hanwen.common.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

@Service
public class AttachmentService {
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(AttachmentService.class);
    final AssignmentService assignments;
    final AttachmentMapper files;
    final Path storage;

    public AttachmentService(
            AssignmentService a,
            AttachmentMapper f,
            @Value("${HW_STORAGE_DIR:${user.dir}/.local/uploads}") String dir)
            throws IOException {
        assignments = a;
        files = f;
        storage = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(storage);
    }

    void writable(long id) {
        Views.role("STUDENT");
        var t = assignments.tasks().lock(id);
        if (t == null) throw ApiException.missing();
        assignments.student(t, true);
        assignments.writable(t);
        var s =
                assignments
                        .submissions()
                        .selectOne(
                                new QueryWrapper<Submission>()
                                        .eq("assignment_id", id)
                                        .eq("student_id", Actor.current().id()));
        if (s != null && s.status.equals("GRADED")) throw ApiException.conflict("已评分答卷附件只读");
    }

    static Object view(Attachment f) {
        var v =
                Views.map(
                        "id",
                        f.id,
                        "assignmentId",
                        f.assignmentId,
                        "studentId",
                        f.studentId,
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

    public Object list(long assignmentId, Long submissionId) {
        long student = Actor.current().id();
        if (submissionId != null) {
            var s = assignments.submission(submissionId);
            assignments.submissionDetail(submissionId);
            if (s.assignmentId != assignmentId) throw ApiException.missing();
            student = s.studentId;
        } else {
            Views.role("STUDENT");
            assignments.student(assignments.task(assignmentId), false);
        }
        return files
                .selectList(
                        new QueryWrapper<Attachment>()
                                .eq("assignment_id", assignmentId)
                                .eq("student_id", student)
                                .eq("status", "ACTIVE")
                                .orderByAsc("id"))
                .stream()
                .map(AttachmentService::view)
                .toList();
    }

    @Transactional
    public Object upload(long id, MultipartFile file) throws IOException {
        writable(id);
        if (file == null || file.isEmpty() || file.getSize() > 20971520)
            throw ApiException.bad("附件须为1字节至20MB");
        if (files.selectCount(
                        new QueryWrapper<Attachment>()
                                .eq("assignment_id", id)
                                .eq("student_id", Actor.current().id())
                                .eq("status", "ACTIVE"))
                >= 5) throw ApiException.bad("每份习作最多5个附件");
        byte[] data = file.getBytes();
        String name = String.valueOf(file.getOriginalFilename()).replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N}._() -]", "_");
        if (name.isBlank() || name.length() > 160) throw ApiException.bad("文件名须为1至160字");
        var f = new Attachment();
        f.assignmentId = id;
        f.studentId = Actor.current().id();
        f.originalName = name;
        f.mimeType = FileRules.type(name, data);
        f.bytes = (long) data.length;
        f.storageName = UUID.randomUUID() + ".bin";
        f.status = "ACTIVE";
        f.createdAt = Views.now();
        try {
            f.sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        Path p = storage.resolve(f.storageName);
        Files.write(p, data, StandardOpenOption.CREATE_NEW);
        try {
            files.insert(f);
        } catch (RuntimeException e) {
            Files.deleteIfExists(p);
            throw e;
        }
        return view(f);
    }

    public Attachment readable(long id) {
        var f = files.selectById(id);
        if (f == null || !f.status.equals("ACTIVE")) throw ApiException.missing();
        var t = assignments.task(f.assignmentId);
        if (Actor.current().role().equals("TEACHER")) {
            assignments.owner(t);
            var s =
                    assignments
                            .submissions()
                            .selectOne(
                                    new QueryWrapper<Submission>()
                                            .eq("assignment_id", t.id)
                                            .eq("student_id", f.studentId));
            if (s == null) throw ApiException.forbidden();
        } else {
            Views.role("STUDENT");
            if (f.studentId != Actor.current().id()) throw ApiException.forbidden();
            assignments.student(t, false);
        }
        return f;
    }

    public Path file(Attachment f) {
        if (f.storageName == null) throw ApiException.missing();
        Path p = storage.resolve(f.storageName).normalize();
        if (!p.startsWith(storage) || !Files.isRegularFile(p)) throw ApiException.missing();
        return p;
    }

    @Transactional
    public void remove(long id) {
        var f = readable(id);
        if (f.studentId != Actor.current().id()) throw ApiException.forbidden();
        writable(f.assignmentId);
        f.status = "REMOVED";
        f.removedAt = Views.now();
        files.updateById(f);
    }

    @Scheduled(fixedDelay = 21_600_000, initialDelay = 60_000)
    void purgeRemovedAttachments() {
        try {
            if (!StorageMaintenance.tryRunExclusive(storage, this::purgeExpiredFiles))
                LOG.debug("备份或其他回收正在使用上传目录，本轮附件回收跳过");
        } catch (IOException e) {
            LOG.warn("无法锁定上传目录，本轮附件回收未执行", e);
        }
    }

    private void purgeExpiredFiles() {
        // 按主键推进本轮批次，坏文件留待下次重试，不阻挡后面的正常记录。
        long after = 0;
        var cutoff = Views.now().minusDays(30);
        while (true) {
            var rows =
                    files.selectList(
                            new QueryWrapper<Attachment>()
                                    .eq("status", "REMOVED")
                                    .isNotNull("storage_name")
                                    .lt("removed_at", cutoff)
                                    .gt("id", after)
                                    .orderByAsc("id")
                                    .last("LIMIT 200"));
            if (rows.isEmpty()) return;
            for (var f : rows) {
                Path path = storage.resolve(f.storageName).normalize();
                if (!path.startsWith(storage)) {
                    LOG.error("附件路径越界，跳过 id={}", f.id);
                    continue;
                }
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    LOG.warn("附件清理失败，下轮重试 id={}", f.id, e);
                    continue;
                }
                // 文件已不存在也可补记完成，覆盖删盘成功、数据库写入失败的重试。
                files.update(
                        null,
                        new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<
                                        Attachment>()
                                .eq("id", f.id)
                                .eq("status", "REMOVED")
                                .eq("storage_name", f.storageName)
                                .set("storage_name", null));
            }
            after = rows.getLast().id;
            if (rows.size() < 200) return;
        }
    }
}
