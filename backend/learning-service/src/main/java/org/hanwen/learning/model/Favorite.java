package org.hanwen.learning.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("work_favorite")
public class Favorite {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long studentId;
    public Long workId;
    public LocalDateTime createdAt;
}
