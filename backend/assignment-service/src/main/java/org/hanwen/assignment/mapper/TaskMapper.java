package org.hanwen.assignment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.assignment.model.Task;

public interface TaskMapper extends BaseMapper<Task> {
    @Select("SELECT * FROM assignment_task WHERE id=#{id} FOR UPDATE")
    Task lock(@Param("id") long id);
}
