package org.hanwen.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.learning.model.Note;

public interface NoteMapper extends BaseMapper<Note> {
    @Select("SELECT * FROM learning_note WHERE id=#{id} FOR UPDATE")
    Note lock(@Param("id") long id);
}
