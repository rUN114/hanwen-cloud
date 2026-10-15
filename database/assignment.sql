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

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `hw_assignment` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `hw_assignment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `assignment_attachment` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `assignment_id` bigint unsigned NOT NULL,
  `student_id` bigint unsigned NOT NULL,
  `original_name` varchar(160) NOT NULL,
  `mime_type` varchar(80) NOT NULL,
  `bytes` bigint unsigned NOT NULL,
  `sha256` char(64) NOT NULL,
  `storage_name` varchar(80) NOT NULL,
  `status` varchar(16) NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `storage_name` (`storage_name`),
  KEY `idx_attachment_task_student` (`assignment_id`,`student_id`,`status`),
  CONSTRAINT `assignment_attachment_ibfk_1` FOREIGN KEY (`assignment_id`) REFERENCES `assignment_task` (`id`),
  CONSTRAINT `assignment_attachment_chk_1` CHECK ((`bytes` between 1 and 20971520)),
  CONSTRAINT `assignment_attachment_chk_2` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'REMOVED')))
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `assignment_draft` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `assignment_id` bigint unsigned NOT NULL,
  `student_id` bigint unsigned NOT NULL,
  `answer_text` text NOT NULL,
  `version` int NOT NULL DEFAULT '0',
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_draft_assignment_student` (`assignment_id`,`student_id`),
  CONSTRAINT `assignment_draft_ibfk_1` FOREIGN KEY (`assignment_id`) REFERENCES `assignment_task` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `assignment_draft_chk_1` CHECK ((`version` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `assignment_grade` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `submission_id` bigint unsigned NOT NULL,
  `teacher_id` bigint unsigned NOT NULL,
  `score` decimal(5,2) NOT NULL,
  `comment` text,
  `graded_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `rubric_scores` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_grade_submission` (`submission_id`),
  CONSTRAINT `fk_grade_submission` FOREIGN KEY (`submission_id`) REFERENCES `assignment_submission` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_grade_score` CHECK (((`score` >= 0) and (`score` <= 100)))
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `assignment_grade_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `submission_id` bigint unsigned NOT NULL,
  `teacher_id` bigint unsigned NOT NULL,
  `submission_version` int NOT NULL,
  `score` decimal(5,2) NOT NULL,
  `comment` text,
  `graded_at` datetime(3) NOT NULL,
  `rubric_scores` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_history_submission_version` (`submission_id`,`submission_version`),
  CONSTRAINT `assignment_grade_history_ibfk_1` FOREIGN KEY (`submission_id`) REFERENCES `assignment_submission` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `assignment_grade_history_chk_1` CHECK (((`score` >= 0) and (`score` <= 100))),
  CONSTRAINT `assignment_grade_history_chk_2` CHECK ((`submission_version` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `assignment_reminder` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `assignment_id` bigint unsigned NOT NULL,
  `student_id` bigint unsigned NOT NULL,
  `teacher_id` bigint unsigned NOT NULL,
  `content` varchar(500) NOT NULL,
  `day_key` date NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reminder_daily` (`assignment_id`,`student_id`,`day_key`),
  KEY `idx_reminder_student` (`student_id`,`id`),
  CONSTRAINT `assignment_reminder_ibfk_1` FOREIGN KEY (`assignment_id`) REFERENCES `assignment_task` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `assignment_submission` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `assignment_id` bigint unsigned NOT NULL,
  `student_id` bigint unsigned NOT NULL,
  `answer_text` text NOT NULL,
  `submitted_at` datetime(3) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'SUBMITTED',
  `version` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_submission_assignment_student` (`assignment_id`,`student_id`),
  KEY `idx_submission_student` (`student_id`,`id`),
  KEY `idx_submission_task_status` (`assignment_id`,`status`,`id`),
  CONSTRAINT `fk_submission_task` FOREIGN KEY (`assignment_id`) REFERENCES `assignment_task` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_submission_status` CHECK ((`status` in (_utf8mb4'SUBMITTED',_utf8mb4'GRADED'))),
  CONSTRAINT `chk_submission_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=42 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `assignment_task` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `course_id` bigint unsigned NOT NULL,
  `teacher_id` bigint unsigned NOT NULL,
  `title` varchar(100) NOT NULL,
  `description` text NOT NULL,
  `max_score` decimal(5,2) NOT NULL,
  `deadline_at` datetime(3) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `rubric_json` text,
  PRIMARY KEY (`id`),
  KEY `idx_task_course_status` (`course_id`,`status`,`id`),
  KEY `idx_task_teacher` (`teacher_id`,`id`),
  CONSTRAINT `chk_task_max` CHECK (((`max_score` > 0) and (`max_score` <= 100))),
  CONSTRAINT `chk_task_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PUBLISHED',_utf8mb4'CLOSED')))
) ENGINE=InnoDB AUTO_INCREMENT=73 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `course_grade_policy` (
  `id` bigint unsigned NOT NULL,
  `assignment_weight` int NOT NULL,
  `missing_rule` varchar(16) NOT NULL,
  `attempt_rule` varchar(16) NOT NULL,
  `version` int NOT NULL DEFAULT '0',
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `course_grade_policy_chk_1` CHECK ((`assignment_weight` between 0 and 100)),
  CONSTRAINT `course_grade_policy_chk_2` CHECK ((`missing_rule` in (_utf8mb4'ZERO',_utf8mb4'EXCLUDE'))),
  CONSTRAINT `course_grade_policy_chk_3` CHECK ((`attempt_rule` in (_utf8mb4'HIGHEST',_utf8mb4'LATEST'))),
  CONSTRAINT `course_grade_policy_chk_4` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `submission_revision` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `submission_id` bigint unsigned NOT NULL,
  `student_id` bigint unsigned NOT NULL,
  `teacher_id` bigint unsigned NOT NULL,
  `base_submission_version` int NOT NULL,
  `request_text` varchar(2000) NOT NULL,
  `answer_text` longtext,
  `student_note` varchar(1000) DEFAULT NULL,
  `teacher_comment` varchar(2000) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'OPEN',
  `version` int NOT NULL DEFAULT '0',
  `opened_at` datetime(3) NOT NULL,
  `submitted_at` datetime(3) DEFAULT NULL,
  `reviewed_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_revision_submission` (`submission_id`,`id`),
  KEY `idx_revision_student` (`student_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
