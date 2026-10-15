package org.hanwen.learning.model;

import com.baomidou.mybatisplus.annotation.*;

@TableName("quiz_run_question")
public class RunQuestion {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long runId;
    public Long questionId;
    public String prompt;
    public String optionA;
    public String optionB;
    public String optionC;
    public String optionD;
    public String correctOption;
    public String explanation;
    public String questionType;
    public Integer points;
    public String knowledgePoint;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String selectedOption;
}
