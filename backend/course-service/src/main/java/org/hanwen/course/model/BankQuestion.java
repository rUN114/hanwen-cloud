package org.hanwen.course.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("question_bank")
public class BankQuestion {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long teacherId;
    public String questionType;
    public String prompt;
    public String optionA;
    public String optionB;
    public String optionC;
    public String optionD;
    public String correctOption;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String explanation;

    public Integer points;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String topic;

    public String status;
    public Integer version;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
