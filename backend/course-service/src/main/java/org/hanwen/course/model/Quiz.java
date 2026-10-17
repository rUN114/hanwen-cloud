package org.hanwen.course.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("chapter_quiz")
public class Quiz {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long courseId;
    public Long chapterId;
    public String title;
    public String instructions;
    public String status;
    public Integer version;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public LocalDateTime opensAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public LocalDateTime closesAt;

    public Integer timeLimitMinutes;
    public Integer maxAttempts;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
