package org.hanwen.assignment;
import org.hanwen.common.*;
import org.springframework.web.bind.annotation.*;
@RestController public class GradebookController {
 final GradebookService s;public GradebookController(GradebookService x){s=x;}
 @GetMapping("/api/grades/courses/{id}") public Object book(@PathVariable long id){return ApiResponse.ok(s.book(id));}
 @PutMapping("/api/grades/courses/{id}/policy") public Object policy(@PathVariable long id,@RequestBody GradebookService.Policy in){return ApiResponse.ok(s.save(id,in));}
 @GetMapping("/api/submissions/queue") public Object queue(@RequestParam(required=false)Long courseId,@RequestParam(required=false)String status,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.ok(s.queue(courseId,status,page,size));}
}
