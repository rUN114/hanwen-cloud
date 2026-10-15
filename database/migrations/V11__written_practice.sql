USE hw_course;
ALTER TABLE quiz_question MODIFY COLUMN correct_option LONGTEXT NOT NULL;
ALTER TABLE question_bank MODIFY COLUMN correct_option LONGTEXT NOT NULL;
SET @hw_v11_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='hw_course' AND table_name='quiz_question' AND column_name='knowledge_point')=0,'ALTER TABLE `hw_course`.`quiz_question` ADD COLUMN `knowledge_point` VARCHAR(60) NULL','SELECT 1');
PREPARE hw_v11 FROM @hw_v11_sql; EXECUTE hw_v11; DEALLOCATE PREPARE hw_v11;
SET @hw_v11_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='hw_learning' AND table_name='quiz_run_question' AND column_name='knowledge_point')=0,'ALTER TABLE `hw_learning`.`quiz_run_question` ADD COLUMN `knowledge_point` VARCHAR(60) NULL','SELECT 1');
PREPARE hw_v11 FROM @hw_v11_sql; EXECUTE hw_v11; DEALLOCATE PREPARE hw_v11;
SET @hw_v11_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='hw_learning' AND table_name='quiz_attempt_item' AND column_name='knowledge_point')=0,'ALTER TABLE `hw_learning`.`quiz_attempt_item` ADD COLUMN `knowledge_point` VARCHAR(60) NULL','SELECT 1');
PREPARE hw_v11 FROM @hw_v11_sql; EXECUTE hw_v11; DEALLOCATE PREPARE hw_v11;
SET @hw_v11_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='hw_learning' AND table_name='quiz_attempt_item' AND column_name='awarded_points')=0,'ALTER TABLE `hw_learning`.`quiz_attempt_item` ADD COLUMN `awarded_points` INT NULL','SELECT 1');
PREPARE hw_v11 FROM @hw_v11_sql; EXECUTE hw_v11; DEALLOCATE PREPARE hw_v11;
SET @hw_v11_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='hw_learning' AND table_name='quiz_attempt_item' AND column_name='teacher_comment')=0,'ALTER TABLE `hw_learning`.`quiz_attempt_item` ADD COLUMN `teacher_comment` VARCHAR(2000) NULL','SELECT 1');
PREPARE hw_v11 FROM @hw_v11_sql; EXECUTE hw_v11; DEALLOCATE PREPARE hw_v11;
SET @hw_v11_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='hw_learning' AND table_name='quiz_attempt' AND column_name='grading_status')=0,'ALTER TABLE `hw_learning`.`quiz_attempt` ADD COLUMN `grading_status` VARCHAR(16) NOT NULL DEFAULT ''GRADED''','SELECT 1');
PREPARE hw_v11 FROM @hw_v11_sql; EXECUTE hw_v11; DEALLOCATE PREPARE hw_v11;
SET @hw_v11_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='hw_learning' AND table_name='quiz_attempt' AND column_name='grading_version')=0,'ALTER TABLE `hw_learning`.`quiz_attempt` ADD COLUMN `grading_version` INT NOT NULL DEFAULT 0','SELECT 1');
PREPARE hw_v11 FROM @hw_v11_sql; EXECUTE hw_v11; DEALLOCATE PREPARE hw_v11;
SET @hw_v11_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='hw_learning' AND table_name='quiz_attempt' AND column_name='grader_id')=0,'ALTER TABLE `hw_learning`.`quiz_attempt` ADD COLUMN `grader_id` BIGINT UNSIGNED NULL','SELECT 1');
PREPARE hw_v11 FROM @hw_v11_sql; EXECUTE hw_v11; DEALLOCATE PREPARE hw_v11;
SET @hw_v11_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='hw_learning' AND table_name='quiz_attempt' AND column_name='graded_at')=0,'ALTER TABLE `hw_learning`.`quiz_attempt` ADD COLUMN `graded_at` DATETIME(3) NULL','SELECT 1');
PREPARE hw_v11 FROM @hw_v11_sql; EXECUTE hw_v11; DEALLOCATE PREPARE hw_v11;
USE hw_learning;
ALTER TABLE quiz_run_question MODIFY COLUMN correct_option LONGTEXT NOT NULL, MODIFY COLUMN selected_option LONGTEXT NULL;
ALTER TABLE quiz_attempt_item MODIFY COLUMN correct_option LONGTEXT NOT NULL, MODIFY COLUMN selected_option LONGTEXT NOT NULL, MODIFY COLUMN correct BOOLEAN NULL;
CREATE TABLE IF NOT EXISTS quiz_mark_event (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, attempt_id BIGINT UNSIGNED NOT NULL,
 teacher_id BIGINT UNSIGNED NOT NULL, version INT NOT NULL, score INT NOT NULL,
 items_json LONGTEXT NOT NULL, created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_mark_version(attempt_id,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V7 expanded checks retain objective constraints and admit bounded written responses.
SET @hw_v11_sql = IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema='hw_course' AND table_name='question_bank' AND constraint_name='question_bank_chk_1')>0,'ALTER TABLE `hw_course`.`question_bank` DROP CHECK `question_bank_chk_1`','SELECT 1');
PREPARE hw_v11 FROM @hw_v11_sql; EXECUTE hw_v11; DEALLOCATE PREPARE hw_v11;
ALTER TABLE `hw_course`.`question_bank` ADD CONSTRAINT `question_bank_chk_1` CHECK (question_type IN ('SINGLE','MULTIPLE','TRUE_FALSE','FILL_BLANK','SHORT_ANSWER'));
SET @hw_v11_sql = IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema='hw_course' AND table_name='quiz_question' AND constraint_name='ck_p2_quiz_question_type')>0,'ALTER TABLE `hw_course`.`quiz_question` DROP CHECK `ck_p2_quiz_question_type`','SELECT 1');
PREPARE hw_v11 FROM @hw_v11_sql; EXECUTE hw_v11; DEALLOCATE PREPARE hw_v11;
ALTER TABLE `hw_course`.`quiz_question` ADD CONSTRAINT `ck_p2_quiz_question_type` CHECK (question_type IN ('SINGLE','MULTIPLE','TRUE_FALSE','FILL_BLANK','SHORT_ANSWER') AND points BETWEEN 1 AND 100);
SET @hw_v11_sql = IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema='hw_course' AND table_name='quiz_question' AND constraint_name='ck_p2_quiz_question_answer')>0,'ALTER TABLE `hw_course`.`quiz_question` DROP CHECK `ck_p2_quiz_question_answer`','SELECT 1');
PREPARE hw_v11 FROM @hw_v11_sql; EXECUTE hw_v11; DEALLOCATE PREPARE hw_v11;
ALTER TABLE `hw_course`.`quiz_question` ADD CONSTRAINT `ck_p2_quiz_question_answer` CHECK ((question_type IN ('FILL_BLANK','SHORT_ANSWER') AND CHAR_LENGTH(correct_option) BETWEEN 1 AND 4000) OR (question_type NOT IN ('FILL_BLANK','SHORT_ANSWER') AND correct_option IN ('A','B','C','D','AB','AC','AD','BC','BD','CD','ABC','ABD','ACD','BCD','ABCD')));
SET @hw_v11_sql = IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema='hw_learning' AND table_name='quiz_run_question' AND constraint_name='quiz_run_question_chk_1')>0,'ALTER TABLE `hw_learning`.`quiz_run_question` DROP CHECK `quiz_run_question_chk_1`','SELECT 1');
PREPARE hw_v11 FROM @hw_v11_sql; EXECUTE hw_v11; DEALLOCATE PREPARE hw_v11;
ALTER TABLE `hw_learning`.`quiz_run_question` ADD CONSTRAINT `quiz_run_question_chk_1` CHECK (question_type IN ('SINGLE','MULTIPLE','TRUE_FALSE','FILL_BLANK','SHORT_ANSWER'));
SET @hw_v11_sql = IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema='hw_learning' AND table_name='quiz_attempt_item' AND constraint_name='ck_p2_quiz_attempt_item_type')>0,'ALTER TABLE `hw_learning`.`quiz_attempt_item` DROP CHECK `ck_p2_quiz_attempt_item_type`','SELECT 1');
PREPARE hw_v11 FROM @hw_v11_sql; EXECUTE hw_v11; DEALLOCATE PREPARE hw_v11;
ALTER TABLE `hw_learning`.`quiz_attempt_item` ADD CONSTRAINT `ck_p2_quiz_attempt_item_type` CHECK (question_type IN ('SINGLE','MULTIPLE','TRUE_FALSE','FILL_BLANK','SHORT_ANSWER') AND points BETWEEN 1 AND 100);
SET @hw_v11_sql = IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema='hw_learning' AND table_name='quiz_attempt_item' AND constraint_name='ck_p2_quiz_attempt_item_answer')>0,'ALTER TABLE `hw_learning`.`quiz_attempt_item` DROP CHECK `ck_p2_quiz_attempt_item_answer`','SELECT 1');
PREPARE hw_v11 FROM @hw_v11_sql; EXECUTE hw_v11; DEALLOCATE PREPARE hw_v11;
ALTER TABLE `hw_learning`.`quiz_attempt_item` ADD CONSTRAINT `ck_p2_quiz_attempt_item_answer` CHECK ((question_type IN ('FILL_BLANK','SHORT_ANSWER') AND CHAR_LENGTH(correct_option) BETWEEN 1 AND 4000) OR (question_type NOT IN ('FILL_BLANK','SHORT_ANSWER') AND correct_option IN ('A','B','C','D','AB','AC','AD','BC','BD','CD','ABC','ABD','ACD','BCD','ABCD')));
SET @hw_v11_sql = IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema='hw_learning' AND table_name='quiz_attempt_item' AND constraint_name='ck_p2_attempt_selected')>0,'ALTER TABLE `hw_learning`.`quiz_attempt_item` DROP CHECK `ck_p2_attempt_selected`','SELECT 1');
PREPARE hw_v11 FROM @hw_v11_sql; EXECUTE hw_v11; DEALLOCATE PREPARE hw_v11;
ALTER TABLE `hw_learning`.`quiz_attempt_item` ADD CONSTRAINT `ck_p2_attempt_selected` CHECK ((question_type IN ('FILL_BLANK','SHORT_ANSWER') AND CHAR_LENGTH(selected_option) BETWEEN 1 AND 4000) OR (question_type NOT IN ('FILL_BLANK','SHORT_ANSWER') AND selected_option IN ('A','B','C','D','AB','AC','AD','BC','BD','CD','ABC','ABD','ACD','BCD','ABCD')));
