package org.hanwen.course.controller;

import org.hanwen.common.*;
import org.hanwen.course.service.CourseCopyService;
import org.hanwen.course.service.CourseService;
import org.springframework.web.bind.annotation.*;

@RestController
public class CourseController {
    final CourseService courseService;
    final CourseCopyService copier;

    public CourseController(CourseService service, CourseCopyService copier) {
        courseService = service;
        this.copier = copier;
    }

    @PostMapping("/api/teacher/courses/{id}/copy")
    public Object copy(@PathVariable long id, @RequestBody CourseCopyService.Copy in) {
        return ApiResponse.ok(copier.copy(id, in));
    }

    @GetMapping("/api/courses")
    public Object list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        return ApiResponse.ok(courseService.list(page, size, keyword, category, null, false));
    }

    @GetMapping("/api/teacher/courses")
    public Object mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(courseService.list(page, size, null, null, status, true));
    }

    @PostMapping("/api/teacher/courses")
    public Object create(@RequestBody CourseService.CourseWrite in) {
        return ApiResponse.ok(courseService.create(in));
    }

    @PutMapping("/api/teacher/courses/{id}")
    public Object update(@PathVariable long id, @RequestBody CourseService.CourseWrite in) {
        return ApiResponse.ok(courseService.update(id, in));
    }

    @PostMapping("/api/teacher/courses/{id}/publish")
    public Object publish(@PathVariable long id) {
        return ApiResponse.ok(courseService.transition(id, true));
    }

    @PostMapping("/api/teacher/courses/{id}/archive")
    public Object archive(@PathVariable long id) {
        return ApiResponse.ok(courseService.transition(id, false));
    }

    @GetMapping("/api/courses/{id}")
    public Object detail(@PathVariable long id) {
        return ApiResponse.ok(courseService.detail(id));
    }

    @GetMapping("/api/courses/{id}/chapters")
    public Object outline(@PathVariable long id) {
        return ApiResponse.ok(courseService.outline(id));
    }

    @GetMapping("/api/courses/{id}/chapters/{chapterId}")
    public Object read(@PathVariable long id, @PathVariable long chapterId) {
        return ApiResponse.ok(courseService.read(id, chapterId));
    }

    @PostMapping("/api/teacher/courses/{id}/chapters")
    public Object add(@PathVariable long id, @RequestBody CourseService.ChapterWrite in) {
        return ApiResponse.ok(courseService.writeChapter(id, null, in));
    }

    @PutMapping("/api/teacher/courses/{id}/chapters/{chapterId}")
    public Object edit(
            @PathVariable long id,
            @PathVariable long chapterId,
            @RequestBody CourseService.ChapterWrite in) {
        return ApiResponse.ok(courseService.writeChapter(id, chapterId, in));
    }

    @DeleteMapping("/api/teacher/courses/{id}/chapters/{chapterId}")
    public Object delete(@PathVariable long id, @PathVariable long chapterId) {
        courseService.deleteChapter(id, chapterId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/internal/courses/staff-ids")
    public java.util.List<Long> staffIds(@RequestParam(defaultValue = "false") boolean teachOnly) {
        return courseService.staffCourseIds(teachOnly);
    }

    @GetMapping("/internal/courses/{id}/validation")
    public Contracts.CourseValidation validation(@PathVariable long id) {
        return courseService.validation(id);
    }

    @GetMapping("/internal/courses/{id}/chapters/{chapterId}/validation")
    public Contracts.ChapterValidation chapter(
            @PathVariable long id, @PathVariable long chapterId) {
        return courseService.validateChapter(id, chapterId);
    }

    @GetMapping("/internal/courses/teachers/{id}/has-data")
    public Contracts.HasData has(@PathVariable long id) {
        return courseService.has(id);
    }
}
