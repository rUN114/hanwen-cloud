package org.hanwen.learning.model;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("quiz_run")
public class QuizRun {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long quizId;
    public Long courseId;
    public Long chapterId;
    public Long studentId;
    public Integer quizVersion;
    public Integer attemptNo;
    public Integer maxAttempts;
    public String status;
    public LocalDateTime startedAt;
    public LocalDateTime deadlineAt;
    public Integer version;
}
