package org.hanwen.user;

import org.hanwen.common.Contracts;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

public final class RelatedDataClient {
    @FeignClient(name = "hw-course-service", contextId = "user-course-data")
    public interface Courses {
        @GetMapping("/internal/courses/teachers/{id}/has-data")
        Contracts.HasData has(@PathVariable("id") long id);
    }

    @FeignClient(name = "hw-learning-service", contextId = "user-learning-data")
    public interface Learning {
        @GetMapping("/internal/enrollments/students/{id}/has-data")
        Contracts.HasData has(@PathVariable("id") long id);
    }

    @FeignClient(name = "hw-assignment-service", contextId = "user-assignment-data")
    public interface Assignments {
        @GetMapping("/internal/submissions/students/{id}/has-data")
        Contracts.HasData has(@PathVariable("id") long id);
    }
}
