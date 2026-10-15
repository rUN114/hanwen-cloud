package org.hanwen.assignment;
import org.hanwen.common.*;import org.springframework.web.bind.annotation.*;import org.springframework.web.multipart.MultipartFile;import org.springframework.http.*;import org.springframework.core.io.FileSystemResource;import java.nio.charset.StandardCharsets;import java.io.*;
@RestController public class AttachmentController {
 final AttachmentService s;public AttachmentController(AttachmentService x){s=x;}
 @GetMapping("/api/assignments/{id}/attachments")public Object list(@PathVariable long id,@RequestParam(required=false)Long submissionId){return ApiResponse.ok(s.list(id,submissionId));}
 @PostMapping(value="/api/assignments/{id}/attachments",consumes="multipart/form-data")public Object upload(@PathVariable long id,@RequestPart MultipartFile file)throws IOException{return ApiResponse.ok(s.upload(id,file));}
 @GetMapping("/api/submissions/attachments/{id}/file")public ResponseEntity<FileSystemResource> file(@PathVariable long id){var f=s.readable(id);return ResponseEntity.ok().header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(f.originalName,StandardCharsets.UTF_8).build().toString()).contentType(MediaType.parseMediaType(f.mimeType)).contentLength(f.bytes).body(new FileSystemResource(s.file(f)));}
 @DeleteMapping("/api/submissions/attachments/{id}")public Object remove(@PathVariable long id){s.remove(id);return ApiResponse.ok(null);}
}
