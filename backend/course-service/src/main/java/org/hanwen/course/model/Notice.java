package org.hanwen.course.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("course_notice")
public class Notice {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long courseId;
    public Long authorId;
    public String title;
    public String content;
    public Boolean pinned;
    public Integer version;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
