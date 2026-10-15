package org.hanwen.user.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("sys_user")
public class User {
    @TableId(type = IdType.AUTO)
    public Long id;

    public String username;
    public String passwordHash;
    public String displayName;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String email;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String biography;

    public String role;
    public String status;
    public Integer authVersion;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
