package org.hanwen.assignment.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("assignment_submission")
public class Submission {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long assignmentId;
    public Long studentId;
    public String answerText;
    public LocalDateTime submittedAt;
    public String status;
    public Integer version;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
