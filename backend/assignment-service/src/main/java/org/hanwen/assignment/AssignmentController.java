package org.hanwen.assignment;

import org.hanwen.common.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class AssignmentController {
    final AssignmentService s;

    public AssignmentController(AssignmentService service) {
        s = service;
    }

    @GetMapping("/api/courses/{id}/assignments")
    public Object list(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(s.list(id, page, size));
    }

    @GetMapping("/api/assignments/{id}")
    public Object task(@PathVariable long id) {
        return ApiResponse.ok(s.detail(id));
    }

    @PostMapping("/api/teacher/assignments")
    public Object create(@RequestBody AssignmentService.Create in) {
        return ApiResponse.ok(s.create(in));
    }

    @PutMapping("/api/teacher/assignments/{id}")
    public Object edit(@PathVariable long id, @RequestBody AssignmentService.Write in) {
        return ApiResponse.ok(s.update(id, in));
    }

    @PostMapping("/api/teacher/assignments/{id}/publish")
    public Object publish(@PathVariable long id) {
        return ApiResponse.ok(s.transition(id, true));
    }

    @PostMapping("/api/teacher/assignments/{id}/close")
    public Object close(@PathVariable long id) {
        return ApiResponse.ok(s.transition(id, false));
    }

    @PostMapping("/api/assignments/{id}/submissions")
    public Object submit(@PathVariable long id, @RequestBody AssignmentService.Answer in) {
        return ApiResponse.ok(s.submit(id, in));
    }

    @PutMapping("/api/submissions/{id}")
    public Object modify(@PathVariable long id, @RequestBody AssignmentService.Modify in) {
        return ApiResponse.ok(s.modify(id, in));
    }

    @GetMapping("/api/assignments/{id}/submissions")
    public Object submissions(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(s.submissions(id, page, size, status));
    }

    @GetMapping("/api/submissions/{id}")
    public Object detail(@PathVariable long id) {
        return ApiResponse.ok(s.submissionDetail(id));
    }

    @PutMapping("/api/submissions/{id}/grade")
    public Object mark(@PathVariable long id, @RequestBody AssignmentService.Mark in) {
        return ApiResponse.ok(s.mark(id, in));
    }

    @GetMapping("/api/grades/me")
    public Object mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long assignmentId) {
        return ApiResponse.ok(s.mine(page, size, courseId, assignmentId));
    }

    @GetMapping("/internal/submissions/students/{id}/has-data")
    public Contracts.HasData has(@PathVariable long id) {
        return s.has(id);
    }
}
