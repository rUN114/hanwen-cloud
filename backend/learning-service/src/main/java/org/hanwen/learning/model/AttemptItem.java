package org.hanwen.learning.model;

import com.baomidou.mybatisplus.annotation.*;

@TableName("quiz_attempt_item")
public class AttemptItem {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long attemptId;
    public Long questionId;
    public String prompt;
    public String optionA;
    public String optionB;
    public String optionC;
    public String optionD;
    public String selectedOption;
    public String correctOption;
    public String explanation;
    public Boolean correct;
    public String questionType;
    public Integer points;
    public String knowledgePoint;
    public Integer awardedPoints;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String teacherComment;
}
