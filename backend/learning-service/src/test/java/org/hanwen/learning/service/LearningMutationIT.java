package org.hanwen.learning.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;

import org.apache.ibatis.session.SqlSessionFactory;
import org.hanwen.common.*;
import org.hanwen.learning.client.CourseClient;
import org.hanwen.learning.mapper.*;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import javax.sql.DataSource;

/** 仅连接显式指定目录的独立测试库，验证锁、回滚及远程调用边界。 */
@SpringJUnitConfig(LearningMutationIT.Config.class)
class LearningMutationIT {
    @Configuration(proxyBeanMethods = false)
    @MapperScan(basePackageClasses = QuizAttemptMapper.class)
    @Import({
        ClassroomService.class,
        PracticeMarkService.class,
        PracticeService.class,
        ReviewService.class
    })
    static class Config {
        @Bean
        DataSource dataSource() throws Exception {
            String url = System.getProperty("hanwen.test.jdbcUrl", "");
            String expected = System.getProperty("hanwen.test.dataDir", "");
            if (!url.startsWith("jdbc:mysql://127.0.0.1:14327/hw_learning?") || expected.isBlank())
                throw new IllegalArgumentException("Explicit isolated database required");
            var ds = new DriverManagerDataSource(url, "root", "");
            try (var c = ds.getConnection();
                    var s = c.createStatement();
                    var row = s.executeQuery("SELECT @@datadir")) {
                row.next();
                if (!Path.of(row.getString(1))
                        .toAbsolutePath()
                        .normalize()
                        .equals(Path.of(expected).toAbsolutePath().normalize()))
                    throw new IllegalArgumentException("Refusing non-test data directory");
            }
            return ds;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            var f = new MybatisSqlSessionFactoryBean();
            f.setDataSource(ds);
            return f.getObject();
        }

        @Bean
        PlatformTransactionManager manager(DataSource ds) {
            return new DataSourceTransactionManager(ds);
        }

        @Bean
        JdbcTemplate jdbc(DataSource ds) {
            return new JdbcTemplate(ds);
        }

        @Bean
        CourseClient courses() {
            return mock(CourseClient.class);
        }

        @Bean
        DirectoryClient directory() {
            return mock(DirectoryClient.class);
        }

        @Bean
        Remote remote() {
            return new Remote();
        }
    }

    @Autowired JdbcTemplate db;
    @Autowired CourseClient courses;
    @Autowired DirectoryClient directory;
    @Autowired ClassroomService classrooms;
    @Autowired PracticeMarkService marks;
    @Autowired ReviewService reviews;
    @Autowired PracticeService practice;

    static void outsideTransaction() {
        assertFalse(
                TransactionSynchronizationManager.isActualTransactionActive(),
                "RPC must precede local writes and locks");
    }

    @BeforeEach
    void setup() {
        Actor.set(new Actor(1, "TEACHER", 0));
        db.execute("DROP TRIGGER IF EXISTS fail_mark_history");
        db.execute("DROP TRIGGER IF EXISTS fail_review_write");
        for (String table :
                List.of(
                        "class_member",
                        "teaching_class",
                        "course_enrollment",
                        "review_card",
                        "quiz_mark_event",
                        "quiz_attempt_item",
                        "quiz_attempt")) db.update("DELETE FROM " + table);
        db.update("DELETE FROM quiz_run_question");
        db.update("DELETE FROM quiz_run");
        reset(courses, directory);
        when(courses.courses(anyString()))
                .thenAnswer(
                        _ -> {
                            outsideTransaction();
                            return List.of(course());
                        });
        when(courses.releases(anyString()))
                .thenAnswer(
                        _ -> {
                            outsideTransaction();
                            return List.of(
                                    new Contracts.QuizRelease(7, "CLOSED", "PUBLISHED", null, 1));
                        });
        when(courses.course(11))
                .thenAnswer(
                        _ -> {
                            outsideTransaction();
                            return course();
                        });
        when(directory.names(anyString()))
                .thenAnswer(
                        _ -> {
                            outsideTransaction();
                            return List.of(
                                    new Contracts.PublicUser(1, "教师"),
                                    new Contracts.PublicUser(3, "学生"));
                        });
        when(directory.students(anyList()))
                .thenAnswer(
                        _ -> {
                            outsideTransaction();
                            return List.of(
                                    new Contracts.StudentAccount(3, "student", "学生", "ACTIVE"));
                        });
        when(courses.snapshot(7))
                .thenAnswer(
                        _ -> {
                            outsideTransaction();
                            return new Contracts.QuizSnapshot(
                                    7,
                                    11,
                                    1,
                                    0,
                                    "CLOSED",
                                    "PUBLISHED",
                                    null,
                                    null,
                                    0,
                                    1,
                                    List.of());
                        });
    }

