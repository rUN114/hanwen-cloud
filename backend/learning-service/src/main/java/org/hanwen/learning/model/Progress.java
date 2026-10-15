package org.hanwen.learning.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("learning_progress")
public class Progress {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long enrollmentId;
    public Long chapterId;
    public Boolean completed;
    public LocalDateTime completedAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
