package org.hanwen.course.client;

import org.hanwen.common.Contracts;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "hw-learning-service", contextId = "course-enrollment")
public interface LearningClient {
    @GetMapping("/internal/enrollments/me/active-course-ids")
    java.util.List<Long> activeCourses();

    @GetMapping("/internal/enrollments/exists")
    Contracts.Exists exists(
            @RequestParam("courseId") long courseId, @RequestParam("studentId") long studentId);
}
