package org.hanwen.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.learning.model.Favorite;

public interface FavoriteMapper extends BaseMapper<Favorite> {
    @Select("SELECT * FROM work_favorite WHERE id=#{id} FOR UPDATE")
    Favorite lock(@Param("id") long id);
}
