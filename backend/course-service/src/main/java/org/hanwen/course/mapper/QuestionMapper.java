package org.hanwen.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.course.model.Question;

public interface QuestionMapper extends BaseMapper<Question> {
    @Select("SELECT * FROM course_question WHERE id=#{id} FOR UPDATE")
    Question lock(@Param("id") long id);

    @Select(
            "SELECT COUNT(*) FROM course_question q JOIN course c ON c.id=q.course_id WHERE"
                    + " (c.teacher_id=#{id} OR EXISTS(SELECT 1 FROM course_team tm WHERE tm.course_id=c.id AND tm.user_id=#{id} AND tm.status='ACTIVE')) AND q.resolved=0 AND c.status='PUBLISHED'")
    long pending(@Param("id") long id);
}
