package org.hanwen.learning.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("class_member")
public class ClassMember {
    @TableId(type = IdType.AUTO)
    public Long id;

    public String status;
    public Long classId;
    public Long studentId;
    public Long enrollmentId;
    public LocalDateTime joinedAt;
}
