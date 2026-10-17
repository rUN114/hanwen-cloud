package org.hanwen.course.mapper;

import org.apache.ibatis.annotations.*;

import java.util.*;

public interface InboxMapper {
    @Select(
            "<script>SELECT CONCAT('NOTICE:',n.id,':',n.version) AS eventKey,'NOTICE' AS"
                    + " kind,n.title,c.name AS"
                    + " courseName,DATE_FORMAT(n.updated_at,'%Y-%m-%dT%H:%i:%s.%f+08:00') AS"
                    + " eventAt,CONCAT('/courses/',c.id,'/community') AS url FROM course_notice n JOIN"
                    + " course c ON c.id=n.course_id WHERE c.status='PUBLISHED' AND n.course_id IN"
                    + " <foreach collection='ids' item='id' open='(' separator=','"
                    + " close=')'>#{id}</foreach> ORDER BY n.updated_at DESC,n.id DESC LIMIT"
                    + " 101</script>")
    List<Map<String, Object>> notices(@Param("ids") List<Long> ids);

    @Select(
            "SELECT CONCAT('QUESTION:',q.id) AS eventKey,'QUESTION' AS kind,q.title,c.name AS"
                    + " courseName,DATE_FORMAT(q.created_at,'%Y-%m-%dT%H:%i:%s.%f+08:00') AS"
                    + " eventAt,CONCAT('/questions/',q.id) AS url FROM course_question q JOIN course c"
                    + " ON c.id=q.course_id WHERE (c.teacher_id=#{id} OR EXISTS(SELECT 1 FROM course_team tm WHERE tm.course_id=c.id AND tm.user_id=#{id} AND tm.status='ACTIVE')) AND c.status!='DRAFT' ORDER BY"
                    + " q.created_at DESC,q.id DESC LIMIT 101")
    List<Map<String, Object>> questions(@Param("id") long id);

    @Select(
            "SELECT CONCAT('REPLY:',r.id) AS eventKey,'REPLY' AS kind,q.title,c.name AS"
                    + " courseName,DATE_FORMAT(r.created_at,'%Y-%m-%dT%H:%i:%s.%f+08:00') AS"
                    + " eventAt,CONCAT('/questions/',q.id) AS url FROM course_reply r JOIN"
                    + " course_question q ON q.id=r.question_id JOIN course c ON c.id=q.course_id WHERE"
                    + " c.status!='DRAFT' AND r.author_id!=#{id} AND ((#{teacher}=TRUE AND"
                    + " (c.teacher_id=#{id} OR EXISTS(SELECT 1 FROM course_team tm WHERE tm.course_id=c.id AND tm.user_id=#{id} AND tm.status='ACTIVE'))) OR (#{teacher}=FALSE AND q.student_id=#{id})) ORDER BY"
                    + " r.created_at DESC,r.id DESC LIMIT 101")
    List<Map<String, Object>> replies(@Param("id") long id, @Param("teacher") boolean teacher);
}
