package org.hanwen.course.controller;

import org.hanwen.common.*;
import org.hanwen.course.service.CommunityService;
import org.springframework.web.bind.annotation.*;

@RestController
public class CommunityController {
    final CommunityService communityService;

    public CommunityController(CommunityService x) {
        communityService = x;
    }

    @GetMapping("/api/courses/{id}/notices")
    public Object notices(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(communityService.notices(id, page, size));
    }

    @PostMapping("/api/teacher/courses/{id}/notices")
    public Object create(@PathVariable long id, @RequestBody CommunityService.NoticeWrite in) {
        return ApiResponse.ok(communityService.notice(id, null, in));
    }

    @PutMapping("/api/teacher/courses/{id}/notices/{noticeId}")
    public Object update(
            @PathVariable long id,
            @PathVariable long noticeId,
            @RequestBody CommunityService.NoticeWrite in) {
        return ApiResponse.ok(communityService.notice(id, noticeId, in));
    }

    @DeleteMapping("/api/teacher/courses/{id}/notices/{noticeId}")
    public Object delete(
            @PathVariable long id, @PathVariable long noticeId, @RequestParam int expectedVersion) {
        communityService.deleteNotice(id, noticeId, expectedVersion);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/courses/{id}/questions")
    public Object questions(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean resolved) {
        return ApiResponse.ok(communityService.questions(id, page, size, keyword, resolved));
    }

    @PostMapping("/api/courses/{id}/questions")
    public Object ask(@PathVariable long id, @RequestBody CommunityService.Ask in) {
        return ApiResponse.ok(communityService.ask(id, in));
    }

    @GetMapping("/api/questions/{id}")
    public Object detail(@PathVariable long id) {
        return ApiResponse.ok(communityService.detail(id));
    }

    @GetMapping("/api/questions/{id}/replies")
    public Object replies(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(communityService.replies(id, page, size));
    }

    @PostMapping("/api/questions/{id}/replies")
    public Object reply(@PathVariable long id, @RequestBody CommunityService.ReplyWrite in) {
        return ApiResponse.ok(communityService.reply(id, in));
    }

    @PutMapping("/api/questions/{id}/resolved")
    public Object resolve(@PathVariable long id, @RequestBody CommunityService.Resolve in) {
        return ApiResponse.ok(communityService.resolve(id, in));
    }
}
