package org.hanwen.assignment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.assignment.model.Draft;

public interface DraftMapper extends BaseMapper<Draft> {
    @Select("SELECT * FROM assignment_draft WHERE id=#{id} FOR UPDATE")
    Draft lock(@Param("id") long id);
}
