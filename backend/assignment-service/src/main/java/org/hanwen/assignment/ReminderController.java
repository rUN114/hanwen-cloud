package org.hanwen.assignment;
import org.hanwen.common.*;import org.springframework.web.bind.annotation.*;
@RestController public class ReminderController{final ReminderService s;public ReminderController(ReminderService x){s=x;}
 @GetMapping("/api/teacher/assignments/{id}/reminder-candidates")public Object candidates(@PathVariable long id){return ApiResponse.ok(s.candidates(id));}
 @PostMapping("/api/teacher/assignments/{id}/remind")public Object send(@PathVariable long id,@RequestBody ReminderService.Send in){return ApiResponse.ok(s.send(id,in));}
 @GetMapping("/api/assignments/reminders")public Object feed(){return ApiResponse.ok(s.feed());}
 @PostMapping("/api/teacher/assignments/copy")public Object copy(@RequestBody ReminderService.Copy in){return ApiResponse.ok(s.copy(in));}
}
