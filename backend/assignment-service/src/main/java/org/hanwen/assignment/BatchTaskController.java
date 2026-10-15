package org.hanwen.assignment;
import org.hanwen.common.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class BatchTaskController {
 final BatchTaskService s;public BatchTaskController(BatchTaskService s){this.s=s;}
 @PostMapping("/api/teacher/assignments/batch") public Object apply(@RequestBody BatchTaskService.Write in){return ApiResponse.ok(s.apply(in));}
}
