SET NAMES utf8mb4;
USE hw_assignment;
CREATE TABLE IF NOT EXISTS assignment_reminder(id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,assignment_id BIGINT UNSIGNED NOT NULL,student_id BIGINT UNSIGNED NOT NULL,teacher_id BIGINT UNSIGNED NOT NULL,content VARCHAR(500) NOT NULL,day_key DATE NOT NULL,created_at DATETIME(3) NOT NULL,UNIQUE KEY uk_reminder_daily(assignment_id,student_id,day_key),KEY idx_reminder_student(student_id,id),FOREIGN KEY(assignment_id) REFERENCES assignment_task(id)) ENGINE=InnoDB;
