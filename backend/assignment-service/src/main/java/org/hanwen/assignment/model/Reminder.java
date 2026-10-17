package org.hanwen.assignment.model;

import com.baomidou.mybatisplus.annotation.*;

@TableName("assignment_reminder")
public class Reminder {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long assignmentId;
    public Long studentId;
    public Long teacherId;
    public String content;
    public java.time.LocalDate dayKey;
    public java.time.LocalDateTime createdAt;
}
