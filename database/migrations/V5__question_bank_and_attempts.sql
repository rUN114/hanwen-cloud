-- P2 additive migration. Back up all four databases before applying.
USE hw_course;
DROP PROCEDURE IF EXISTS p2_column;
DROP PROCEDURE IF EXISTS p2_option_checks;
DELIMITER $$
CREATE PROCEDURE p2_column(IN dbn VARCHAR(64), IN tn VARCHAR(64), IN cn VARCHAR(64), IN definition TEXT)
BEGIN
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=dbn AND table_name=tn AND column_name=cn) THEN
  SET @p2_sql=CONCAT('ALTER TABLE `',dbn,'`.`',tn,'` ADD COLUMN `',cn,'` ',definition);
  PREPARE p2_stmt FROM @p2_sql; EXECUTE p2_stmt; DEALLOCATE PREPARE p2_stmt;
 END IF;
END$$
CREATE PROCEDURE p2_option_checks(IN dbn VARCHAR(64), IN tn VARCHAR(64))
BEGIN
 DECLARE done INT DEFAULT 0;
 DECLARE n VARCHAR(64);
 DECLARE checks CURSOR FOR SELECT tc.constraint_name FROM information_schema.table_constraints tc
 JOIN information_schema.check_constraints cc ON tc.constraint_schema=cc.constraint_schema AND tc.constraint_name=cc.constraint_name
 WHERE tc.table_schema=dbn AND tc.table_name=tn AND (cc.check_clause LIKE '%correct_option%' OR cc.check_clause LIKE '%selected_option%');
 DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=1;
 OPEN checks;
 repeat_checks: LOOP
  FETCH checks INTO n;
  IF done=1 THEN LEAVE repeat_checks; END IF;
  SET @p2_sql=CONCAT('ALTER TABLE `',dbn,'`.`',tn,'` DROP CHECK `',n,'`');
  PREPARE p2_stmt FROM @p2_sql; EXECUTE p2_stmt; DEALLOCATE PREPARE p2_stmt;
 END LOOP;
 CLOSE checks;
END$$
DELIMITER ;
CALL hw_course.p2_column('hw_course','chapter_quiz','opens_at','DATETIME(3) NULL');
CALL hw_course.p2_column('hw_course','chapter_quiz','closes_at','DATETIME(3) NULL');
CALL hw_course.p2_column('hw_course','chapter_quiz','time_limit_minutes','INT NOT NULL DEFAULT 0');
CALL hw_course.p2_column('hw_course','chapter_quiz','max_attempts','INT NOT NULL DEFAULT 1');
CALL hw_course.p2_column('hw_course','quiz_question','question_type','VARCHAR(16) NOT NULL DEFAULT ''SINGLE''');
CALL hw_course.p2_column('hw_course','quiz_question','points','INT NOT NULL DEFAULT 1');
CALL hw_course.p2_column('hw_course','quiz_question','bank_id','BIGINT UNSIGNED NULL');
CALL hw_course.p2_column('hw_course','quiz_question','bank_version','INT NULL');
CALL hw_course.p2_column('hw_learning','quiz_attempt','run_id','BIGINT UNSIGNED NULL');
CALL hw_course.p2_column('hw_learning','quiz_attempt','attempt_no','INT NOT NULL DEFAULT 1');
CALL hw_course.p2_column('hw_learning','quiz_attempt','quiz_version','INT NULL');
CALL hw_course.p2_column('hw_learning','quiz_attempt_item','question_type','VARCHAR(16) NOT NULL DEFAULT ''SINGLE''');
CALL hw_course.p2_column('hw_learning','quiz_attempt_item','points','INT NOT NULL DEFAULT 1');
CALL hw_course.p2_option_checks('hw_course','quiz_question');
ALTER TABLE `hw_course`.`quiz_question` MODIFY `correct_option` VARCHAR(4) NOT NULL;
CALL hw_course.p2_option_checks('hw_learning','quiz_attempt_item');
ALTER TABLE `hw_learning`.`quiz_attempt_item` MODIFY `correct_option` VARCHAR(4) NOT NULL;
ALTER TABLE `hw_learning`.`quiz_attempt_item` MODIFY `selected_option` VARCHAR(4) NOT NULL;
DROP PROCEDURE hw_course.p2_column;
DROP PROCEDURE hw_course.p2_option_checks;
USE hw_course;
CREATE TABLE IF NOT EXISTS question_bank (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,teacher_id BIGINT UNSIGNED NOT NULL,
 question_type VARCHAR(16) NOT NULL,prompt TEXT NOT NULL,option_a VARCHAR(500) NOT NULL,option_b VARCHAR(500) NOT NULL,
 option_c VARCHAR(500) NOT NULL,option_d VARCHAR(500) NOT NULL,correct_option VARCHAR(4) NOT NULL,explanation VARCHAR(2000) NULL,
 points INT NOT NULL,topic VARCHAR(60) NULL,status VARCHAR(16) NOT NULL,version INT NOT NULL,
 created_at DATETIME(3) NOT NULL,updated_at DATETIME(3) NOT NULL,
 KEY ix_bank_teacher(teacher_id,status,id),CHECK(question_type IN ('SINGLE','MULTIPLE','TRUE_FALSE')),
 CHECK(points BETWEEN 1 AND 100),CHECK(status IN ('ACTIVE','ARCHIVED')),CHECK(version>=0)
) ENGINE=InnoDB;
USE hw_learning;
CREATE TABLE IF NOT EXISTS quiz_run (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,quiz_id BIGINT UNSIGNED NOT NULL,course_id BIGINT UNSIGNED NOT NULL,
 chapter_id BIGINT UNSIGNED NOT NULL,student_id BIGINT UNSIGNED NOT NULL,quiz_version INT NOT NULL,
 attempt_no INT NOT NULL,max_attempts INT NOT NULL,status VARCHAR(16) NOT NULL,started_at DATETIME(3) NOT NULL,
 deadline_at DATETIME(3) NULL,version INT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_run_student_quiz_no(student_id,quiz_id,attempt_no),KEY ix_run_course(course_id,student_id),
 CHECK(status IN ('ACTIVE','SUBMITTED','EXPIRED')),CHECK(attempt_no>=1),CHECK(max_attempts BETWEEN 1 AND 5)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS quiz_run_question (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,run_id BIGINT UNSIGNED NOT NULL,question_id BIGINT UNSIGNED NOT NULL,
 prompt TEXT NOT NULL,option_a VARCHAR(500) NOT NULL,option_b VARCHAR(500) NOT NULL,option_c VARCHAR(500) NOT NULL,
 option_d VARCHAR(500) NOT NULL,correct_option VARCHAR(4) NOT NULL,explanation VARCHAR(2000) NULL,
 question_type VARCHAR(16) NOT NULL,points INT NOT NULL,selected_option VARCHAR(4) NULL,
 UNIQUE KEY uk_run_question(run_id,question_id),FOREIGN KEY(run_id) REFERENCES quiz_run(id) ON DELETE RESTRICT,
 CHECK(question_type IN ('SINGLE','MULTIPLE','TRUE_FALSE')),CHECK(points BETWEEN 1 AND 100)
) ENGINE=InnoDB;
-- Replace the previous single-submission uniqueness with run uniqueness; legacy run_id remains NULL.
DROP PROCEDURE IF EXISTS p2_attempt_index;
DELIMITER $$
CREATE PROCEDURE p2_attempt_index()
BEGIN
 IF EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema='hw_learning' AND table_name='quiz_attempt' AND index_name='uk_attempt_student_quiz') THEN
  ALTER TABLE quiz_attempt DROP INDEX uk_attempt_student_quiz;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema='hw_learning' AND table_name='quiz_attempt' AND index_name='uk_attempt_run') THEN
  ALTER TABLE quiz_attempt ADD UNIQUE KEY uk_attempt_run(run_id);
 END IF;
