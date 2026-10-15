package org.hanwen.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.user.model.User;

public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM sys_user WHERE id=#{id} FOR UPDATE")
    User lock(@Param("id") long id);
}
