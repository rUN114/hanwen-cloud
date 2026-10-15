package org.hanwen.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.course.model.Reply;

public interface ReplyMapper extends BaseMapper<Reply> {
    @Select("SELECT * FROM course_reply WHERE id=#{id} FOR UPDATE")
    Reply lock(@Param("id") long id);
}
