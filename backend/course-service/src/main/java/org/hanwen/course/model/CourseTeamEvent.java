package org.hanwen.course.model;

import com.baomidou.mybatisplus.annotation.*;

@TableName("course_team_event")
public class CourseTeamEvent {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long courseId, actorId, targetId;
    public String action, detail;
    public java.time.LocalDateTime createdAt;
}
