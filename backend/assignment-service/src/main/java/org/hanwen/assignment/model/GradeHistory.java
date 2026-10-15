package org.hanwen.assignment.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("assignment_grade_history")
public class GradeHistory {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long submissionId;
    public Long teacherId;
    public Integer submissionVersion;
    public java.math.BigDecimal score;
    public String comment;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String rubricScores;
    public LocalDateTime gradedAt;
}
