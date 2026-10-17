package org.hanwen.course.model;

import com.baomidou.mybatisplus.annotation.*;

@TableName("course_team_state")
public class CourseTeamState {
    @TableId(type = IdType.INPUT)
    public Long id;

    public Integer version;
}
