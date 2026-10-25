package org.hanwen.course.controller;

import org.hanwen.common.*;
import org.hanwen.course.service.QuizService;
import org.springframework.web.bind.annotation.*;

@RestController
public class QuizController {
    final QuizService quizService;

    public QuizController(QuizService x) {
        quizService = x;
    }

    @GetMapping("/api/courses/{id}/quizzes")
    public Object list(
            @PathVariable long id,
            @RequestParam(required = false) Long chapterId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.ok(quizService.list(id, chapterId, page, size));
    }

    @GetMapping("/api/quizzes/{id}")
    public Object detail(@PathVariable long id) {
        return ApiResponse.ok(quizService.detail(id));
    }

    @PostMapping("/api/teacher/courses/{id}/quizzes")
    public Object create(@PathVariable long id, @RequestBody QuizService.Write in) {
        return ApiResponse.ok(quizService.write(id, null, in));
    }

    @PutMapping("/api/teacher/courses/{courseId}/quizzes/{id}")
    public Object update(
            @PathVariable long courseId, @PathVariable long id, @RequestBody QuizService.Write in) {
        return ApiResponse.ok(quizService.write(courseId, id, in));
    }

    @PostMapping("/api/teacher/quizzes/{id}/questions")
    public Object add(@PathVariable long id, @RequestBody QuizService.QuestionWrite in) {
        return ApiResponse.ok(quizService.question(id, null, in));
    }

    @PutMapping("/api/teacher/quizzes/{id}/questions/{questionId}")
    public Object edit(
            @PathVariable long id,
            @PathVariable long questionId,
            @RequestBody QuizService.QuestionWrite in) {
        return ApiResponse.ok(quizService.question(id, questionId, in));
    }

    @DeleteMapping("/api/teacher/quizzes/{id}/questions/{questionId}")
    public Object delete(
            @PathVariable long id,
            @PathVariable long questionId,
            @RequestParam int expectedVersion) {
        quizService.deleteQuestion(id, questionId, expectedVersion);
        return ApiResponse.ok(null);
    }

    @PutMapping("/api/teacher/quizzes/{id}/status")
    public Object state(@PathVariable long id, @RequestBody QuizService.State in) {
        return ApiResponse.ok(quizService.state(id, in));
    }

    @GetMapping("/internal/quizzes/releases")
    public java.util.List<Contracts.QuizRelease> releases(@RequestParam java.util.List<Long> ids) {
        return quizService.releases(ids);
    }

    @GetMapping("/internal/quizzes/{id}/snapshot")
    public Contracts.QuizSnapshot snapshot(@PathVariable long id) {
        return quizService.snapshot(id);
    }

    @PostMapping("/api/teacher/quizzes/{id}/from-bank")
    public Object fromBank(@PathVariable long id, @RequestBody QuizService.ImportQuestion in) {
        return ApiResponse.ok(quizService.importQuestion(id, in));
    }
}
