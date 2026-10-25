SET NAMES utf8mb4;
USE hw_user;
SET @hw_sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='hw_user' AND table_name='sys_user' AND column_name='email')=0,'ALTER TABLE sys_user ADD COLUMN email VARCHAR(120) NULL','SELECT 1');
PREPARE hw_stmt FROM @hw_sql;EXECUTE hw_stmt;DEALLOCATE PREPARE hw_stmt;
SET @hw_sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='hw_user' AND table_name='sys_user' AND column_name='biography')=0,'ALTER TABLE sys_user ADD COLUMN biography VARCHAR(500) NULL','SELECT 1');
PREPARE hw_stmt FROM @hw_sql;EXECUTE hw_stmt;DEALLOCATE PREPARE hw_stmt;

USE hw_course;
CREATE TABLE IF NOT EXISTS course_notice(
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, course_id BIGINT UNSIGNED NOT NULL, author_id BIGINT UNSIGNED NOT NULL,
 title VARCHAR(100) NOT NULL, content TEXT NOT NULL, pinned BOOLEAN NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 KEY idx_notice_course(course_id,pinned,id), FOREIGN KEY(course_id) REFERENCES course(id) ON DELETE RESTRICT,
 CHECK(pinned IN(0,1)),CHECK(version>=0)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS course_question(
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,course_id BIGINT UNSIGNED NOT NULL,chapter_id BIGINT UNSIGNED NULL,student_id BIGINT UNSIGNED NOT NULL,
 title VARCHAR(100) NOT NULL,content TEXT NOT NULL,resolved BOOLEAN NOT NULL DEFAULT 0,version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL,updated_at DATETIME(3) NOT NULL,
 KEY idx_question_course(course_id,resolved,id),FOREIGN KEY(course_id) REFERENCES course(id) ON DELETE RESTRICT,
 FOREIGN KEY(chapter_id) REFERENCES course_chapter(id) ON DELETE RESTRICT,CHECK(resolved IN(0,1)),CHECK(version>=0)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS course_reply(
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,question_id BIGINT UNSIGNED NOT NULL,author_id BIGINT UNSIGNED NOT NULL,
 role VARCHAR(16) NOT NULL,content TEXT NOT NULL,created_at DATETIME(3) NOT NULL,
 KEY idx_reply_question(question_id,id),FOREIGN KEY(question_id) REFERENCES course_question(id) ON DELETE RESTRICT,CHECK(role IN('STUDENT','TEACHER'))
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS literature_work(
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,teacher_id BIGINT UNSIGNED NULL,seed_key VARCHAR(30) NULL,
 title VARCHAR(100) NOT NULL,author VARCHAR(50) NOT NULL,dynasty VARCHAR(30) NOT NULL,genre VARCHAR(30) NOT NULL,
 content LONGTEXT NOT NULL,reading_guide TEXT NULL,source_url VARCHAR(500) NULL,source_note VARCHAR(500) NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',version INT NOT NULL DEFAULT 0,created_at DATETIME(3) NOT NULL,updated_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_work_seed(seed_key),KEY idx_work_status(status,dynasty,genre,id),KEY idx_work_teacher(teacher_id,id),
 CHECK(status IN('PUBLISHED','ARCHIVED')),CHECK(version>=0)
) ENGINE=InnoDB;

USE hw_learning;
CREATE TABLE IF NOT EXISTS learning_note(
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,student_id BIGINT UNSIGNED NOT NULL,enrollment_id BIGINT UNSIGNED NOT NULL,
 chapter_id BIGINT UNSIGNED NOT NULL,title VARCHAR(100) NOT NULL,content TEXT NOT NULL,version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL,updated_at DATETIME(3) NOT NULL,
 KEY idx_note_student(student_id,enrollment_id,chapter_id,id),FOREIGN KEY(enrollment_id) REFERENCES course_enrollment(id) ON DELETE RESTRICT,CHECK(version>=0)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS work_favorite(
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,student_id BIGINT UNSIGNED NOT NULL,work_id BIGINT UNSIGNED NOT NULL,
 created_at DATETIME(3) NOT NULL,UNIQUE KEY uk_favorite_student_work(student_id,work_id)
) ENGINE=InnoDB;
USE hw_course;
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
