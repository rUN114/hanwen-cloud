package org.hanwen.course;

import org.hanwen.common.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ChapterTextController {
    final CourseService s;

    public ChapterTextController(CourseService x) {
        s = x;
    }

    @GetMapping("/internal/courses/{id}/chapters/{chapterId}/text")
    public Contracts.ChapterText text(@PathVariable long id, @PathVariable long chapterId) {
        return s.chapterText(id, chapterId);
    }
}
