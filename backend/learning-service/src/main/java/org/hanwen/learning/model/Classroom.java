package org.hanwen.learning.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("teaching_class")
public class Classroom {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long courseId;
    public Long teacherId;
    public String name;
    public String semester;
    public String description;
    public String joinCode;
    public String status;
    public Integer version;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
