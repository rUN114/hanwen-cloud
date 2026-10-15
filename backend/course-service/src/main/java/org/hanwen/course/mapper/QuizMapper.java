package org.hanwen.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.course.model.Quiz;

public interface QuizMapper extends BaseMapper<Quiz> {
    @Select("SELECT * FROM chapter_quiz WHERE id=#{id} FOR UPDATE")
    Quiz lock(@Param("id") long id);
}
