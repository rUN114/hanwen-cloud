package org.hanwen.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.course.model.BankQuestion;

public interface BankQuestionMapper extends BaseMapper<BankQuestion> {
    @Select("SELECT * FROM question_bank WHERE id=#{id} FOR UPDATE")
    BankQuestion lock(@Param("id") long id);
}
