package org.hanwen.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.learning.model.AttemptItem;

public interface AttemptItemMapper extends BaseMapper<AttemptItem> {
    @Select("SELECT * FROM quiz_attempt_item WHERE id=#{id} FOR UPDATE")
    AttemptItem lock(@Param("id") long id);
}
