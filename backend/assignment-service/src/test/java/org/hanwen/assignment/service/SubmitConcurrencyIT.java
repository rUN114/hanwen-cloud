package org.hanwen.assignment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;

import org.apache.ibatis.session.SqlSessionFactory;
import org.hanwen.assignment.client.*;
import org.hanwen.assignment.mapper.*;
import org.hanwen.common.*;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import javax.sql.DataSource;

/** 显式连接独立测试库运行，不属于默认单元测试；禁止使用现用教学数据库。 */
@SpringJUnitConfig(SubmitConcurrencyIT.Config.class)
class SubmitConcurrencyIT {
    @Autowired AssignmentService service;
    @Autowired JdbcTemplate db;
    @Autowired CourseClient courses;
    @Autowired LearningClient learning;

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = TaskMapper.class)
    static class Config {
        @Bean
        DataSource dataSource() throws Exception {
            String url = System.getProperty("hanwen.test.jdbcUrl", "");
            String expected = System.getProperty("hanwen.test.dataDir", "");
            if (!url.startsWith("jdbc:mysql://127.0.0.1:14327/hw_assignment?")
                    || expected.isBlank())
                throw new IllegalArgumentException(
                        "Integration test requires its isolated port and explicit data directory");
            var ds = new DriverManagerDataSource(url, "root", "");
            try (var c = ds.getConnection();
                    var statement = c.createStatement();
                    var rows = statement.executeQuery("SELECT @@datadir")) {
                rows.next();
                if (!Path.of(rows.getString(1))
                        .toAbsolutePath()
                        .normalize()
                        .equals(Path.of(expected).toAbsolutePath().normalize()))
                    throw new IllegalArgumentException(
                            "Refusing a database outside the isolated test directory");
            }
            return ds;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(ds);
            return factory.getObject();
        }

        @Bean
        PlatformTransactionManager transactions(DataSource ds) {
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
        LearningClient learning() {
            return mock(LearningClient.class);
        }

        @Bean
        AssignmentService assignment(
                TaskMapper tasks,
                SubmissionMapper submissions,
                GradeMapper grades,
                CourseClient courses,
                LearningClient learning,
                DraftMapper drafts,
                GradeHistoryMapper history) {
            return new AssignmentService(
                    tasks,
                    submissions,
                    grades,
                    courses,
                    learning,
                    new Remote(),
                    drafts,
                    history,
                    mock(DirectoryClient.class));
        }
    }

    @BeforeEach
    void resetData() {
        assertTrue(AopUtils.isAopProxy(service));
        assertEquals(
                "REPEATABLE-READ",
                db.queryForObject("SELECT @@transaction_isolation", String.class));
        db.update("DELETE FROM assignment_draft");
        db.update("DELETE FROM assignment_submission");
        db.update("DELETE FROM assignment_task");
        db.update(
                "INSERT INTO assignment_task(id,course_id,teacher_id,title,description,max_score,status,deadline_at,created_at,updated_at) VALUES(1,11,1,'并发验收','隔离数据',100,'PUBLISHED',DATE_ADD(NOW(),INTERVAL 1 HOUR),NOW(),NOW())");
        reset(courses, learning);
        when(courses.course(11))
                .thenReturn(new Contracts.CourseValidation(11, 1, "PUBLISHED", 1, "课程", "NONE"));
        when(learning.exists(11, 3)).thenReturn(new Contracts.Exists(true));
    }

    @Test
    void simultaneousSameContentCreatesOneSubmissionAndReturnsItTwice() throws Exception {
        var results = concurrent(" 原答案 ", "原答案\n");
        assertTrue(results.stream().allMatch(Map.class::isInstance));
        assertEquals(
                ((Map<?, ?>) results.get(0)).get("id"), ((Map<?, ?>) results.get(1)).get("id"));
        assertEquals(
                1, db.queryForObject("SELECT COUNT(*) FROM assignment_submission", Integer.class));
        assertEquals(
                "原答案",
                db.queryForObject("SELECT answer_text FROM assignment_submission", String.class));
    }

    @Test
    void simultaneousDifferentContentReportsOneConflict() throws Exception {
        var results = concurrent("答案甲", "答案乙");
        assertEquals(1, results.stream().filter(Map.class::isInstance).count());
        var error =
                (ApiException)
                        results.stream()
                                .filter(ApiException.class::isInstance)
                                .findFirst()
                                .orElseThrow();
        assertEquals(409, error.status);
        assertEquals("SUBMISSION_EXISTS", error.code);
        assertEquals(
                1, db.queryForObject("SELECT COUNT(*) FROM assignment_submission", Integer.class));
    }

    @Test
    void existingSubmissionCannotBypassDeadlineClosureOrArchive() {
        assertInstanceOf(Map.class, submit("原答案"));
        db.update("UPDATE assignment_task SET deadline_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE)");
        assertEquals(409, ((ApiException) submit("原答案")).status);
        db.update(
                "UPDATE assignment_task SET status='CLOSED',deadline_at=DATE_ADD(NOW(),INTERVAL 1 HOUR)");
        assertEquals(409, ((ApiException) submit("原答案")).status);
        db.update("UPDATE assignment_task SET status='PUBLISHED'");
        when(courses.course(11))
                .thenReturn(new Contracts.CourseValidation(11, 1, "ARCHIVED", 1, "课程", "NONE"));
        assertEquals(409, ((ApiException) submit("原答案")).status);
        assertEquals(
                1, db.queryForObject("SELECT COUNT(*) FROM assignment_submission", Integer.class));
    }

    private List<Object> concurrent(String a, String b) throws Exception {
        // 两个事务先完成普通读，再竞争作业锁，覆盖旧快照下重试的场景。
        var barrier = new CyclicBarrier(2);
        when(learning.exists(11, 3))
                .thenAnswer(
                        call -> {
                            barrier.await(10, TimeUnit.SECONDS);
                            return new Contracts.Exists(true);
                        });
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> submit(a));
            var second = workers.submit(() -> submit(b));
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
    }

    private Object submit(String text) {
        Actor.set(new Actor(3, "STUDENT", 0));
        try {
            return service.submit(1, new AssignmentService.Answer(text));
        } catch (ApiException e) {
            return e;
        } finally {
            Actor.clear();
        }
    }
}
