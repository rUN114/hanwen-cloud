package org.hanwen.learning;

import org.hanwen.common.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class LearningController {
    final LearningService s;

    public LearningController(LearningService service) {
        s = service;
    }

    @PostMapping("/api/enrollments")
    public Object join(@RequestBody LearningService.Join in) {
        return ApiResponse.ok(s.join(in));
    }

    @GetMapping("/api/enrollments/me")
    public Object list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long courseId) {
        return ApiResponse.ok(s.list(page, size, courseId));
    }

    @GetMapping("/api/enrollments/{id}")
    public Object detail(@PathVariable long id) {
        return ApiResponse.ok(s.detail(id));
    }

    @PutMapping("/api/enrollments/{id}/chapters/{chapterId}/complete")
    public Object complete(@PathVariable long id, @PathVariable long chapterId) {
        return ApiResponse.ok(s.complete(id, chapterId));
    }

    @GetMapping("/api/enrollments/{id}/progress")
    public Object progress(@PathVariable long id) {
        return ApiResponse.ok(s.summary(id));
    }

    @GetMapping("/internal/enrollments/exists")
    public Contracts.Exists exists(@RequestParam long courseId, @RequestParam long studentId) {
        return s.exists(courseId, studentId);
    }

    @GetMapping("/internal/enrollments/students/{id}/has-data")
    public Contracts.HasData has(@PathVariable long id) {
        return s.has(id);
    }

    @GetMapping("/api/teacher/diagnostics/course-instance")
    public Object instance() {
        return ApiResponse.ok(s.diagnostic());
    }
}
