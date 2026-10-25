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

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `hw_course` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `hw_course`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `chapter_quiz` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `course_id` bigint unsigned NOT NULL,
  `chapter_id` bigint unsigned NOT NULL,
  `title` varchar(100) NOT NULL,
  `instructions` varchar(2000) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `version` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `opens_at` datetime(3) DEFAULT NULL,
  `closes_at` datetime(3) DEFAULT NULL,
  `time_limit_minutes` int NOT NULL DEFAULT '0',
  `max_attempts` int NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `idx_quiz_course` (`course_id`,`chapter_id`,`status`,`id`),
  KEY `chapter_id` (`chapter_id`),
  CONSTRAINT `chapter_quiz_ibfk_1` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chapter_quiz_ibfk_2` FOREIGN KEY (`chapter_id`) REFERENCES `course_chapter` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chapter_quiz_chk_1` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PUBLISHED',_utf8mb4'ARCHIVED'))),
  CONSTRAINT `chapter_quiz_chk_2` CHECK ((`version` >= 0)),
  CONSTRAINT `ck_p2_quiz_rules` CHECK (((`time_limit_minutes` between 0 and 180) and (`max_attempts` between 1 and 5) and ((`opens_at` is null) or (`closes_at` is null) or (`opens_at` < `closes_at`))))
) ENGINE=InnoDB AUTO_INCREMENT=76 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `course` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `teacher_id` bigint unsigned NOT NULL,
  `name` varchar(100) NOT NULL,
  `summary` text,
  `category` varchar(30) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_course_teacher_status` (`teacher_id`,`status`,`id`),
  KEY `idx_course_status` (`status`,`id`),
  CONSTRAINT `chk_course_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PUBLISHED',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB AUTO_INCREMENT=79 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `course_chapter` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `course_id` bigint unsigned NOT NULL,
  `title` varchar(100) NOT NULL,
  `content` longtext NOT NULL,
  `sort_no` int NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `lesson_document` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chapter_course_sort` (`course_id`,`sort_no`),
  CONSTRAINT `fk_chapter_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_chapter_sort` CHECK ((`sort_no` > 0))
) ENGINE=InnoDB AUTO_INCREMENT=108 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `course_notice` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `course_id` bigint unsigned NOT NULL,
  `author_id` bigint unsigned NOT NULL,
  `title` varchar(100) NOT NULL,
  `content` text NOT NULL,
  `pinned` tinyint(1) NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notice_course` (`course_id`,`pinned`,`id`),
  CONSTRAINT `course_notice_ibfk_1` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `course_notice_chk_1` CHECK ((`pinned` in (0,1))),
  CONSTRAINT `course_notice_chk_2` CHECK ((`version` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `course_question` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `course_id` bigint unsigned NOT NULL,
  `chapter_id` bigint unsigned DEFAULT NULL,
  `student_id` bigint unsigned NOT NULL,
  `title` varchar(100) NOT NULL,
  `content` text NOT NULL,
  `resolved` tinyint(1) NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_question_course` (`course_id`,`resolved`,`id`),
  KEY `chapter_id` (`chapter_id`),
  CONSTRAINT `course_question_ibfk_1` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `course_question_ibfk_2` FOREIGN KEY (`chapter_id`) REFERENCES `course_chapter` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `course_question_chk_1` CHECK ((`resolved` in (0,1))),
  CONSTRAINT `course_question_chk_2` CHECK ((`version` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `course_reply` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `question_id` bigint unsigned NOT NULL,
  `author_id` bigint unsigned NOT NULL,
  `role` varchar(16) NOT NULL,
  `content` text NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_reply_question` (`question_id`,`id`),
  CONSTRAINT `course_reply_ibfk_1` FOREIGN KEY (`question_id`) REFERENCES `course_question` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `course_reply_chk_1` CHECK ((`role` in (_utf8mb4'STUDENT',_utf8mb4'TEACHER')))
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `course_resource` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `course_id` bigint unsigned NOT NULL,
  `chapter_id` bigint unsigned DEFAULT NULL,
  `title` varchar(100) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `original_name` varchar(160) NOT NULL,
  `storage_name` varchar(60) DEFAULT NULL,
  `mime_type` varchar(80) NOT NULL,
  `bytes` bigint unsigned NOT NULL,
  `sha256` char(64) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(3) NOT NULL,
  `removed_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_resource_purge` (`status`,`removed_at`,`id`),
  KEY `idx_resource_course` (`course_id`,`chapter_id`,`status`,`id`),
  KEY `chapter_id` (`chapter_id`),
  CONSTRAINT `course_resource_ibfk_1` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `course_resource_ibfk_2` FOREIGN KEY (`chapter_id`) REFERENCES `course_chapter` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `course_resource_chk_1` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'REMOVED'))),
  CONSTRAINT `course_resource_chk_2` CHECK (((`bytes` > 0) and (`bytes` <= 20971520)))
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `course_team` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `course_id` bigint unsigned NOT NULL,
  `user_id` bigint unsigned NOT NULL,
  `role` varchar(16) NOT NULL,
  `status` varchar(16) NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_member` (`course_id`,`user_id`),
  KEY `idx_team_actor` (`user_id`,`status`,`course_id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `course_team_event` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `course_id` bigint unsigned NOT NULL,
  `actor_id` bigint unsigned NOT NULL,
  `target_id` bigint unsigned NOT NULL,
  `action` varchar(24) NOT NULL,
  `detail` varchar(1000) NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_team_event` (`course_id`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `course_team_state` (
  `id` bigint unsigned NOT NULL,
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `literature_work` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `teacher_id` bigint unsigned DEFAULT NULL,
  `seed_key` varchar(30) DEFAULT NULL,
  `title` varchar(100) NOT NULL,
  `author` varchar(50) NOT NULL,
  `dynasty` varchar(30) NOT NULL,
  `genre` varchar(30) NOT NULL,
  `content` longtext NOT NULL,
  `reading_guide` text,
  `source_url` varchar(500) DEFAULT NULL,
  `source_note` varchar(500) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PUBLISHED',
  `version` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `translation` text,
  `annotations_text` text,
  `allusions_json` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_seed` (`seed_key`),
  KEY `idx_work_status` (`status`,`dynasty`,`genre`,`id`),
  KEY `idx_work_teacher` (`teacher_id`,`id`),
  CONSTRAINT `literature_work_chk_1` CHECK ((`status` in (_utf8mb4'PUBLISHED',_utf8mb4'ARCHIVED'))),
  CONSTRAINT `literature_work_chk_2` CHECK ((`version` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `question_bank` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `teacher_id` bigint unsigned NOT NULL,
  `question_type` varchar(16) NOT NULL,
  `prompt` text NOT NULL,
  `option_a` varchar(500) NOT NULL,
  `option_b` varchar(500) NOT NULL,
  `option_c` varchar(500) NOT NULL,
  `option_d` varchar(500) NOT NULL,
  `correct_option` longtext NOT NULL,
  `explanation` varchar(2000) DEFAULT NULL,
  `points` int NOT NULL,
  `topic` varchar(60) DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  `version` int NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_bank_teacher` (`teacher_id`,`status`,`id`),
  CONSTRAINT `question_bank_chk_1` CHECK ((`question_type` in (_utf8mb4'SINGLE',_utf8mb4'MULTIPLE',_utf8mb4'TRUE_FALSE',_utf8mb4'FILL_BLANK',_utf8mb4'SHORT_ANSWER'))),
  CONSTRAINT `question_bank_chk_2` CHECK ((`points` between 1 and 100)),
  CONSTRAINT `question_bank_chk_3` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'ARCHIVED'))),
  CONSTRAINT `question_bank_chk_4` CHECK ((`version` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `quiz_question` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `quiz_id` bigint unsigned NOT NULL,
  `prompt` text NOT NULL,
  `option_a` varchar(500) NOT NULL,
  `option_b` varchar(500) NOT NULL,
  `option_c` varchar(500) NOT NULL,
  `option_d` varchar(500) NOT NULL,
  `correct_option` longtext NOT NULL,
  `explanation` varchar(2000) DEFAULT NULL,
  `sort_no` int NOT NULL,
  `question_type` varchar(16) NOT NULL DEFAULT 'SINGLE',
  `points` int NOT NULL DEFAULT '1',
  `bank_id` bigint unsigned DEFAULT NULL,
  `bank_version` int DEFAULT NULL,
  `knowledge_point` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quiz_sort` (`quiz_id`,`sort_no`),
  CONSTRAINT `quiz_question_ibfk_1` FOREIGN KEY (`quiz_id`) REFERENCES `chapter_quiz` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_p2_quiz_question_answer` CHECK ((((`question_type` in (_utf8mb4'FILL_BLANK',_utf8mb4'SHORT_ANSWER')) and (char_length(`correct_option`) between 1 and 4000)) or ((`question_type` not in (_utf8mb4'FILL_BLANK',_utf8mb4'SHORT_ANSWER')) and (`correct_option` in (_utf8mb4'A',_utf8mb4'B',_utf8mb4'C',_utf8mb4'D',_utf8mb4'AB',_utf8mb4'AC',_utf8mb4'AD',_utf8mb4'BC',_utf8mb4'BD',_utf8mb4'CD',_utf8mb4'ABC',_utf8mb4'ABD',_utf8mb4'ACD',_utf8mb4'BCD',_utf8mb4'ABCD'))))),
  CONSTRAINT `ck_p2_quiz_question_type` CHECK (((`question_type` in (_utf8mb4'SINGLE',_utf8mb4'MULTIPLE',_utf8mb4'TRUE_FALSE',_utf8mb4'FILL_BLANK',_utf8mb4'SHORT_ANSWER')) and (`points` between 1 and 100))),
  CONSTRAINT `quiz_question_chk_2` CHECK ((`sort_no` > 0))
) ENGINE=InnoDB AUTO_INCREMENT=133 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


INSERT INTO literature_work(seed_key,title,author,dynasty,genre,content,reading_guide,source_url,source_note,status,version,created_at,updated_at) SELECT 'HW-CLASSIC-1','静夜思','李白','唐','诗','床前明月光，
疑是地上霜。
举头望明月，
低头思故乡。','观察月光、霜与故乡如何连接眼前所见和内心情感。尝试用两句话说明诗中视线的变化。','https://zh.wikisource.org/w/index.php?title=靜夜思&oldid=8747585','采用页面所列《唐诗三百首》版本，繁体转简体，按诗句分行。','PUBLISHED',0,NOW(3),NOW(3) WHERE NOT EXISTS(SELECT 1 FROM literature_work WHERE seed_key='HW-CLASSIC-1');
INSERT INTO literature_work(seed_key,title,author,dynasty,genre,content,reading_guide,source_url,source_note,status,version,created_at,updated_at) SELECT 'HW-CLASSIC-2','春晓','孟浩然','唐','诗','春眠不觉晓，
处处闻啼鸟。
夜来风雨声，
花落知多少。','先找出听觉意象，再讨论末句为什么用疑问收束。注意页面记载的异文。','https://zh.wikisource.org/w/index.php?title=春曉_(孟浩然)&oldid=10374454','采用所列“夜来风雨声”版本，繁体转简体，按诗句分行。','PUBLISHED',0,NOW(3),NOW(3) WHERE NOT EXISTS(SELECT 1 FROM literature_work WHERE seed_key='HW-CLASSIC-2');
INSERT INTO literature_work(seed_key,title,author,dynasty,genre,content,reading_guide,source_url,source_note,status,version,created_at,updated_at) SELECT 'HW-CLASSIC-3','陋室铭','刘禹锡','唐','文','山不在高，有仙则名。水不在深，有龙则灵。斯是陋室，惟吾德馨。
苔痕上阶绿，草色入帘青。谈笑有鸿儒，往来无白丁。可以调素琴，阅金经。无丝竹之乱耳，无案牍之劳形。
南阳诸葛庐，西蜀子云亭。孔子云：何陋之有？','圈出对偶句，比较环境、交往和生活三组描写。思考题目中的“陋”与结尾的关系。','https://zh.wikisource.org/w/index.php?title=陋室銘&oldid=11122639','繁体转简体，保留原文措辞，按阅读节奏分段。','PUBLISHED',0,NOW(3),NOW(3) WHERE NOT EXISTS(SELECT 1 FROM literature_work WHERE seed_key='HW-CLASSIC-3');
INSERT INTO literature_work(seed_key,title,author,dynasty,genre,content,reading_guide,source_url,source_note,status,version,created_at,updated_at) SELECT 'HW-CLASSIC-4','爱莲说','周敦颐','宋','文','水陆草木之花，可爱者甚蕃。晋陶渊明独爱菊。自李唐来，世人盛爱牡丹。予独爱莲之出淤泥而不染，濯清涟而不妖，中通外直，不蔓不枝，香远益清，亭亭净植，可远观而不可亵玩焉。
予谓菊，花之隐逸者也；牡丹，花之富贵者也；莲，花之君子者也。噫！菊之爱，陶后鲜有闻。莲之爱，同予者何人？牡丹之爱，宜乎众矣。','比较菊、牡丹、莲的写法。把莲的形态描写与作者表达的品格联系起来，写出自己的文本依据。','https://zh.wikisource.org/w/index.php?title=愛蓮說&oldid=2676605','仅收录正文，不收录页面注释；繁体转简体，保留“盛爱”等页面版本措辞。','PUBLISHED',0,NOW(3),NOW(3) WHERE NOT EXISTS(SELECT 1 FROM literature_work WHERE seed_key='HW-CLASSIC-4');
