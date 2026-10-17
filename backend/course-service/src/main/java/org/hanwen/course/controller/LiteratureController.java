package org.hanwen.course.controller;

import org.hanwen.common.*;
import org.hanwen.course.service.LiteratureService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class LiteratureController {
    final LiteratureService literatureService;

    public LiteratureController(LiteratureService x) {
        literatureService = x;
    }

    @GetMapping("/api/literature")
    public Object list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "9") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String dynasty,
            @RequestParam(required = false) String genre) {
        return ApiResponse.ok(literatureService.list(page, size, keyword, dynasty, genre, false));
    }

    @GetMapping("/api/teacher/literature")
    public Object mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "9") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(literatureService.list(page, size, keyword, null, null, true));
    }

    @GetMapping("/api/literature/{id}")
    public Object detail(@PathVariable long id) {
        return ApiResponse.ok(literatureService.detail(id));
    }

    @PostMapping("/api/teacher/literature")
    public Object create(@RequestBody LiteratureService.Write in) {
        return ApiResponse.ok(literatureService.write(null, in));
    }

    @PutMapping("/api/teacher/literature/{id}")
    public Object update(@PathVariable long id, @RequestBody LiteratureService.Write in) {
        return ApiResponse.ok(literatureService.write(id, in));
    }

    @PutMapping("/api/teacher/literature/{id}/status")
    public Object state(@PathVariable long id, @RequestBody LiteratureService.State in) {
        return ApiResponse.ok(literatureService.state(id, in));
    }

    @GetMapping("/internal/literature/briefs")
    public List<Contracts.WorkBrief> briefs(@RequestParam String ids) {
        return literatureService.briefs(ids);
    }

    @GetMapping("/internal/literature/{id}/validation")
    public Contracts.WorkBrief validation(@PathVariable long id) {
        return literatureService.validation(id);
    }
}
