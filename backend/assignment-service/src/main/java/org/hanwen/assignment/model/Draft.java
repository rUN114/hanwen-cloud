package org.hanwen.assignment.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("assignment_draft")
public class Draft {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long assignmentId;
    public Long studentId;
    public String answerText;
    public Integer version;
    public LocalDateTime updatedAt;
}
