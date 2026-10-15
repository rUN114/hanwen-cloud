package org.hanwen.assignment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.assignment.model.GradeHistory;

public interface GradeHistoryMapper extends BaseMapper<GradeHistory> {
    @Select("SELECT * FROM assignment_grade_history WHERE id=#{id} FOR UPDATE")
    GradeHistory lock(@Param("id") long id);
}
