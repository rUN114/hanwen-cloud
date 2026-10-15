package org.hanwen.course;

import org.hanwen.common.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class LiteratureController {
    final LiteratureService s;

    public LiteratureController(LiteratureService x) {
        s = x;
    }

    @GetMapping("/api/literature")
    public Object list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "9") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String dynasty,
            @RequestParam(required = false) String genre) {
        return ApiResponse.ok(s.list(page, size, keyword, dynasty, genre, false));
    }

    @GetMapping("/api/teacher/literature")
    public Object mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "9") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(s.list(page, size, keyword, null, null, true));
    }

    @GetMapping("/api/literature/{id}")
    public Object detail(@PathVariable long id) {
        return ApiResponse.ok(s.detail(id));
    }

    @PostMapping("/api/teacher/literature")
    public Object create(@RequestBody LiteratureService.Write in) {
        return ApiResponse.ok(s.write(null, in));
    }

    @PutMapping("/api/teacher/literature/{id}")
    public Object update(@PathVariable long id, @RequestBody LiteratureService.Write in) {
        return ApiResponse.ok(s.write(id, in));
    }

    @PutMapping("/api/teacher/literature/{id}/status")
    public Object state(@PathVariable long id, @RequestBody LiteratureService.State in) {
        return ApiResponse.ok(s.state(id, in));
    }

    @GetMapping("/internal/literature/briefs")
    public List<Contracts.WorkBrief> briefs(@RequestParam String ids) {
        return s.briefs(ids);
    }

    @GetMapping("/internal/literature/{id}/validation")
    public Contracts.WorkBrief validation(@PathVariable long id) {
        return s.validation(id);
    }
}
