USE hw_learning;
CREATE TABLE IF NOT EXISTS review_card (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 student_id BIGINT UNSIGNED NOT NULL, course_id BIGINT UNSIGNED NOT NULL,
 quiz_id BIGINT UNSIGNED NOT NULL, question_id BIGINT UNSIGNED NOT NULL,
 quiz_version INT NOT NULL, attempt_item_id BIGINT UNSIGNED NOT NULL,
 wrong_count INT NOT NULL DEFAULT 1, review_count INT NOT NULL DEFAULT 0,
 correct_streak INT NOT NULL DEFAULT 0, mastered BOOLEAN NOT NULL DEFAULT FALSE,
 last_wrong_at DATETIME(3) NOT NULL, last_reviewed_at DATETIME(3) NULL,
 next_review_at DATETIME(3) NOT NULL, version INT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_review_identity(student_id,quiz_id,question_id,quiz_version),
 KEY idx_review_due(student_id,mastered,next_review_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO review_card(student_id,course_id,quiz_id,question_id,quiz_version,attempt_item_id,wrong_count,last_wrong_at,next_review_at)
SELECT a.student_id,a.course_id,a.quiz_id,i.question_id,COALESCE(a.quiz_version,0),MAX(i.id),COUNT(*),MAX(a.completed_at),MAX(a.completed_at)
FROM quiz_attempt a JOIN quiz_attempt_item i ON i.attempt_id=a.id
WHERE i.correct=0 AND i.question_type IN ('SINGLE','MULTIPLE','TRUE_FALSE') GROUP BY a.student_id,a.course_id,a.quiz_id,i.question_id,COALESCE(a.quiz_version,0);

USE hw_assignment;
CREATE TABLE IF NOT EXISTS submission_revision (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 submission_id BIGINT UNSIGNED NOT NULL, student_id BIGINT UNSIGNED NOT NULL,
 teacher_id BIGINT UNSIGNED NOT NULL, base_submission_version INT NOT NULL,
 request_text VARCHAR(2000) NOT NULL, answer_text LONGTEXT NULL,
 student_note VARCHAR(1000) NULL, teacher_comment VARCHAR(2000) NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'OPEN', version INT NOT NULL DEFAULT 0,
 opened_at DATETIME(3) NOT NULL, submitted_at DATETIME(3) NULL, reviewed_at DATETIME(3) NULL,
 KEY idx_revision_submission(submission_id,id), KEY idx_revision_student(student_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
