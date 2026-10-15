package org.hanwen.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.course.model.Work;

public interface WorkMapper extends BaseMapper<Work> {
    @Select("SELECT * FROM literature_work WHERE id=#{id} FOR UPDATE")
    Work lock(@Param("id") long id);
}
