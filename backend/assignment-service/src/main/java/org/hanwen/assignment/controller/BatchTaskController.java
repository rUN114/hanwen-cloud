package org.hanwen.assignment.controller;

import org.hanwen.assignment.service.BatchTaskService;
import org.hanwen.common.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class BatchTaskController {
    final BatchTaskService batchTaskService;

    public BatchTaskController(BatchTaskService batchTaskService) {
        this.batchTaskService = batchTaskService;
    }

    @PostMapping("/api/teacher/assignments/batch")
    public Object apply(@RequestBody BatchTaskService.Write in) {
        return ApiResponse.ok(batchTaskService.apply(in));
    }
}
