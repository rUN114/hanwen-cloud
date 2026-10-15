package org.hanwen.assignment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.assignment.model.Submission;

public interface SubmissionMapper extends BaseMapper<Submission> {
    @Select("SELECT * FROM assignment_submission WHERE id=#{id} FOR UPDATE")
    Submission lock(@Param("id") long id);
}
