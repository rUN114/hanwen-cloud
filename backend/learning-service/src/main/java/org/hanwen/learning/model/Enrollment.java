package org.hanwen.learning.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("course_enrollment")
public class Enrollment {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long courseId;
    public Long studentId;
    public LocalDateTime joinedAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
