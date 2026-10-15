package org.hanwen.course.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("course_question")
public class Question {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long courseId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long chapterId;

    public Long studentId;
    public String title;
    public String content;
    public Boolean resolved;
    public Integer version;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
