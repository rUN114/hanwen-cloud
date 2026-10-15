package org.hanwen.course.model;
import com.baomidou.mybatisplus.annotation.*;
@TableName("course_team") public class CourseTeam {
 @TableId(type=IdType.AUTO) public Long id;
 public Long courseId,userId; public String role,status;
 public java.time.LocalDateTime createdAt,updatedAt;
}
