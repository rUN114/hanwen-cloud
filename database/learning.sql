-- V7 fresh schema only; existing databases require additive migrations.

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `hw_learning` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `hw_learning`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `class_member` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `class_id` bigint unsigned NOT NULL,
  `student_id` bigint unsigned NOT NULL,
  `enrollment_id` bigint unsigned NOT NULL,
  `status` varchar(10) NOT NULL DEFAULT 'ACTIVE',
  `joined_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_student` (`class_id`,`student_id`),
  KEY `idx_member_student` (`student_id`,`id`),
  KEY `enrollment_id` (`enrollment_id`),
  CONSTRAINT `class_member_ibfk_1` FOREIGN KEY (`class_id`) REFERENCES `teaching_class` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `class_member_ibfk_2` FOREIGN KEY (`enrollment_id`) REFERENCES `course_enrollment` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `class_member_chk_1` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'LEFT')))
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `course_enrollment` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `course_id` bigint unsigned NOT NULL,
  `student_id` bigint unsigned NOT NULL,
  `joined_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_enrollment_course_student` (`course_id`,`student_id`),
  KEY `idx_enrollment_student` (`student_id`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=157 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `inbox_receipt` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `event_key` varchar(180) NOT NULL,
  `read_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_receipt_user_event` (`user_id`,`event_key`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `learning_note` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `student_id` bigint unsigned NOT NULL,
  `enrollment_id` bigint unsigned NOT NULL,
  `chapter_id` bigint unsigned NOT NULL,
  `title` varchar(100) NOT NULL,
  `content` text NOT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_note_student` (`student_id`,`enrollment_id`,`chapter_id`,`id`),
  KEY `enrollment_id` (`enrollment_id`),
  CONSTRAINT `learning_note_ibfk_1` FOREIGN KEY (`enrollment_id`) REFERENCES `course_enrollment` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `learning_note_chk_1` CHECK ((`version` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `learning_progress` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `enrollment_id` bigint unsigned NOT NULL,
  `chapter_id` bigint unsigned NOT NULL,
  `completed` tinyint(1) NOT NULL DEFAULT '0',
  `completed_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_progress_enrollment_chapter` (`enrollment_id`,`chapter_id`),
  CONSTRAINT `fk_progress_enrollment` FOREIGN KEY (`enrollment_id`) REFERENCES `course_enrollment` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_progress_completed` CHECK (((`completed` in (0,1)) and (((`completed` = 1) and (`completed_at` is not null)) or ((`completed` = 0) and (`completed_at` is null)))))
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `quiz_attempt` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `quiz_id` bigint unsigned NOT NULL,
  `course_id` bigint unsigned NOT NULL,
  `chapter_id` bigint unsigned NOT NULL,
  `student_id` bigint unsigned NOT NULL,
  `score` int NOT NULL,
  `total` int NOT NULL,
  `completed_at` datetime(3) NOT NULL,
  `run_id` bigint unsigned DEFAULT NULL,
  `attempt_no` int NOT NULL DEFAULT '1',
  `quiz_version` int DEFAULT NULL,
  `grading_status` varchar(16) NOT NULL DEFAULT 'GRADED',
  `grading_version` int NOT NULL DEFAULT '0',
  `grader_id` bigint unsigned DEFAULT NULL,
  `graded_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attempt_run` (`run_id`),
  KEY `idx_attempt_course` (`course_id`,`student_id`,`id`),
  CONSTRAINT `quiz_attempt_chk_1` CHECK (((`total` > 0) and (`score` >= 0) and (`score` <= `total`)))
) ENGINE=InnoDB AUTO_INCREMENT=65 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `quiz_attempt_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `attempt_id` bigint unsigned NOT NULL,
  `question_id` bigint unsigned NOT NULL,
  `prompt` text NOT NULL,
  `option_a` varchar(500) NOT NULL,
  `option_b` varchar(500) NOT NULL,
  `option_c` varchar(500) NOT NULL,
  `option_d` varchar(500) NOT NULL,
  `selected_option` longtext NOT NULL,
  `correct_option` longtext NOT NULL,
  `explanation` varchar(2000) DEFAULT NULL,
  `correct` tinyint(1) DEFAULT NULL,
  `question_type` varchar(16) NOT NULL DEFAULT 'SINGLE',
  `points` int NOT NULL DEFAULT '1',
  `knowledge_point` varchar(60) DEFAULT NULL,
  `awarded_points` int DEFAULT NULL,
  `teacher_comment` varchar(2000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attempt_question` (`attempt_id`,`question_id`),
  CONSTRAINT `quiz_attempt_item_ibfk_1` FOREIGN KEY (`attempt_id`) REFERENCES `quiz_attempt` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_p2_attempt_selected` CHECK ((((`question_type` in (_utf8mb4'FILL_BLANK',_utf8mb4'SHORT_ANSWER')) and (char_length(`selected_option`) between 1 and 4000)) or ((`question_type` not in (_utf8mb4'FILL_BLANK',_utf8mb4'SHORT_ANSWER')) and (`selected_option` in (_utf8mb4'A',_utf8mb4'B',_utf8mb4'C',_utf8mb4'D',_utf8mb4'AB',_utf8mb4'AC',_utf8mb4'AD',_utf8mb4'BC',_utf8mb4'BD',_utf8mb4'CD',_utf8mb4'ABC',_utf8mb4'ABD',_utf8mb4'ACD',_utf8mb4'BCD',_utf8mb4'ABCD'))))),
  CONSTRAINT `ck_p2_quiz_attempt_item_answer` CHECK ((((`question_type` in (_utf8mb4'FILL_BLANK',_utf8mb4'SHORT_ANSWER')) and (char_length(`correct_option`) between 1 and 4000)) or ((`question_type` not in (_utf8mb4'FILL_BLANK',_utf8mb4'SHORT_ANSWER')) and (`correct_option` in (_utf8mb4'A',_utf8mb4'B',_utf8mb4'C',_utf8mb4'D',_utf8mb4'AB',_utf8mb4'AC',_utf8mb4'AD',_utf8mb4'BC',_utf8mb4'BD',_utf8mb4'CD',_utf8mb4'ABC',_utf8mb4'ABD',_utf8mb4'ACD',_utf8mb4'BCD',_utf8mb4'ABCD'))))),
  CONSTRAINT `ck_p2_quiz_attempt_item_type` CHECK (((`question_type` in (_utf8mb4'SINGLE',_utf8mb4'MULTIPLE',_utf8mb4'TRUE_FALSE',_utf8mb4'FILL_BLANK',_utf8mb4'SHORT_ANSWER')) and (`points` between 1 and 100))),
  CONSTRAINT `quiz_attempt_item_chk_3` CHECK ((`correct` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=89 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `quiz_mark_event` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `attempt_id` bigint unsigned NOT NULL,
  `teacher_id` bigint unsigned NOT NULL,
  `version` int NOT NULL,
  `score` int NOT NULL,
  `items_json` longtext NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mark_version` (`attempt_id`,`version`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `quiz_run` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `quiz_id` bigint unsigned NOT NULL,
  `course_id` bigint unsigned NOT NULL,
  `chapter_id` bigint unsigned NOT NULL,
  `student_id` bigint unsigned NOT NULL,
  `quiz_version` int NOT NULL,
  `attempt_no` int NOT NULL,
  `max_attempts` int NOT NULL,
  `status` varchar(16) NOT NULL,
  `started_at` datetime(3) NOT NULL,
  `deadline_at` datetime(3) DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_student_quiz_no` (`student_id`,`quiz_id`,`attempt_no`),
  KEY `ix_run_course` (`course_id`,`student_id`),
  CONSTRAINT `quiz_run_chk_1` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'SUBMITTED',_utf8mb4'EXPIRED'))),
  CONSTRAINT `quiz_run_chk_2` CHECK ((`attempt_no` >= 1)),
  CONSTRAINT `quiz_run_chk_3` CHECK ((`max_attempts` between 1 and 5))
) ENGINE=InnoDB AUTO_INCREMENT=51 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `quiz_run_question` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `run_id` bigint unsigned NOT NULL,
  `question_id` bigint unsigned NOT NULL,
  `prompt` text NOT NULL,
  `option_a` varchar(500) NOT NULL,
  `option_b` varchar(500) NOT NULL,
  `option_c` varchar(500) NOT NULL,
  `option_d` varchar(500) NOT NULL,
  `correct_option` longtext NOT NULL,
  `explanation` varchar(2000) DEFAULT NULL,
  `question_type` varchar(16) NOT NULL,
  `points` int NOT NULL,
  `selected_option` longtext,
  `knowledge_point` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_question` (`run_id`,`question_id`),
  CONSTRAINT `quiz_run_question_ibfk_1` FOREIGN KEY (`run_id`) REFERENCES `quiz_run` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `quiz_run_question_chk_1` CHECK ((`question_type` in (_utf8mb4'SINGLE',_utf8mb4'MULTIPLE',_utf8mb4'TRUE_FALSE',_utf8mb4'FILL_BLANK',_utf8mb4'SHORT_ANSWER'))),
  CONSTRAINT `quiz_run_question_chk_2` CHECK ((`points` between 1 and 100))
) ENGINE=InnoDB AUTO_INCREMENT=91 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `reading_annotation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `student_id` bigint unsigned NOT NULL,
  `enrollment_id` bigint unsigned NOT NULL,
  `chapter_id` bigint unsigned NOT NULL,
  `start_offset` int NOT NULL,
  `end_offset` int NOT NULL,
  `quote_text` varchar(500) NOT NULL,
  `source_hash` char(64) NOT NULL,
  `comment` varchar(1000) NOT NULL,
  `color` varchar(10) NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_annotation_owner` (`student_id`,`enrollment_id`,`chapter_id`,`id`),
  KEY `enrollment_id` (`enrollment_id`),
  CONSTRAINT `reading_annotation_ibfk_1` FOREIGN KEY (`enrollment_id`) REFERENCES `course_enrollment` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `reading_annotation_chk_1` CHECK (((`start_offset` >= 0) and (`end_offset` > `start_offset`))),
  CONSTRAINT `reading_annotation_chk_2` CHECK ((`color` in (_utf8mb4'RED',_utf8mb4'PINE',_utf8mb4'GOLD')))
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `review_card` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `student_id` bigint unsigned NOT NULL,
  `course_id` bigint unsigned NOT NULL,
  `quiz_id` bigint unsigned NOT NULL,
  `question_id` bigint unsigned NOT NULL,
  `quiz_version` int NOT NULL,
  `attempt_item_id` bigint unsigned NOT NULL,
  `wrong_count` int NOT NULL DEFAULT '1',
  `review_count` int NOT NULL DEFAULT '0',
  `correct_streak` int NOT NULL DEFAULT '0',
  `mastered` tinyint(1) NOT NULL DEFAULT '0',
  `last_wrong_at` datetime(3) NOT NULL,
  `last_reviewed_at` datetime(3) DEFAULT NULL,
  `next_review_at` datetime(3) NOT NULL,
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_identity` (`student_id`,`quiz_id`,`question_id`,`quiz_version`),
  KEY `idx_review_due` (`student_id`,`mastered`,`next_review_at`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `teaching_class` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `course_id` bigint unsigned NOT NULL,
  `teacher_id` bigint unsigned NOT NULL,
  `name` varchar(100) NOT NULL,
  `semester` varchar(40) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `join_code` varchar(24) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `version` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_code` (`join_code`),
  KEY `idx_class_teacher` (`teacher_id`,`status`,`id`),
  CONSTRAINT `teaching_class_chk_1` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'CLOSED'))),
  CONSTRAINT `teaching_class_chk_2` CHECK ((`version` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `work_favorite` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `student_id` bigint unsigned NOT NULL,
  `work_id` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_student_work` (`student_id`,`work_id`)
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
