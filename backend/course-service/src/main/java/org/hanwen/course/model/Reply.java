package org.hanwen.course.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("course_reply")
public class Reply {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long questionId;
    public Long authorId;
    public String role;
    public String content;
    public LocalDateTime createdAt;
}
