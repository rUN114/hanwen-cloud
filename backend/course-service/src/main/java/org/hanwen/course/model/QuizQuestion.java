package org.hanwen.course.model;

import com.baomidou.mybatisplus.annotation.*;

@TableName("quiz_question")
public class QuizQuestion {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long quizId;
    public String prompt;
    public String optionA;
    public String optionB;
    public String optionC;
    public String optionD;
    public String correctOption;
    public String explanation;
    public Integer sortNo;
    public String questionType;
    public Integer points;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String knowledgePoint;

    public Long bankId;
    public Integer bankVersion;
}
