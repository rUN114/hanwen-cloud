package org.hanwen.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.course.model.QuizQuestion;

public interface QuizQuestionMapper extends BaseMapper<QuizQuestion> {
    @Select("SELECT * FROM quiz_question WHERE id=#{id} FOR UPDATE")
    QuizQuestion lock(@Param("id") long id);
}
