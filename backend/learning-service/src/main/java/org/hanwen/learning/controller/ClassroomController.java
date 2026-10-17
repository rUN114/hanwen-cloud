package org.hanwen.learning.controller;

import org.hanwen.common.*;
import org.hanwen.learning.service.ClassroomService;
import org.springframework.web.bind.annotation.*;

@RestController
public class ClassroomController {
    final ClassroomService classroomService;

    public ClassroomController(ClassroomService x) {
        classroomService = x;
    }

    @GetMapping("/api/classes")
    public Object student(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "9") int size,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(classroomService.list(page, size, false, semester, status));
    }

    @GetMapping("/api/teacher/classes")
    public Object teacher(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "9") int size,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(classroomService.list(page, size, true, semester, status));
    }

    @GetMapping("/api/classes/{id}")
    public Object detail(@PathVariable long id) {
        return ApiResponse.ok(classroomService.detail(id));
    }

    @PostMapping("/api/teacher/classes")
    public Object create(@RequestBody ClassroomService.Create in) {
        return ApiResponse.ok(classroomService.create(in));
    }

    @PutMapping("/api/teacher/classes/{id}")
    public Object update(@PathVariable long id, @RequestBody ClassroomService.Write in) {
        return ApiResponse.ok(classroomService.update(id, in));
    }

    @PostMapping("/api/teacher/classes/{id}/rotate-code")
    public Object rotate(@PathVariable long id, @RequestParam int expectedVersion) {
        return ApiResponse.ok(classroomService.rotate(id, expectedVersion));
    }

    @GetMapping("/api/teacher/classes/{id}/members")
    public Object members(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(classroomService.roster(id, page, size));
    }

    @PostMapping("/api/teacher/classes/{id}/members")
    public Object add(@PathVariable long id, @RequestBody ClassroomService.Import in) {
        return ApiResponse.ok(classroomService.importMembers(id, in));
    }

    @DeleteMapping("/api/teacher/classes/{id}/members/{memberId}")
    public Object remove(@PathVariable long id, @PathVariable long memberId) {
        classroomService.remove(id, memberId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/classes/join")
    public Object join(@RequestBody ClassroomService.Join in) {
        return ApiResponse.ok(classroomService.join(in));
    }
}
