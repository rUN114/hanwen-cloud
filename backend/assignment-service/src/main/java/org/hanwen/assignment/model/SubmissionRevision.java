package org.hanwen.assignment.model;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("submission_revision")
public class SubmissionRevision {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long submissionId, studentId, teacherId;
    public Integer baseSubmissionVersion, version;
    public String requestText, answerText, studentNote, teacherComment, status;
    public LocalDateTime openedAt, submittedAt, reviewedAt;
}
