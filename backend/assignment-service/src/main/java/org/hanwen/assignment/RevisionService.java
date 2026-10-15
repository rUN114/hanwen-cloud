package org.hanwen.assignment;
import org.hanwen.common.*;
import org.hanwen.assignment.model.*;
import org.hanwen.assignment.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class RevisionService {
 final AssignmentService assignment;final SubmissionRevisionMapper revisions;
 public RevisionService(AssignmentService a,SubmissionRevisionMapper r){assignment=a;revisions=r;}
 public record Invite(String requestText,Integer expectedSubmissionVersion) {}
 public record Submit(String answerText,String studentNote,Integer expectedVersion) {}
 public record Review(String decision,String comment,Integer expectedVersion) {}
 Submission sub(long id,boolean lock){var s=lock?assignment.submissions().lock(id):assignment.submissions().selectById(id);if(s==null)throw ApiException.missing();return s;}
 Task access(Submission s){var t=assignment.task(s.assignmentId);if(Actor.current().role().equals("TEACHER"))assignment.owner(t);else{Views.role("STUDENT");if(s.studentId!=Actor.current().id())throw ApiException.forbidden();assignment.student(t,false);}return t;}
 void writable(Task t){if(!assignment.course(t.courseId).status().equals("PUBLISHED"))throw ApiException.conflict("课程已归档，修订记录只读");}
 List<SubmissionRevision> rows(long id){return revisions.selectList(new QueryWrapper<SubmissionRevision>().eq("submission_id",id).orderByAsc("id"));}
 void room(List<SubmissionRevision> rows){if(rows.size()>=50)throw ApiException.conflict("每份习作最多50轮修订，请整理现有反馈");}
 boolean active(SubmissionRevision r){return Set.of("OPEN","SUBMITTED").contains(r.status);}
 Map<String,Object> view(SubmissionRevision r){return Views.map("id",r.id,"submissionId",r.submissionId,"baseSubmissionVersion",r.baseSubmissionVersion,"requestText",r.requestText,"answerText",r.answerText,"studentNote",r.studentNote,"teacherComment",r.teacherComment,"status",r.status,"version",r.version,"openedAt",r.openedAt,"submittedAt",r.submittedAt,"reviewedAt",r.reviewedAt);}
 SubmissionRevision open(Submission s,String text){var r=new SubmissionRevision();r.submissionId=s.id;r.studentId=s.studentId;r.teacherId=Actor.current().id();r.baseSubmissionVersion=s.version;r.requestText=Views.text(text,2000,"修订要求");r.status="OPEN";r.version=0;r.openedAt=Views.now();revisions.insert(r);return r;}
 public Object history(long id){var s=sub(id,false);var t=access(s);return Views.map("originalText",s.answerText,"courseStatus",assignment.course(t.courseId).status(),"items",rows(id).stream().map(this::view).toList());}
 @Transactional public Object invite(long id,Invite in){
  Views.role("TEACHER");var s=sub(id,true);var t=access(s);writable(t);Names.version(s.version,in.expectedSubmissionVersion());
  if(!s.status.equals("GRADED"))throw ApiException.conflict("请先批阅正式答卷，再发起修订");var rows=rows(id);room(rows);if(rows.stream().anyMatch(this::active))throw ApiException.conflict("当前已有未完成修订");return view(open(s,in.requestText()));
 }
 SubmissionRevision required(long id,Submission s){var r=revisions.selectById(id);if(r==null||!r.submissionId.equals(s.id))throw ApiException.missing();return r;}
 @Transactional public Object submit(long submission,long id,Submit in){
  Views.role("STUDENT");var s=sub(submission,true);var t=access(s);writable(t);var r=required(id,s);Names.version(r.version,in.expectedVersion());if(!r.status.equals("OPEN"))throw ApiException.conflict("修订状态已改变，请刷新");
  String text=Views.text(in.answerText(),10000,"修订稿");var prior=rows(s.id).stream().filter(x->x.answerText!=null).reduce((a,b)->b);if(text.equals(prior.map(x->x.answerText).orElse(s.answerText)))throw ApiException.bad("请修改正文后提交，并说明修改之处");
  r.answerText=text;r.studentNote=Views.optional(in.studentNote(),1000,"修订说明");r.status="SUBMITTED";r.version++;r.submittedAt=Views.now();revisions.updateById(r);return view(r);
 }
 @Transactional public Object review(long submission,long id,Review in){
  Views.role("TEACHER");var s=sub(submission,true);var t=access(s);writable(t);var r=required(id,s);Names.version(r.version,in.expectedVersion());if(!r.status.equals("SUBMITTED"))throw ApiException.conflict("学生尚未提交或此修订已评阅");if(in.decision()==null||!Set.of("ACCEPTED","CHANGES").contains(in.decision()))throw ApiException.bad("修订结论无效");
  String comment=Views.text(in.comment(),2000,"修订反馈");if(in.decision().equals("CHANGES"))room(rows(s.id));r.teacherComment=comment;r.status=in.decision();r.reviewedAt=Views.now();r.version++;revisions.updateById(r);
  if(r.status.equals("CHANGES"))open(s,comment);return view(r);
 }
 @Transactional public Object cancel(long submission,long id,Integer version){Views.role("TEACHER");var s=sub(submission,true);var t=access(s);writable(t);var r=required(id,s);Names.version(r.version,version);if(!r.status.equals("OPEN"))throw ApiException.conflict("只可取消尚未提交的修订邀请");r.status="CANCELLED";r.version++;r.reviewedAt=Views.now();revisions.updateById(r);return view(r);}
}
