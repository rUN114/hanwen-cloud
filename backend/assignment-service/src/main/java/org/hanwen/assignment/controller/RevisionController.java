package org.hanwen.assignment.controller;

import org.hanwen.assignment.service.RevisionService;
import org.hanwen.common.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/submissions/{submissionId}/revisions")
public class RevisionController {
    final RevisionService revisionService;

    public RevisionController(RevisionService revisionService) {
        this.revisionService = revisionService;
    }

    public record Cancel(Integer expectedVersion) {}

    @GetMapping
    public Object history(@PathVariable long submissionId) {
        return ApiResponse.ok(revisionService.history(submissionId));
    }

    @PostMapping
    public Object invite(@PathVariable long submissionId, @RequestBody RevisionService.Invite in) {
        return ApiResponse.ok(revisionService.invite(submissionId, in));
    }

    @PostMapping("/{id}/submit")
    public Object submit(
            @PathVariable long submissionId,
            @PathVariable long id,
            @RequestBody RevisionService.Submit in) {
        return ApiResponse.ok(revisionService.submit(submissionId, id, in));
    }

    @PostMapping("/{id}/review")
    public Object review(
            @PathVariable long submissionId,
            @PathVariable long id,
            @RequestBody RevisionService.Review in) {
        return ApiResponse.ok(revisionService.review(submissionId, id, in));
    }

    @PostMapping("/{id}/cancel")
    public Object cancel(
            @PathVariable long submissionId, @PathVariable long id, @RequestBody Cancel in) {
        return ApiResponse.ok(revisionService.cancel(submissionId, id, in.expectedVersion()));
    }
}