    Contracts.CourseValidation course() {
        return new Contracts.CourseValidation(11, 1, "PUBLISHED", 1, "课程", "OWNER");
    }

    void answerFixture(String type, String selected, String answer) {
        db.update(
                "INSERT INTO quiz_attempt(id,quiz_id,course_id,chapter_id,student_id,score,total,completed_at,grading_status) VALUES(1,7,11,1,3,0,10,NOW(3),'PENDING')");
        db.update(
                "INSERT INTO quiz_attempt_item(id,attempt_id,question_id,prompt,option_a,option_b,option_c,option_d,selected_option,correct_option,question_type,points) VALUES(1,1,1,'题目','','','','',?,?,?,10)",
                selected,
                answer,
                type);
    }

    PracticeMarkService.Mark markRequest() {
        return new PracticeMarkService.Mark(
                0, List.of(new PracticeMarkService.Item(1L, 8, "补充引文")));
    }

    @Test
    void writtenMarkUpdatesItemAttemptAndHistoryTogether() {
        answerFixture("SHORT_ANSWER", "学生回答", "参考答案");
        var result = (Map<?, ?>) marks.mark(1, markRequest());
        assertEquals(1, result.get("gradingVersion"));
        assertEquals(
                8, db.queryForObject("SELECT score FROM quiz_attempt WHERE id=1", Integer.class));
        assertEquals(
                8,
                db.queryForObject(
                        "SELECT awarded_points FROM quiz_attempt_item WHERE id=1", Integer.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM quiz_mark_event", Integer.class));
        verify(courses, times(1)).course(11);
        verify(directory, times(1)).names(anyString());
    }

    @Test
    void historyFailureRollsBackItemAndAttempt() {
        answerFixture("SHORT_ANSWER", "学生回答", "参考答案");
        db.execute(
                "CREATE TRIGGER fail_mark_history BEFORE INSERT ON quiz_mark_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected history failure'");
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> marks.mark(1, markRequest()));
        assertEquals(
                0,
                db.queryForObject(
                        "SELECT grading_version FROM quiz_attempt WHERE id=1", Integer.class));
        assertNull(
                db.queryForObject(
                        "SELECT awarded_points FROM quiz_attempt_item WHERE id=1", Integer.class));
    }

    void reviewFixture() {
        answerFixture("SINGLE", "B", "A");
        db.update(
                "INSERT INTO review_card(id,student_id,course_id,quiz_id,question_id,quiz_version,attempt_item_id,last_wrong_at,next_review_at) VALUES(1,3,11,7,1,0,1,NOW(3),DATE_SUB(NOW(3),INTERVAL 1 DAY))");
        Actor.set(new Actor(3, "STUDENT", 0));
    }

    @Test
    void reviewChecksReleaseBeforeLockAndPreservesVersionConflict() {
        reviewFixture();
        reviews.answer(1, new ReviewService.Answer("A", 0));
        assertEquals(
                1,
                db.queryForObject(
                        "SELECT review_count FROM review_card WHERE id=1", Integer.class));
        var error =
                assertThrows(
                        ApiException.class,
                        () -> reviews.answer(1, new ReviewService.Answer("A", 0)));
        assertEquals(409, error.status);
    }

    @Test
    void failedReviewWriteLeavesScheduleUnchanged() {
        reviewFixture();
        db.execute(
                "CREATE TRIGGER fail_review_write BEFORE UPDATE ON review_card FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected review failure'");
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> reviews.answer(1, new ReviewService.Answer("A", 0)));
        assertEquals(
                0, db.queryForObject("SELECT version FROM review_card WHERE id=1", Integer.class));
    }

    @Test
    void classroomLifecycleDoesNotPerformRemoteCallsInsideTransaction() {
        var created =
                (Map<?, ?>) classrooms.create(new ClassroomService.Create(11L, "国学一班", "秋季", ""));
        long id = Long.parseLong(created.get("id").toString());
        classrooms.update(id, new ClassroomService.Write("国学二班", "秋季", "", "ACTIVE", 0));
        classrooms.rotate(id, 1);
        classrooms.importMembers(id, new ClassroomService.Import(List.of("student")));
        long member =
                db.queryForObject("SELECT id FROM class_member WHERE class_id=?", Long.class, id);
        classrooms.remove(id, member);
        String code =
                db.queryForObject(
                        "SELECT join_code FROM teaching_class WHERE id=?", String.class, id);
        Actor.set(new Actor(3, "STUDENT", 0));
        classrooms.join(new ClassroomService.Join(code));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM course_enrollment", Integer.class));
        assertEquals(
                "ACTIVE",
                db.queryForObject(
                        "SELECT status FROM class_member WHERE id=?", String.class, member));
        var page = (Map<?, ?>) classrooms.list(1, 10, false, null, null);
        var rows = (List<Map<?, ?>>) page.get("items");
        assertEquals(1L, rows.getFirst().get("memberCount"));
        verify(courses, times(1)).courses(anyString());
    }

