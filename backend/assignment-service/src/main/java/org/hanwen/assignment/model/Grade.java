package org.hanwen.assignment.model;

import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("assignment_grade")
public class Grade {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long submissionId;
    public Long teacherId;
    public BigDecimal score;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String comment;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String rubricScores;

    public LocalDateTime gradedAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
