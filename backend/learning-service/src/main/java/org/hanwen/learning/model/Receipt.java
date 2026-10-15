package org.hanwen.learning.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("inbox_receipt")
public class Receipt {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long userId;
    public String eventKey;
    public LocalDateTime readAt;
}
