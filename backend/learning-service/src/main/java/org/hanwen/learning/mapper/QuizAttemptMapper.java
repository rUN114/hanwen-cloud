package org.hanwen.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.learning.model.QuizAttempt;

public interface QuizAttemptMapper extends BaseMapper<QuizAttempt> {
    @Select("SELECT * FROM quiz_attempt WHERE id=#{id} FOR UPDATE")
    QuizAttempt lock(@Param("id") long id);
}
