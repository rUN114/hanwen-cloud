package org.hanwen.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.learning.model.Progress;

public interface ProgressMapper extends BaseMapper<Progress> {
    @Select("SELECT * FROM learning_progress WHERE id=#{id} FOR UPDATE")
    Progress lock(@Param("id") long id);

    @org.apache.ibatis.annotations.Select(
            "SELECT COUNT(*) FROM learning_progress p JOIN course_enrollment e ON"
                    + " e.id=p.enrollment_id WHERE e.student_id=#{id} AND p.completed=1")
    long completedByStudent(@org.apache.ibatis.annotations.Param("id") long id);
}
