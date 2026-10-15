-- Additive and repeatable; existing scores remain unchanged.
SET NAMES utf8mb4;
USE hw_assignment;
DROP PROCEDURE IF EXISTS hw_v6_column;
DELIMITER $$
CREATE PROCEDURE hw_v6_column(IN t VARCHAR(64),IN c VARCHAR(64),IN definition TEXT)
BEGIN
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='hw_assignment' AND table_name=t AND column_name=c) THEN
 SET @ddl=CONCAT('ALTER TABLE `',t,'` ADD COLUMN `',c,'` ',definition);PREPARE hw_stmt FROM @ddl;EXECUTE hw_stmt;DEALLOCATE PREPARE hw_stmt;
 END IF;
END$$
DELIMITER ;
CALL hw_v6_column('assignment_task','rubric_json','TEXT NULL');
CALL hw_v6_column('assignment_grade','rubric_scores','TEXT NULL');
CALL hw_v6_column('assignment_grade_history','rubric_scores','TEXT NULL');
DROP PROCEDURE hw_v6_column;
CREATE TABLE IF NOT EXISTS course_grade_policy(id BIGINT UNSIGNED PRIMARY KEY,assignment_weight INT NOT NULL,missing_rule VARCHAR(16) NOT NULL,attempt_rule VARCHAR(16) NOT NULL,version INT NOT NULL DEFAULT 0,updated_at DATETIME(3) NOT NULL,CHECK(assignment_weight BETWEEN 0 AND 100),CHECK(missing_rule IN('ZERO','EXCLUDE')),CHECK(attempt_rule IN('HIGHEST','LATEST')),CHECK(version>=0)) ENGINE=InnoDB;
