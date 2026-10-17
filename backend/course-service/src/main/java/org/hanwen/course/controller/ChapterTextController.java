package org.hanwen.course.controller;

import org.hanwen.common.*;
import org.hanwen.course.service.CourseService;
import org.springframework.web.bind.annotation.*;

@RestController
public class ChapterTextController {
    final CourseService courseService;

    public ChapterTextController(CourseService x) {
        courseService = x;
    }

    @GetMapping("/internal/courses/{id}/chapters/{chapterId}/text")
    public Contracts.ChapterText text(@PathVariable long id, @PathVariable long chapterId) {
        return courseService.chapterText(id, chapterId);
    }
}
