package org.hanwen.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.course.model.Notice;

public interface NoticeMapper extends BaseMapper<Notice> {
    @Select("SELECT * FROM course_notice WHERE id=#{id} FOR UPDATE")
    Notice lock(@Param("id") long id);
}
