package org.hanwen.course.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("course")
public class Course {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long teacherId;
    public String name;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String summary;

    public String category;
    public String status;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
