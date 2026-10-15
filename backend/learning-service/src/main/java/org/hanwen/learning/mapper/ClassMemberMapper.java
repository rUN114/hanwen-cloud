package org.hanwen.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.learning.model.ClassMember;

public interface ClassMemberMapper extends BaseMapper<ClassMember> {
    @Select("SELECT * FROM class_member WHERE id=#{id} FOR UPDATE")
    ClassMember lock(@Param("id") long id);
}
