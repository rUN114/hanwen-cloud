package org.hanwen.learning.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.hanwen.learning.model.ReviewCard;
public interface ReviewCardMapper extends BaseMapper<ReviewCard> {
 @Select("SELECT * FROM review_card WHERE id=#{id} FOR UPDATE") ReviewCard lock(long id);
 @Insert("""
 INSERT INTO review_card(student_id,course_id,quiz_id,question_id,quiz_version,attempt_item_id,last_wrong_at,next_review_at)
 VALUES(#{studentId},#{courseId},#{quizId},#{questionId},#{quizVersion},#{attemptItemId},#{lastWrongAt},#{nextReviewAt}) AS incoming
 ON DUPLICATE KEY UPDATE attempt_item_id=incoming.attempt_item_id,last_wrong_at=incoming.last_wrong_at,
 next_review_at=incoming.next_review_at,wrong_count=wrong_count+1,correct_streak=0,mastered=FALSE,version=version+1
 """) void mistake(ReviewCard card);
}
