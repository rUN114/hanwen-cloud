package org.hanwen.assignment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.assignment.model.Grade;

public interface GradeMapper extends BaseMapper<Grade> {
    @Select("SELECT * FROM assignment_grade WHERE id=#{id} FOR UPDATE")
    Grade lock(@Param("id") long id);
}
