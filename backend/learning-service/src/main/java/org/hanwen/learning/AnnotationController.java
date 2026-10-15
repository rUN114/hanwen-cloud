package org.hanwen.learning;

import org.hanwen.common.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class AnnotationController {
    final AnnotationService s;

    public AnnotationController(AnnotationService x) {
        s = x;
    }

    @GetMapping("/api/annotations")
    public Object list(
            @RequestParam long enrollmentId,
            @RequestParam long chapterId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(s.list(enrollmentId, chapterId, page, size));
    }

    @PostMapping("/api/annotations")
    public Object create(@RequestBody AnnotationService.Write in) {
        return ApiResponse.ok(s.create(in));
    }

    @DeleteMapping("/api/annotations/{id}")
    public Object delete(@PathVariable long id) {
        s.delete(id);
        return ApiResponse.ok(null);
    }
}
