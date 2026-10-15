package org.hanwen.learning.model;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("review_card")
public class ReviewCard {
 @TableId(type=IdType.AUTO) public Long id;
 public Long studentId,courseId,quizId,questionId,attemptItemId;
 public Integer quizVersion,wrongCount,reviewCount,correctStreak,version;
 public Boolean mastered;
 public LocalDateTime lastWrongAt,lastReviewedAt,nextReviewAt;
}
