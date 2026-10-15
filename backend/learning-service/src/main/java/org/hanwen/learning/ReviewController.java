package org.hanwen.learning;
import org.hanwen.common.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/review-cards")
public class ReviewController {
 final ReviewService s;public ReviewController(ReviewService s){this.s=s;}
 @GetMapping public Object list(@RequestParam(required=false) Long courseId,@RequestParam(defaultValue="ALL")String state,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="10")int size){return ApiResponse.ok(s.list(courseId,state,page,size));}
 @GetMapping("/{id}") public Object detail(@PathVariable long id){return ApiResponse.ok(s.detail(id));}
 @PostMapping("/{id}/answers") public Object answer(@PathVariable long id,@RequestBody ReviewService.Answer in){return ApiResponse.ok(s.answer(id,in));}
}
