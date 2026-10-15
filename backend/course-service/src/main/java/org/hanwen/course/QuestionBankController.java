package org.hanwen.course;

import org.hanwen.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teacher/question-bank")
public class QuestionBankController {
    final QuestionBankService service;
    public QuestionBankController(QuestionBankService service) { this.service = service; }
    @GetMapping
    public Object list(@RequestParam(required=false) String keyword, @RequestParam(required=false) String type,
            @RequestParam(required=false) String topic, @RequestParam(required=false) String status,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="12") int size) {
        return ApiResponse.ok(service.list(keyword, type, topic, status, page, size));
    }
    @PostMapping
    public Object create(@RequestBody QuestionBankService.Write in) { return ApiResponse.ok(service.write(null, in)); }
    @PutMapping("/{id}")
    public Object update(@PathVariable long id, @RequestBody QuestionBankService.Write in) { return ApiResponse.ok(service.write(id, in)); }
    @PutMapping("/{id}/status")
    public Object state(@PathVariable long id, @RequestBody QuestionBankService.State in) { return ApiResponse.ok(service.state(id, in)); }
}
