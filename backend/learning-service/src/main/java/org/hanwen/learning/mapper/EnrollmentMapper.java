package org.hanwen.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.learning.model.Enrollment;

public interface EnrollmentMapper extends BaseMapper<Enrollment> {
    @Select("SELECT * FROM course_enrollment WHERE id=#{id} FOR UPDATE")
    Enrollment lock(@Param("id") long id);
}
