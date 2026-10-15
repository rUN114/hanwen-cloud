package org.hanwen.assignment.model;
import com.baomidou.mybatisplus.annotation.*;
@TableName("assignment_attachment") public class Attachment {
 @TableId(type=IdType.AUTO) public Long id;
 public Long assignmentId;public Long studentId;public String originalName;public String mimeType;public Long bytes;public String sha256;public String storageName;public String status;public java.time.LocalDateTime createdAt;
}
