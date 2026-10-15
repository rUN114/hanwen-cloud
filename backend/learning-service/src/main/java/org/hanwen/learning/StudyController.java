package org.hanwen.learning;

import org.hanwen.common.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class StudyController {
    final StudyService s;

    public StudyController(StudyService x) {
        s = x;
    }

    @GetMapping("/api/notes")
    public Object notes(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long enrollmentId,
            @RequestParam(required = false) Long chapterId) {
        return ApiResponse.ok(s.notes(page, size, keyword, enrollmentId, chapterId));
    }

    @PostMapping("/api/notes")
    public Object create(@RequestBody StudyService.NoteWrite in) {
        return ApiResponse.ok(s.note(null, in));
    }

    @PutMapping("/api/notes/{id}")
    public Object update(@PathVariable long id, @RequestBody StudyService.NoteWrite in) {
        return ApiResponse.ok(s.note(id, in));
    }

    @DeleteMapping("/api/notes/{id}")
    public Object delete(@PathVariable long id, @RequestParam int expectedVersion) {
        s.delete(id, expectedVersion);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/favorites")
    public Object favorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "9") int size) {
        return ApiResponse.ok(s.favorites(page, size));
    }

    @GetMapping("/api/favorites/{workId}")
    public Object state(@PathVariable long workId) {
        return ApiResponse.ok(s.favoriteState(workId));
    }

    @PostMapping("/api/favorites")
    public Object favorite(@RequestBody StudyService.FavoriteWrite in) {
        return ApiResponse.ok(s.favorite(in));
    }

    @DeleteMapping("/api/favorites/{workId}")
    public Object unfavorite(@PathVariable long workId) {
        s.unfavorite(workId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/enrollments/summary")
    public Object overview() {
        return ApiResponse.ok(s.overview());
    }

    @GetMapping("/api/teacher/courses/{id}/learners")
    public Object roster(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(s.roster(id, page, size));
    }

    @GetMapping("/internal/enrollments/me/active-course-ids")
    public List<Long> activeCourses() {
        return s.activeCourses();
    }
}
