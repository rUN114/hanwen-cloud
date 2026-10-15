package org.hanwen.course;

import org.hanwen.common.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class QuizController {
    final QuizService s;

    public QuizController(QuizService x) {
        s = x;
    }

    @GetMapping("/api/courses/{id}/quizzes")
    public Object list(
            @PathVariable long id,
            @RequestParam(required = false) Long chapterId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.ok(s.list(id, chapterId, page, size));
    }

    @GetMapping("/api/quizzes/{id}")
    public Object detail(@PathVariable long id) {
        return ApiResponse.ok(s.detail(id));
    }

    @PostMapping("/api/teacher/courses/{id}/quizzes")
    public Object create(@PathVariable long id, @RequestBody QuizService.Write in) {
        return ApiResponse.ok(s.write(id, null, in));
    }

    @PutMapping("/api/teacher/courses/{courseId}/quizzes/{id}")
    public Object update(
            @PathVariable long courseId, @PathVariable long id, @RequestBody QuizService.Write in) {
        return ApiResponse.ok(s.write(courseId, id, in));
    }

    @PostMapping("/api/teacher/quizzes/{id}/questions")
    public Object add(@PathVariable long id, @RequestBody QuizService.QuestionWrite in) {
        return ApiResponse.ok(s.question(id, null, in));
    }

    @PutMapping("/api/teacher/quizzes/{id}/questions/{questionId}")
    public Object edit(
            @PathVariable long id,
            @PathVariable long questionId,
            @RequestBody QuizService.QuestionWrite in) {
        return ApiResponse.ok(s.question(id, questionId, in));
    }

    @DeleteMapping("/api/teacher/quizzes/{id}/questions/{questionId}")
    public Object delete(
            @PathVariable long id,
            @PathVariable long questionId,
            @RequestParam int expectedVersion) {
        s.deleteQuestion(id, questionId, expectedVersion);
        return ApiResponse.ok(null);
    }

    @PutMapping("/api/teacher/quizzes/{id}/status")
    public Object state(@PathVariable long id, @RequestBody QuizService.State in) {
        return ApiResponse.ok(s.state(id, in));
    }

    @PostMapping("/internal/quizzes/{id}/grade")
    public Contracts.QuizResult grade(
            @PathVariable long id, @RequestBody Contracts.QuizAnswers in) {
        return s.grade(id, in);
    }

    @GetMapping("/internal/quizzes/{id}/snapshot")
    public Contracts.QuizSnapshot snapshot(@PathVariable long id) { return s.snapshot(id); }

    @PostMapping("/api/teacher/quizzes/{id}/from-bank")
    public Object fromBank(@PathVariable long id, @RequestBody QuizService.ImportQuestion in) {
        return ApiResponse.ok(s.importQuestion(id, in));
    }
}
