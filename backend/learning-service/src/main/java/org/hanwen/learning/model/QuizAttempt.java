package org.hanwen.learning.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("quiz_attempt")
public class QuizAttempt {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long quizId;
    public Long courseId;
    public Long chapterId;
    public Long studentId;
    public Integer score;
    public Integer total;
    public String gradingStatus;
    public Integer gradingVersion;
    public Long graderId;
    public LocalDateTime gradedAt;
    public Long runId;
    public Integer attemptNo;
    public Integer quizVersion;
    public LocalDateTime completedAt;
}
