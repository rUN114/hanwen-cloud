package org.hanwen.learning.controller;

import org.hanwen.common.*;
import org.hanwen.learning.service.AnnotationService;
import org.springframework.web.bind.annotation.*;

@RestController
public class AnnotationController {
    final AnnotationService annotationService;

    public AnnotationController(AnnotationService x) {
        annotationService = x;
    }

    @GetMapping("/api/annotations")
    public Object list(
            @RequestParam long enrollmentId,
            @RequestParam long chapterId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(annotationService.list(enrollmentId, chapterId, page, size));
    }

    @PostMapping("/api/annotations")
    public Object create(@RequestBody AnnotationService.Write in) {
        return ApiResponse.ok(annotationService.create(in));
    }

    @DeleteMapping("/api/annotations/{id}")
    public Object delete(@PathVariable long id) {
        annotationService.delete(id);
        return ApiResponse.ok(null);
    }
}
