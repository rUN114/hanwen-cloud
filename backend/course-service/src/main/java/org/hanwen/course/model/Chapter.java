package org.hanwen.course.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("course_chapter")
public class Chapter {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long courseId;
    public String title;
    public String content;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String lessonDocument;

    public Integer sortNo;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
