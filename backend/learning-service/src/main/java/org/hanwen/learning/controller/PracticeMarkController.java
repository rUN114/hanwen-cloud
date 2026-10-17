package org.hanwen.learning.controller;

import org.hanwen.common.*;
import org.hanwen.learning.service.PracticeMarkService;
import org.springframework.web.bind.annotation.*;

@RestController
public class PracticeMarkController {
    final PracticeMarkService service;

    public PracticeMarkController(PracticeMarkService s) {
        service = s;
    }

    @GetMapping("/api/teacher/practice-attempts/pending")
    public Object pending(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int size) {
        return ApiResponse.ok(service.pending(page, size));
    }

    @GetMapping("/api/teacher/practice-attempts/{id}")
    public Object detail(@PathVariable long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PutMapping("/api/teacher/practice-attempts/{id}/grade")
    public Object mark(@PathVariable long id, @RequestBody PracticeMarkService.Mark in) {
        return ApiResponse.ok(service.mark(id, in));
    }
}
