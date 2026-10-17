package org.hanwen.course.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("literature_work")
public class Work {
    @TableId(type = IdType.AUTO)
    public Long id;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long teacherId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String seedKey;

    public String title;
    public String author;
    public String dynasty;
    public String genre;
    public String content;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String readingGuide;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String sourceUrl;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String sourceNote;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String translation;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String annotationsText;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String allusionsJson;

    public String status;
    public Integer version;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
