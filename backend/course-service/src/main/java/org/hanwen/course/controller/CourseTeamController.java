package org.hanwen.course.controller;

import org.hanwen.common.*;
import org.hanwen.course.service.CourseTeamService;
import org.springframework.web.bind.annotation.*;

@RestController
public class CourseTeamController {
    final CourseTeamService service;

    public CourseTeamController(CourseTeamService s) {
        service = s;
    }

    @GetMapping("/api/teacher/courses/{id}/team")
    public Object list(@PathVariable long id) {
        return ApiResponse.ok(service.list(id));
    }

    @PutMapping("/api/teacher/courses/{id}/team")
    public Object save(@PathVariable long id, @RequestBody CourseTeamService.MemberWrite in) {
        return ApiResponse.ok(service.save(id, in));
    }

    @PostMapping("/api/teacher/courses/{id}/team/{user}/remove")
    public Object remove(
            @PathVariable long id,
            @PathVariable long user,
            @RequestBody CourseTeamService.Remove in) {
        return ApiResponse.ok(service.remove(id, user, in));
    }

    @PostMapping("/api/teacher/courses/{id}/transfer")
    public Object transfer(@PathVariable long id, @RequestBody CourseTeamService.Transfer in) {
        return ApiResponse.ok(service.transfer(id, in));
    }
}
