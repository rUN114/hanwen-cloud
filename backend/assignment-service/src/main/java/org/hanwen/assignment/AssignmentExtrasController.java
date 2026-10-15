package org.hanwen.assignment;

import org.hanwen.common.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class AssignmentExtrasController {
    final AssignmentExtrasService s;

    public AssignmentExtrasController(AssignmentExtrasService x) {
        s = x;
    }

    @GetMapping("/api/assignments/{id}/draft")
    public Object read(@PathVariable long id) {
        return ApiResponse.ok(s.read(id));
    }

    @PutMapping("/api/assignments/{id}/draft")
    public Object save(@PathVariable long id, @RequestBody AssignmentExtrasService.Write in) {
        return ApiResponse.ok(s.save(id, in));
    }

    @GetMapping("/api/submissions/{id}/grade-history")
    public Object history(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(s.history(id, page, size));
    }
}
