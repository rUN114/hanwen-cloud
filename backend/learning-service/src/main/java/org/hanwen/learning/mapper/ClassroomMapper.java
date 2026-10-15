package org.hanwen.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.learning.model.Classroom;

public interface ClassroomMapper extends BaseMapper<Classroom> {
    @Select("SELECT * FROM teaching_class WHERE id=#{id} FOR UPDATE")
    Classroom lock(@Param("id") long id);
}
