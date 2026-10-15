package org.hanwen.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.*;
import org.hanwen.learning.model.Receipt;

public interface ReceiptMapper extends BaseMapper<Receipt> {
    @Select("SELECT * FROM inbox_receipt WHERE id=#{id} FOR UPDATE")
    Receipt lock(@Param("id") long id);
}
