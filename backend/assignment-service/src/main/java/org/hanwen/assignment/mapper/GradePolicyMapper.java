package org.hanwen.assignment.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.hanwen.assignment.model.GradePolicy;
import org.apache.ibatis.annotations.*;
public interface GradePolicyMapper extends BaseMapper<GradePolicy> {
 @Select("SELECT * FROM course_grade_policy WHERE id=#{id} FOR UPDATE") GradePolicy lock(@Param("id") long id);
}
