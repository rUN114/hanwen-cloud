package org.hanwen.learning.controller;

import org.hanwen.common.*;
import org.hanwen.learning.service.PracticeService;
import org.springframework.web.bind.annotation.*;

@RestController
public class PracticeController {
    final PracticeService practiceService;

    public PracticeController(PracticeService x) {
        practiceService = x;
    }

    @GetMapping("/api/practices/{id}/result")
    public Object result(@PathVariable long id, @RequestParam(required = false) Long attemptId) {
        return ApiResponse.ok(practiceService.result(id, attemptId));
    }

    @GetMapping("/api/practices/{id}/state")
    public Object state(@PathVariable long id) {
        return ApiResponse.ok(practiceService.state(id));
    }

    @PostMapping("/api/practices/{id}/start")
    public Object start(@PathVariable long id) {
        return ApiResponse.ok(practiceService.start(id));
    }

    @PutMapping("/api/practices/{id}/runs/{runId}/draft")
    public Object draft(
            @PathVariable long id,
            @PathVariable long runId,
            @RequestBody PracticeService.Draft in) {
        return ApiResponse.ok(practiceService.draft(id, runId, in));
    }

    @PostMapping("/api/practices/{id}/attempts")
    public Object submit(@PathVariable long id, @RequestBody Contracts.QuizAnswers in) {
        return ApiResponse.ok(practiceService.submit(id, in));
    }

    @GetMapping("/api/teacher/courses/{id}/practice-results")
    public Object results(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(practiceService.courseResults(id, page, size));
    }
}
