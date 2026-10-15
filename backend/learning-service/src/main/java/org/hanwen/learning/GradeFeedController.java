package org.hanwen.learning;
import org.hanwen.common.*;
import org.hanwen.learning.mapper.*;
import org.hanwen.learning.model.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.web.bind.annotation.*;
@RestController
public class GradeFeedController {
 final LearningService learning; final EnrollmentMapper enrollments; final QuizAttemptMapper attempts;
 public GradeFeedController(LearningService l,EnrollmentMapper e,QuizAttemptMapper a){learning=l;enrollments=e;attempts=a;}
 @GetMapping("/internal/courses/{id}/grade-feed")
 public Contracts.GradeFeed feed(@PathVariable long id){
  var c=learning.course(id);var actor=Actor.current();
  var eq=new QueryWrapper<Enrollment>().eq("course_id",id);
  var aq=new QueryWrapper<QuizAttempt>().eq("course_id",id);
  if(actor.role().equals("TEACHER")){if(!c.canGrade())throw ApiException.forbidden();}
  else{Views.role("STUDENT");if(!learning.exists(id,actor.id()).exists())throw ApiException.forbidden();eq.eq("student_id",actor.id());aq.eq("student_id",actor.id());}
  var students=enrollments.selectList(eq.orderByAsc("id").last("LIMIT 501"));
  var scores=attempts.selectList(aq.orderByAsc("id").last("LIMIT 20001"));
  if(students.size()>500||scores.size()>20000)throw ApiException.bad("成绩册超过单课程500人或20000答卷限制，请分班分课程管理");
  return new Contracts.GradeFeed(students.stream().map(e->e.studentId).toList(),scores.stream().map(a->new Contracts.ScoreBrief(a.studentId,a.quizId,a.score,a.total,a.id,a.gradingStatus)).toList());
 }
}
