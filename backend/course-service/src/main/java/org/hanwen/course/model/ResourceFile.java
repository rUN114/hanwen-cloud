package org.hanwen.course.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("course_resource")
public class ResourceFile {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long courseId;
    public Long chapterId;
    public String title;
    public String description;
    public String originalName;
    public String storageName;
    public String mimeType;
    public Long bytes;
    public String sha256;
    public String status;
    public LocalDateTime createdAt;
}
