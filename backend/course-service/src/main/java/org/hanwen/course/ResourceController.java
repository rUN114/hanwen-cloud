package org.hanwen.course;

import org.hanwen.common.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
public class ResourceController {
    final ResourceService s;

    public ResourceController(ResourceService x) {
        s = x;
    }

    @GetMapping("/api/courses/{id}/resources")
    public Object list(
            @PathVariable long id,
            @RequestParam(required = false) Long chapterId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.ok(s.list(id, chapterId, page, size));
    }

    @PostMapping(value = "/api/teacher/courses/{id}/resources", consumes = "multipart/form-data")
    public Object upload(
            @PathVariable long id,
            @RequestParam(required = false) Long chapterId,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestPart MultipartFile file)
            throws IOException {
        return ApiResponse.ok(s.upload(id, chapterId, title, description, file));
    }

    @GetMapping("/api/resources/{id}/file")
    public ResponseEntity<FileSystemResource> file(@PathVariable long id) {
        var f = s.readable(id);
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
                .body(new FileSystemResource(s.file(f)));
    }

    @DeleteMapping("/api/resources/{id}")
    public Object remove(@PathVariable long id) {
        s.remove(id);
        return ApiResponse.ok(null);
    }
}
