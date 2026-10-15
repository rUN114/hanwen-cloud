package org.hanwen.assignment.mapper;

import org.apache.ibatis.annotations.*;

import java.util.*;

public interface AnalyticsMapper {
    @Select(
            "SELECT t.id, t.title, t.max_score AS maxScore, t.status, t.deadline_at AS deadlineAt,"
                + " COUNT(s.id) AS submitted, COUNT(g.id) AS graded, ROUND(AVG(g.score),2) AS"
                + " averageScore FROM assignment_task t LEFT JOIN assignment_submission s ON"
                + " s.assignment_id=t.id LEFT JOIN assignment_grade g ON g.submission_id=s.id WHERE"
                + " t.course_id=#{courseId} AND t.status!='DRAFT' GROUP BY t.id ORDER BY t.id DESC")
    List<Map<String, Object>> tasks(@Param("courseId") long courseId);

    @Select(
            "SELECT s.student_id AS studentId, COUNT(s.id) AS submitted, COUNT(g.id) AS graded,"
                + " ROUND(AVG(g.score/t.max_score*100),2) AS averagePercent FROM assignment_task t"
                + " JOIN assignment_submission s ON s.assignment_id=t.id LEFT JOIN assignment_grade"
                + " g ON g.submission_id=s.id WHERE t.course_id=#{courseId} AND t.status!='DRAFT'"
                + " GROUP BY s.student_id")
    List<Map<String, Object>> learners(@Param("courseId") long courseId);

    @Select(
            "<script>SELECT COUNT(s.id) FROM assignment_submission s JOIN assignment_task t ON"
                + " t.id=s.assignment_id WHERE t.course_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> AND s.status='SUBMITTED'</script>")
    long pending(@Param("ids") List<Long> ids);

    @Select(
            "<script>SELECT s.id,s.assignment_id AS assignmentId,t.title AS taskTitle,s.student_id AS"
                + " studentId,s.submitted_at AS submittedAt FROM assignment_submission s JOIN"
                + " assignment_task t ON t.id=s.assignment_id WHERE t.course_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> AND"
                + " s.status='SUBMITTED' ORDER BY s.submitted_at ASC,s.id ASC LIMIT 8</script>")
    List<Map<String, Object>> inbox(@Param("ids") List<Long> ids);
}
