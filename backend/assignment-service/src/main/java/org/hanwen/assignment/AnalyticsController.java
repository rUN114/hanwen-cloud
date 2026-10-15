package org.hanwen.assignment;

import org.hanwen.common.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class AnalyticsController {
    final AnalyticsService s;

    public AnalyticsController(AnalyticsService x) {
        s = x;
    }

    @GetMapping("/api/teacher/courses/{id}/assignment-stats")
    public Object course(@PathVariable long id) {
        return ApiResponse.ok(s.course(id));
    }

    @GetMapping("/api/teacher/assignments/summary")
    public Object overview() {
        return ApiResponse.ok(s.overview());
    }

    @GetMapping("/api/assignments/pending")
    public Object pending(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int size) {
        return ApiResponse.ok(s.pending(page, size));
    }
}
