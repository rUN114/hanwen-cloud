package org.hanwen.learning.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("learning_note")
public class Note {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long studentId;
    public Long enrollmentId;
    public Long chapterId;
    public String title;
    public String content;
    public Integer version;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
