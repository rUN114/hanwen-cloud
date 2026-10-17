package org.hanwen.learning.controller;

import org.hanwen.common.*;
import org.hanwen.learning.service.InsightsService;
import org.springframework.web.bind.annotation.*;

@RestController
public class InsightsController {
    final InsightsService service;

    public InsightsController(InsightsService s) {
        service = s;
    }

    @GetMapping("/api/teacher/courses/{id}/learning-insights")
    public Object course(@PathVariable long id, @RequestParam(required = false) Long classId) {
        return ApiResponse.ok(service.course(id, classId));
    }
}
