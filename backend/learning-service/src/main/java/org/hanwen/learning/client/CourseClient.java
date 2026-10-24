package org.hanwen.learning.client;

import org.hanwen.common.Contracts;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "hw-course-service", contextId = "learning-course")
public interface CourseClient {
    @GetMapping("/internal/quizzes/{id}/snapshot")
    Contracts.QuizSnapshot snapshot(@PathVariable long id);

    @GetMapping("/internal/courses/{id}/chapters/{chapterId}/text")
    Contracts.ChapterText text(@PathVariable long id, @PathVariable long chapterId);

    @GetMapping("/internal/courses/staff-ids")
    java.util.List<Long> staffIds(@RequestParam("teachOnly") boolean teachOnly);

    @GetMapping("/internal/courses/{id}/validation")
    Contracts.CourseValidation course(@PathVariable long id);

    @GetMapping("/internal/courses/{id}/chapters/{chapterId}/validation")
    Contracts.ChapterValidation chapter(@PathVariable long id, @PathVariable long chapterId);

    @GetMapping("/internal/instance")
    Map<String, Object> instance();

    @GetMapping("/internal/literature/briefs")
    java.util.List<Contracts.WorkBrief> works(@RequestParam("ids") String ids);

    @GetMapping("/internal/literature/{id}/validation")
    Contracts.WorkBrief work(@PathVariable long id);
}
