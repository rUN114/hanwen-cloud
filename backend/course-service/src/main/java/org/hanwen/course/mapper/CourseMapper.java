package org.hanwen.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.course.model.Course;

public interface CourseMapper extends BaseMapper<Course> {
    @Select("SELECT * FROM course WHERE id=#{id} FOR UPDATE")
    Course lock(@Param("id") long id);
}
