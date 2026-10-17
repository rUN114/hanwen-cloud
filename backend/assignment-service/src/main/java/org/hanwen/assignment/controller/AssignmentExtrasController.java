package org.hanwen.assignment.controller;

import org.hanwen.assignment.service.AssignmentExtrasService;
import org.hanwen.common.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class AssignmentExtrasController {
    final AssignmentExtrasService assignmentExtrasService;

    public AssignmentExtrasController(AssignmentExtrasService x) {
        assignmentExtrasService = x;
    }

    @GetMapping("/api/assignments/{id}/draft")
    public Object read(@PathVariable long id) {
        return ApiResponse.ok(assignmentExtrasService.read(id));
    }

    @PutMapping("/api/assignments/{id}/draft")
    public Object save(@PathVariable long id, @RequestBody AssignmentExtrasService.Write in) {
        return ApiResponse.ok(assignmentExtrasService.save(id, in));
    }

    @GetMapping("/api/submissions/{id}/grade-history")
    public Object history(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(assignmentExtrasService.history(id, page, size));
    }
}
