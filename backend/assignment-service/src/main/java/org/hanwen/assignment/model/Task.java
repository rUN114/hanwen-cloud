package org.hanwen.assignment.model;

import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("assignment_task")
public class Task {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long courseId;
    public Long teacherId;
    public String title;
    public String description;
    public BigDecimal maxScore;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public LocalDateTime deadlineAt;

    public String rubricJson;
    public String status;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