END$$
DELIMITER ;
CALL p2_attempt_index();
DROP PROCEDURE p2_attempt_index;
USE hw_course;
DROP PROCEDURE IF EXISTS p2_check;
DELIMITER $$
CREATE PROCEDURE p2_check(IN dbn VARCHAR(64), IN tn VARCHAR(64), IN cn VARCHAR(64), IN expression TEXT)
BEGIN
 IF NOT EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE table_schema=dbn AND table_name=tn AND constraint_name=cn) THEN
  SET @p2_sql=CONCAT('ALTER TABLE `',dbn,'`.`',tn,'` ADD CONSTRAINT `',cn,'` CHECK (',expression,')');
  PREPARE p2_stmt FROM @p2_sql; EXECUTE p2_stmt; DEALLOCATE PREPARE p2_stmt;
 END IF;
END$$
DELIMITER ;
CALL hw_course.p2_check('hw_course','chapter_quiz','ck_p2_quiz_rules','time_limit_minutes BETWEEN 0 AND 180 AND max_attempts BETWEEN 1 AND 5 AND (opens_at IS NULL OR closes_at IS NULL OR opens_at<closes_at)');
CALL hw_course.p2_check('hw_course','quiz_question','ck_p2_quiz_question_type','question_type IN (''SINGLE'',''MULTIPLE'',''TRUE_FALSE'') AND points BETWEEN 1 AND 100');
CALL hw_course.p2_check('hw_course','quiz_question','ck_p2_quiz_question_answer','correct_option IN (''A'',''B'',''C'',''D'',''AB'',''AC'',''AD'',''BC'',''BD'',''CD'',''ABC'',''ABD'',''ACD'',''BCD'',''ABCD'')');
CALL hw_course.p2_check('hw_learning','quiz_attempt_item','ck_p2_quiz_attempt_item_type','question_type IN (''SINGLE'',''MULTIPLE'',''TRUE_FALSE'') AND points BETWEEN 1 AND 100');
CALL hw_course.p2_check('hw_learning','quiz_attempt_item','ck_p2_quiz_attempt_item_answer','correct_option IN (''A'',''B'',''C'',''D'',''AB'',''AC'',''AD'',''BC'',''BD'',''CD'',''ABC'',''ABD'',''ACD'',''BCD'',''ABCD'')');
CALL hw_course.p2_check('hw_learning','quiz_attempt_item','ck_p2_attempt_selected','selected_option IN (''A'',''B'',''C'',''D'',''AB'',''AC'',''AD'',''BC'',''BD'',''CD'',''ABC'',''ABD'',''ACD'',''BCD'',''ABCD'')');
DROP PROCEDURE hw_course.p2_check;
