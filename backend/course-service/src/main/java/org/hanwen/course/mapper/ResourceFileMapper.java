package org.hanwen.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.course.model.ResourceFile;

public interface ResourceFileMapper extends BaseMapper<ResourceFile> {
    @Select("SELECT * FROM course_resource WHERE id=#{id} FOR UPDATE")
    ResourceFile lock(@Param("id") long id);
}
