package org.hanwen.learning.controller;

import org.hanwen.common.*;
import org.hanwen.learning.service.ReviewService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/review-cards")
public class ReviewController {
    final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public Object list(
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "ALL") String state,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(reviewService.list(courseId, state, page, size));
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable long id) {
        return ApiResponse.ok(reviewService.detail(id));
    }

    @PostMapping("/{id}/answers")
    public Object answer(@PathVariable long id, @RequestBody ReviewService.Answer in) {
        return ApiResponse.ok(reviewService.answer(id, in));
    }
}
