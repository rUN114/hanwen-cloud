package org.hanwen.assignment.controller;

import org.hanwen.assignment.service.AnalyticsService;
import org.hanwen.common.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class AnalyticsController {
    final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService x) {
        analyticsService = x;
    }

    @GetMapping("/api/teacher/courses/{id}/assignment-stats")
    public Object course(@PathVariable long id) {
        return ApiResponse.ok(analyticsService.course(id));
    }

    @GetMapping("/api/teacher/assignments/summary")
    public Object overview() {
        return ApiResponse.ok(analyticsService.overview());
    }

    @GetMapping("/api/assignments/pending")
    public Object pending(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int size) {
        return ApiResponse.ok(analyticsService.pending(page, size));
    }
}
