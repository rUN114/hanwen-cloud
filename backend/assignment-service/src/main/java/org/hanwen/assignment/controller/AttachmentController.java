package org.hanwen.assignment.controller;

import org.hanwen.assignment.service.AttachmentService;
import org.hanwen.common.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;

@RestController
public class AttachmentController {
    final AttachmentService attachmentService;

    public AttachmentController(AttachmentService x) {
        attachmentService = x;
    }

    @GetMapping("/api/assignments/{id}/attachments")
    public Object list(@PathVariable long id, @RequestParam(required = false) Long submissionId) {
        return ApiResponse.ok(attachmentService.list(id, submissionId));
    }

    @PostMapping(value = "/api/assignments/{id}/attachments", consumes = "multipart/form-data")
    public Object upload(@PathVariable long id, @RequestPart MultipartFile file)
            throws IOException {
        return ApiResponse.ok(attachmentService.upload(id, file));
    }

    @GetMapping("/api/submissions/attachments/{id}/file")
    public ResponseEntity<FileSystemResource> file(@PathVariable long id) {
        var f = attachmentService.readable(id);
        return ResponseEntity.ok()
                .header("X-Content-Type-Options", "nosniff")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(f.originalName, StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .contentType(MediaType.parseMediaType(f.mimeType))
                .contentLength(f.bytes)
                .body(new FileSystemResource(attachmentService.file(f)));
    }

    @DeleteMapping("/api/submissions/attachments/{id}")
    public Object remove(@PathVariable long id) {
        attachmentService.remove(id);
        return ApiResponse.ok(null);
    }
}
