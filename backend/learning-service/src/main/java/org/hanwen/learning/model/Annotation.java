package org.hanwen.learning.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("reading_annotation")
public class Annotation {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long studentId;
    public Long enrollmentId;
    public Long chapterId;
    public Integer startOffset;
    public Integer endOffset;
    public String quoteText;
    public String sourceHash;
    public String comment;
    public String color;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
