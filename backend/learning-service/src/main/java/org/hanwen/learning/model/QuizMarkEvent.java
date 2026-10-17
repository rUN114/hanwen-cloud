package org.hanwen.learning.model;

import com.baomidou.mybatisplus.annotation.*;

@TableName("quiz_mark_event")
public class QuizMarkEvent {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long attemptId, teacherId;
    public Integer version, score;
    public String itemsJson;
    public java.time.LocalDateTime createdAt;
}