    @Test
    void concurrentJoinsInDifferentClassesShareOneEnrollment() throws Exception {
        var first = (Map<?, ?>) classrooms.create(new ClassroomService.Create(11L, "甲班", "秋季", ""));
        var second =
                (Map<?, ?>) classrooms.create(new ClassroomService.Create(11L, "乙班", "秋季", ""));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<Object>>();
            for (var room : List.of(first, second))
                futures.add(
                        pool.submit(
                                () -> {
                                    Actor.set(new Actor(3, "STUDENT", 0));
                                    try {
                                        start.await();
                                        return classrooms.join(
                                                new ClassroomService.Join(
                                                        room.get("joinCode").toString()));
                                    } finally {
                                        Actor.clear();
                                    }
                                }));
            start.countDown();
            for (var future : futures)
                assertInstanceOf(Map.class, future.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM course_enrollment", Integer.class));
        assertEquals(
                2,
                db.queryForObject(
                        "SELECT COUNT(*) FROM class_member WHERE status='ACTIVE'", Integer.class));
    }

    @Test
    void reviewListUsesReleaseMetadataAndFrozenPageItems() {
        reviewFixture();
        var page = (Map<?, ?>) reviews.list(null, "ALL", 1, 10);
        var rows = (List<Map<?, ?>>) page.get("items");
        assertEquals(1, rows.size());
        assertEquals("题目", rows.getFirst().get("prompt"));
        verify(courses, times(1)).releases("7");
        verify(courses, never()).snapshot(anyLong());
    }

    @Test
    void batchedReleaseRetainsActiveRunAndLegacyAttemptRules() {
        Actor.set(new Actor(3, "STUDENT", 0));
        db.update(
                "INSERT INTO quiz_run(id,quiz_id,course_id,chapter_id,student_id,quiz_version,attempt_no,max_attempts,status,started_at,deadline_at) VALUES(1,7,11,1,3,0,1,1,'ACTIVE',NOW(3),DATE_ADD(NOW(3),INTERVAL 1 HOUR)),(2,8,11,1,3,0,1,1,'ACTIVE',NOW(3),DATE_SUB(NOW(3),INTERVAL 1 HOUR))");
        db.update(
                "INSERT INTO quiz_attempt(id,quiz_id,course_id,chapter_id,student_id,score,total,completed_at) VALUES(1,9,11,1,3,0,10,NOW(3))");
        when(courses.releases(anyString()))
                .thenReturn(
                        List.of(
                                new Contracts.QuizRelease(7, "PUBLISHED", "PUBLISHED", null, 1),
                                new Contracts.QuizRelease(8, "PUBLISHED", "PUBLISHED", null, 1),
                                new Contracts.QuizRelease(9, "PUBLISHED", "PUBLISHED", null, 1),
                                new Contracts.QuizRelease(10, "PUBLISHED", "PUBLISHED", null, 1),
                                new Contracts.QuizRelease(11, "PUBLISHED", "ARCHIVED", null, 1)));
        var allowed = practice.reviewReleased(List.of(7L, 8L, 9L, 10L, 11L));
        assertEquals(Map.of(7L, false, 8L, true, 9L, true, 10L, false, 11L, true), allowed);
        for (long quiz : List.of(7L, 8L, 9L, 10L, 11L)) {
            when(courses.snapshot(quiz))
                    .thenReturn(
                            new Contracts.QuizSnapshot(
                                    quiz,
                                    11,
                                    1,
                                    0,
                                    "PUBLISHED",
                                    quiz == 11 ? "ARCHIVED" : "PUBLISHED",
                                    null,
                                    null,
                                    0,
                                    1,
                                    List.of()));
            assertEquals(allowed.get(quiz), practice.reviewReleased(quiz));
        }
    }

    @Test
    void unavailableCourseDoesNotCreateClassroom() {
        when(courses.course(11)).thenThrow(new IllegalStateException("injected upstream failure"));
        assertThrows(
                ApiException.class,
                () -> classrooms.create(new ClassroomService.Create(11L, "一班", "秋季", "")));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM teaching_class", Integer.class));
    }

    @AfterEach
    void cleanup() {
        db.execute("DROP TRIGGER IF EXISTS fail_mark_history");
        db.execute("DROP TRIGGER IF EXISTS fail_review_write");
        Actor.clear();
    }
}
