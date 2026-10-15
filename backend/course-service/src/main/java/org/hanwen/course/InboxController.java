package org.hanwen.course;

import org.hanwen.common.*;
import org.hanwen.course.mapper.InboxMapper;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class InboxController {
    final InboxMapper inbox;
    final LearningClient learning;
    final Remote remote;

    public InboxController(InboxMapper i, LearningClient l, Remote r) {
        inbox = i;
        learning = l;
        remote = r;
    }

    @GetMapping("/api/courses/inbox")
    public Object feed() {
        var a = Actor.current();
        boolean teacher = a.role().equals("TEACHER");
        if (!teacher && !a.role().equals("STUDENT")) throw ApiException.forbidden();
        List<List<Map<String, Object>>> groups = new ArrayList<>();
        if (teacher) groups.add(inbox.questions(a.id()));
        else {
            var ids = remote.call("inbox-enrollments", () -> learning.activeCourses());
            if (!ids.isEmpty()) groups.add(inbox.notices(ids));
        }
        groups.add(inbox.replies(a.id(), teacher));
        boolean more = groups.stream().anyMatch(g -> g.size() > 100);
        var rows =
                groups.stream()
                        .flatMap(g -> g.stream().limit(100))
                        .sorted(
                                Comparator.comparing(
                                                (Map<String, Object> m) ->
                                                        String.valueOf(m.get("eventAt")))
                                        .reversed())
                        .toList();
        return ApiResponse.ok(Views.map("items", rows, "hasMore", more));
    }
}
