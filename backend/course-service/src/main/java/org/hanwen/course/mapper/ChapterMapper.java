package org.hanwen.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.course.model.Chapter;

public interface ChapterMapper extends BaseMapper<Chapter> {
    @Select("SELECT * FROM course_chapter WHERE id=#{id} FOR UPDATE")
    Chapter lock(@Param("id") long id);
}
