package org.hanwen.assignment;
import org.hanwen.common.*;
import org.hanwen.assignment.mapper.*;
import org.hanwen.assignment.model.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.util.*;
@Service
public class GradebookService {
 final AssignmentService assignment;final GradePolicyMapper policies;
 public GradebookService(AssignmentService a,GradePolicyMapper p){assignment=a;policies=p;}
 public record Policy(int assignmentWeight,String missingRule,String attemptRule,Integer expectedVersion){}
 GradePolicy policy(long id){var p=policies.selectById(id);if(p==null){p=new GradePolicy();p.id=id;p.assignmentWeight=70;p.missingRule="EXCLUDE";p.attemptRule="HIGHEST";p.version=-1;}return p;}
 void access(long id){var c=assignment.course(id);if(Actor.current().role().equals("TEACHER")){if(!c.canGrade())throw ApiException.forbidden();}else{Views.role("STUDENT");if(!assignment.remote().call("grades-enrollment",()->assignment.learning().exists(id,Actor.current().id())).exists())throw ApiException.forbidden();}}
 Object view(GradePolicy p){return Views.map("assignmentWeight",p.assignmentWeight,"practiceWeight",100-p.assignmentWeight,"missingRule",p.missingRule,"attemptRule",p.attemptRule,"version",p.version,"updatedAt",p.updatedAt);}
 @Transactional public Object save(long id,Policy in){Views.role("TEACHER");access(id);if(!assignment.course(id).canTeach())throw ApiException.forbidden();if(!assignment.course(id).status().equals("PUBLISHED"))throw ApiException.conflict("归档课程总评规则只读");
  if(in.assignmentWeight()<0||in.assignmentWeight()>100||!Set.of("ZERO","EXCLUDE").contains(String.valueOf(in.missingRule()))||!Set.of("HIGHEST","LATEST").contains(String.valueOf(in.attemptRule())))throw ApiException.bad("总评规则不合法");
  // Lock a local row shared with assignment creation to serialize the first policy insert.
  var rows=assignment.tasks().selectList(new QueryWrapper<Task>().eq("course_id",id).orderByAsc("id").last("LIMIT 1"));
  if(!rows.isEmpty())assignment.tasks().lock(rows.getFirst().id);
  var p=policies.lock(id);boolean fresh=p==null;if(fresh){p=policy(id);if(!Objects.equals(in.expectedVersion(),-1))throw ApiException.conflict("规则已改变，请刷新");}else Names.version(p.version,in.expectedVersion());
  p.assignmentWeight=in.assignmentWeight();p.missingRule=in.missingRule();p.attemptRule=in.attemptRule();p.version++;p.updatedAt=Views.now();if(fresh)policies.insert(p);else policies.updateById(p);return view(p);
 }
 public Object book(long id){access(id);var p=policy(id);
  var feed=assignment.remote().call("grade-feed",()->assignment.learning().grades(id));
  var quizzes=assignment.remote().call("grade-quizzes",()->assignment.courses().quizzes(id));
  var tasks=assignment.tasks().selectList(new QueryWrapper<Task>().eq("course_id",id).ne("status","DRAFT").orderByAsc("id").last("LIMIT 501"));
  if(tasks.size()>500)throw ApiException.bad("课程作业超过500项，请分课程管理");
  var ids=tasks.stream().map(t->t.id).toList();
  var sq=new QueryWrapper<Submission>();if(ids.isEmpty())sq.eq("id",0);else sq.in("assignment_id",ids);
  if(Actor.current().role().equals("STUDENT"))sq.eq("student_id",Actor.current().id());
  var submitted=assignment.submissions().selectList(sq.last("LIMIT 250001"));if(submitted.size()>250000)throw ApiException.bad("成绩数据超过处理上限");
  var gids=submitted.stream().map(s->s.id).toList();var grades=new HashMap<Long,Grade>();
  // Bounded batches avoid oversized SQL IN lists for large classes.
  for(int i=0;i<gids.size();i+=500)for(var g:assignment.grades().selectList(new QueryWrapper<Grade>().in("submission_id",gids.subList(i,Math.min(i+500,gids.size())))))grades.put(g.submissionId,g);
  var submap=new HashMap<String,Submission>();for(var s:submitted)submap.put(s.studentId+":"+s.assignmentId,s);
  var names=Names.read(feed.students(),assignment.directory(),assignment.remote());var output=new ArrayList<Object>();
  for(var student:feed.students()){
   var items=new ArrayList<GradeRules.Item>();
   for(var t:tasks){var s=submap.get(student+":"+t.id);var g=s==null?null:grades.get(s.id);
    boolean ended=t.status.equals("CLOSED")||t.deadlineAt!=null&&!Views.now().isBefore(t.deadlineAt);
    String state=g!=null?"GRADED":s!=null?"PENDING":ended?"MISSING":"OPEN";
    items.add(new GradeRules.Item("ASSIGNMENT",t.id.toString(),t.title,state,g==null?null:GradeRules.percent(g.score,t.maxScore)));
   }
   for(var q:quizzes){Contracts.ScoreBrief selected=null;boolean awaiting=false;long latestId=0;String latestStatus="GRADED";for(var a:feed.attempts())if(a.studentId()==student&&a.quizId()==q.id()&&a.total()>0){
     if(a.attemptId()>latestId){latestId=a.attemptId();latestStatus=a.gradingStatus();}
     if("PENDING".equals(a.gradingStatus())){awaiting=true;continue;}
     if(selected==null||p.attemptRule.equals("LATEST")&&a.attemptId()>selected.attemptId()||p.attemptRule.equals("HIGHEST")&&GradeRules.percent(BigDecimal.valueOf(a.score()),BigDecimal.valueOf(a.total())).compareTo(GradeRules.percent(BigDecimal.valueOf(selected.score()),BigDecimal.valueOf(selected.total())))>0)selected=a;
    }
    boolean pending=p.attemptRule.equals("LATEST")?"PENDING".equals(latestStatus):awaiting;
    items.add(new GradeRules.Item("PRACTICE",Long.toString(q.id()),q.title(),pending?"PENDING":selected!=null?"GRADED":q.closesAt()!=null&&!Views.now().isBefore(q.closesAt())?"MISSING":"OPEN",pending||selected==null?null:GradeRules.percent(BigDecimal.valueOf(selected.score()),BigDecimal.valueOf(selected.total()))));
   }
   var result=GradeRules.calculate(items,p.assignmentWeight,p.missingRule);
   output.add(Views.map("studentId",student,"displayName",names.getOrDefault(student,"学生"),"assignment",result.assignment(),"practice",result.practice(),"provisional",result.provisional(),"total",result.total(),"pending",result.pending(),"missing",result.missing(),"items",items));
  }
  return Views.map("courseId",id,"policy",view(p),"students",output,"calculatedAt",Views.now());
 }
 public Object queue(Long courseId,String status,int page,int size){Views.role("TEACHER");int offset=Views.offset(page,size);
  if(status!=null&&!status.isBlank()&&!Set.of("SUBMITTED","GRADED").contains(status))throw ApiException.bad("提交状态不合法");
  var tq=new QueryWrapper<Task>();if(courseId!=null){access(courseId);tq.eq("course_id",courseId);}else{var ids=assignment.remote().call("review-staff-courses",()->assignment.courses().staffIds(false));if(ids.isEmpty())return Views.page(List.of(),page,size,0);tq.in("course_id",ids);}
  var tasks=assignment.tasks().selectList(tq.last("LIMIT 2001"));if(tasks.size()>2000)throw ApiException.bad("请按课程筛选批阅队列");if(tasks.isEmpty())return Views.page(List.of(),page,size,0);
  var map=new HashMap<Long,Task>();for(var t:tasks)map.put(t.id,t);
  var sq=new QueryWrapper<Submission>().in("assignment_id",map.keySet());if(status!=null&&!status.isBlank())sq.eq("status",status);
  long total=assignment.submissions().selectCount(sq);var rows=assignment.submissions().selectList(sq.orderByAsc("submitted_at","id").last("LIMIT "+size+" OFFSET "+offset));
  var names=Names.read(rows.stream().map(s->s.studentId).toList(),assignment.directory(),assignment.remote());
  return Views.page(rows.stream().map(s->{var v=AssignmentService.submissionView(s,false);v.put("taskTitle",map.get(s.assignmentId).title);v.put("courseId",map.get(s.assignmentId).courseId.toString());v.put("studentName",names.getOrDefault(s.studentId,"学生"));return v;}).toList(),page,size,total);
 }
}
