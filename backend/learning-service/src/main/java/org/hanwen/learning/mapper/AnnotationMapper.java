package org.hanwen.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.learning.model.Annotation;

public interface AnnotationMapper extends BaseMapper<Annotation> {
    @Select("SELECT * FROM reading_annotation WHERE id=#{id} FOR UPDATE")
    Annotation lock(@Param("id") long id);
}
