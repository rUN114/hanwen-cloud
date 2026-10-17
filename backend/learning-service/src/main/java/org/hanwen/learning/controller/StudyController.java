package org.hanwen.learning.controller;

import org.hanwen.common.*;
import org.hanwen.learning.service.StudyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class StudyController {
    final StudyService studyService;

    public StudyController(StudyService x) {
        studyService = x;
    }

    @GetMapping("/api/notes")
    public Object notes(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long enrollmentId,
            @RequestParam(required = false) Long chapterId) {
        return ApiResponse.ok(studyService.notes(page, size, keyword, enrollmentId, chapterId));
    }

    @PostMapping("/api/notes")
    public Object create(@RequestBody StudyService.NoteWrite in) {
        return ApiResponse.ok(studyService.note(null, in));
    }

    @PutMapping("/api/notes/{id}")
    public Object update(@PathVariable long id, @RequestBody StudyService.NoteWrite in) {
        return ApiResponse.ok(studyService.note(id, in));
    }

    @DeleteMapping("/api/notes/{id}")
    public Object delete(@PathVariable long id, @RequestParam int expectedVersion) {
        studyService.delete(id, expectedVersion);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/favorites")
    public Object favorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "9") int size) {
        return ApiResponse.ok(studyService.favorites(page, size));
    }

    @GetMapping("/api/favorites/{workId}")
    public Object state(@PathVariable long workId) {
        return ApiResponse.ok(studyService.favoriteState(workId));
    }

    @PostMapping("/api/favorites")
    public Object favorite(@RequestBody StudyService.FavoriteWrite in) {
        return ApiResponse.ok(studyService.favorite(in));
    }

    @DeleteMapping("/api/favorites/{workId}")
    public Object unfavorite(@PathVariable long workId) {
        studyService.unfavorite(workId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/enrollments/summary")
    public Object overview() {
        return ApiResponse.ok(studyService.overview());
    }

    @GetMapping("/api/teacher/courses/{id}/learners")
    public Object roster(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(studyService.roster(id, page, size));
    }

    @GetMapping("/internal/enrollments/me/active-course-ids")
    public List<Long> activeCourses() {
        return studyService.activeCourses();
    }
}
