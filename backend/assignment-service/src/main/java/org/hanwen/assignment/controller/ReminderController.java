package org.hanwen.assignment.controller;

import org.hanwen.assignment.service.ReminderService;
import org.hanwen.common.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReminderController {
    final ReminderService reminderService;

    public ReminderController(ReminderService x) {
        reminderService = x;
    }

    @GetMapping("/api/teacher/assignments/{id}/reminder-candidates")
    public Object candidates(@PathVariable long id) {
        return ApiResponse.ok(reminderService.candidates(id));
    }

    @PostMapping("/api/teacher/assignments/{id}/remind")
    public Object send(@PathVariable long id, @RequestBody ReminderService.Send in) {
        return ApiResponse.ok(reminderService.send(id, in));
    }

    @GetMapping("/api/assignments/reminders")
    public Object feed() {
        return ApiResponse.ok(reminderService.feed());
    }

    @PostMapping("/api/teacher/assignments/copy")
    public Object copy(@RequestBody ReminderService.Copy in) {
        return ApiResponse.ok(reminderService.copy(in));
    }
}
