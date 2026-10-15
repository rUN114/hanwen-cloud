package org.hanwen.learning;

import org.hanwen.common.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class PracticeController {
    final PracticeService s;

    public PracticeController(PracticeService x) {
        s = x;
    }

    @GetMapping("/api/practices/{id}/result")
    public Object result(@PathVariable long id, @RequestParam(required=false) Long attemptId) {
        return ApiResponse.ok(s.result(id, attemptId));
    }

    @GetMapping("/api/practices/{id}/state")
    public Object state(@PathVariable long id) { return ApiResponse.ok(s.state(id)); }

    @PostMapping("/api/practices/{id}/start")
    public Object start(@PathVariable long id) { return ApiResponse.ok(s.start(id)); }

    @PutMapping("/api/practices/{id}/runs/{runId}/draft")
    public Object draft(@PathVariable long id, @PathVariable long runId, @RequestBody PracticeService.Draft in) {
        return ApiResponse.ok(s.draft(id,runId,in));
    }

    @PostMapping("/api/practices/{id}/attempts")
    public Object submit(@PathVariable long id, @RequestBody Contracts.QuizAnswers in) {
        return ApiResponse.ok(s.submit(id, in));
    }

    @GetMapping("/api/teacher/courses/{id}/practice-results")
    public Object results(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(s.courseResults(id, page, size));
    }
}
