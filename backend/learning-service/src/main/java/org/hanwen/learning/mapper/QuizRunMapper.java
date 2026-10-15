package org.hanwen.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.hanwen.learning.model.QuizRun;

public interface QuizRunMapper extends BaseMapper<QuizRun> {
    @Select("SELECT * FROM quiz_run WHERE id=#{id} FOR UPDATE")
    QuizRun lock(@Param("id") long id);
}
