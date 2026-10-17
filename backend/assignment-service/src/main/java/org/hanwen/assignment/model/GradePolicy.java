package org.hanwen.assignment.model;

import com.baomidou.mybatisplus.annotation.*;

@TableName("course_grade_policy")
public class GradePolicy {
    @TableId public Long id;
    public Integer assignmentWeight;
    public String missingRule;
    public String attemptRule;
    public Integer version;
    public java.time.LocalDateTime updatedAt;
}
