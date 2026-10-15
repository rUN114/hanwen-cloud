SET NAMES utf8mb4;
USE hw_course;
DROP PROCEDURE IF EXISTS hw_v7_column;
DELIMITER $$
CREATE PROCEDURE hw_v7_column(IN t VARCHAR(64),IN c VARCHAR(64),IN definition TEXT)
BEGIN
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='hw_course' AND table_name=t AND column_name=c) THEN
 SET @ddl=CONCAT('ALTER TABLE `',t,'` ADD COLUMN `',c,'` ',definition);PREPARE hw_stmt FROM @ddl;EXECUTE hw_stmt;DEALLOCATE PREPARE hw_stmt;
 END IF;
END$$
DELIMITER ;
CALL hw_v7_column('course_chapter','lesson_document','TEXT NULL');
CALL hw_v7_column('literature_work','translation','TEXT NULL');
CALL hw_v7_column('literature_work','annotations_text','TEXT NULL');
CALL hw_v7_column('literature_work','allusions_json','TEXT NULL');
DROP PROCEDURE hw_v7_column;
USE hw_assignment;
CREATE TABLE IF NOT EXISTS assignment_attachment(id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,assignment_id BIGINT UNSIGNED NOT NULL,student_id BIGINT UNSIGNED NOT NULL,original_name VARCHAR(160) NOT NULL,mime_type VARCHAR(80) NOT NULL,bytes BIGINT UNSIGNED NOT NULL,sha256 CHAR(64) NOT NULL,storage_name VARCHAR(80) NOT NULL UNIQUE,status VARCHAR(16) NOT NULL,created_at DATETIME(3) NOT NULL,KEY idx_attachment_task_student(assignment_id,student_id,status),FOREIGN KEY(assignment_id) REFERENCES assignment_task(id),CHECK(bytes BETWEEN 1 AND 20971520),CHECK(status IN('ACTIVE','REMOVED'))) ENGINE=InnoDB;
