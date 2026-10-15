package org.hanwen.learning;
import org.hanwen.common.*;
import org.hanwen.learning.mapper.*;
import org.hanwen.learning.model.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class ReviewService {
 final ReviewCardMapper cards;final AttemptItemMapper items;final PracticeService practice;
 public ReviewService(ReviewCardMapper c,AttemptItemMapper i,PracticeService p){cards=c;items=i;practice=p;}
 public record Answer(String choice,Integer expectedVersion) {}
 ReviewCard owned(long id,boolean lock){Views.role("STUDENT");var c=lock?cards.lock(id):cards.selectById(id);if(c==null)throw ApiException.missing();if(c.studentId!=Actor.current().id())throw ApiException.forbidden();return c;}
 void released(ReviewCard c){if(!practice.reviewReleased(c.quizId))throw ApiException.conflict("原练习解析尚未开放，暂不能复习");}
 Map<String,Object> brief(ReviewCard c){return Views.map("id",c.id,"courseId",c.courseId,"quizId",c.quizId,"questionId",c.questionId,"quizVersion",c.quizVersion,"wrongCount",c.wrongCount,"reviewCount",c.reviewCount,"correctStreak",c.correctStreak,"mastered",c.mastered,"lastWrongAt",c.lastWrongAt,"lastReviewedAt",c.lastReviewedAt,"nextReviewAt",c.nextReviewAt,"version",c.version);}
 AttemptItem frozen(ReviewCard c){var i=items.selectById(c.attemptItemId);if(i==null)throw ApiException.missing();return i;}
 public Object list(Long course,String state,int page,int size){
  Views.role("STUDENT");int offset=Views.offset(page,size);if(!Set.of("ALL","DUE","MASTERED").contains(state))throw ApiException.bad("复习筛选无效");
  var q=new QueryWrapper<ReviewCard>().eq("student_id",Actor.current().id());if(course!=null)q.eq("course_id",course);
  // Bound notebook aggregation; larger libraries need server-side released-quiz projection.
  var all=cards.selectList(q.orderByAsc("next_review_at").orderByAsc("id").last("LIMIT 5001"));if(all.size()>5000)throw ApiException.bad("温故记录过多，请按课程筛选");
  var allowed=new HashMap<Long,Boolean>();var visible=new ArrayList<ReviewCard>();
  for(var c:all){boolean show=allowed.computeIfAbsent(c.quizId,practice::reviewReleased);if(show)visible.add(c);}
  var now=Views.now();var selected=visible.stream().filter(c->state.equals("ALL")||state.equals("MASTERED")&&c.mastered||state.equals("DUE")&&!c.mastered&&!c.nextReviewAt.isAfter(now)).toList();
  var rows=selected.stream().skip(offset).limit(size).map(c->{var v=brief(c);var i=frozen(c);v.put("prompt",i.prompt);v.put("questionType",i.questionType);return v;}).toList();
  return Views.map("items",rows,"page",page,"size",size,"total",selected.size(),"summary",Views.map("total",visible.size(),"due",visible.stream().filter(c->!c.mastered&&!c.nextReviewAt.isAfter(now)).count(),"mastered",visible.stream().filter(c->c.mastered).count()),"serverTime",now);
 }
 public Object detail(long id){var c=owned(id,false);released(c);var i=frozen(c);var v=brief(c);v.putAll(Views.map("prompt",i.prompt,"questionType",i.questionType,"optionA",i.optionA,"optionB",i.optionB,"optionC",i.optionC,"optionD",i.optionD,"previousChoice",i.selectedOption));return v;}
 @Transactional public Object answer(long id,Answer in){
  var c=owned(id,true);released(c);Names.version(c.version,in.expectedVersion());var i=frozen(c);
  if(c.nextReviewAt.isAfter(Views.now()))throw ApiException.conflict("尚未到计划复习时间，可先回看题面");
  String choice=QuestionRules.choice(i.questionType,in.choice(),false);boolean correct=choice.equals(i.correctOption);
  var schedule=ReviewRules.next(c.correctStreak,correct,Views.now());c.correctStreak=schedule.streak();c.mastered=schedule.mastered();c.nextReviewAt=schedule.next();c.lastReviewedAt=Views.now();c.reviewCount++;c.version++;cards.updateById(c);
  var v=brief(c);v.putAll(Views.map("correct",correct,"choice",choice,"correctOption",i.correctOption,"explanation",i.explanation));return v;
 }
}
