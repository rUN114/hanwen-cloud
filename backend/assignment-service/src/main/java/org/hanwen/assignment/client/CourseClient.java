package org.hanwen.assignment.client;

import org.hanwen.common.Contracts;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "hw-course-service", contextId = "assignment-course")
public interface CourseClient {
    @GetMapping("/internal/courses/staff-ids")
    java.util.List<Long> staffIds(@RequestParam("teachOnly") boolean teachOnly);

    @GetMapping("/internal/courses/{id}/validation")
    Contracts.CourseValidation course(@PathVariable long id);

    @GetMapping("/internal/courses/{id}/chapters/{chapterId}/validation")
    Contracts.ChapterValidation chapter(@PathVariable long id, @PathVariable long chapterId);

    @GetMapping("/internal/courses/{id}/grade-quizzes")
    java.util.List<Contracts.QuizBrief> quizzes(@PathVariable long id);

    @GetMapping("/internal/instance")
    Map<String, Object> instance();
}
