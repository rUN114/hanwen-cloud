package org.hanwen.course.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;

import org.apache.ibatis.session.SqlSessionFactory;
import org.hanwen.common.*;
import org.hanwen.course.mapper.*;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Comparator;

import javax.sql.DataSource;

@SpringJUnitConfig(ResourcePurgeIT.Config.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResourcePurgeIT {
    @Configuration
    @MapperScan("org.hanwen.course.mapper")
    static class Config {
        @Bean
        DataSource dataSource() throws Exception {
            String url = System.getProperty("hanwen.test.jdbcUrl", "");
            String expected = System.getProperty("hanwen.test.dataDir", "");
            if (!url.startsWith("jdbc:mysql://127.0.0.1:14327/hw_course?") || expected.isBlank())
                throw new IllegalArgumentException("Isolated course database required");
            var ds = new DriverManagerDataSource(url, "root", "");
            try (var connection = ds.getConnection();
                    var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT @@datadir")) {
                result.next();
                if (!Path.of(result.getString(1))
                        .toAbsolutePath()
                        .normalize()
                        .equals(Path.of(expected).toAbsolutePath().normalize()))
                    throw new IllegalArgumentException("Refusing a non-test database directory");
            }
            return ds;
        }

        @Bean
        SqlSessionFactory factory(DataSource ds) throws Exception {
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(ds);
            return factory.getObject();
        }

        @Bean
        JdbcTemplate jdbc(DataSource ds) {
            return new JdbcTemplate(ds);
        }

        @Bean
        Path storage() throws Exception {
            return Files.createTempDirectory("hanwen-resource-it-");
        }

        @Bean
        ResourceService resources(ResourceFileMapper files, ChapterMapper chapters, Path storage)
                throws Exception {
            return new ResourceService(
                    mock(CourseService.class), files, chapters, storage.toString());
        }
    }

    @Autowired JdbcTemplate db;
    @Autowired ResourceService resources;
    @Autowired ResourceFileMapper files;
    @Autowired ChapterMapper chapters;
    @Autowired Path storage;

    @BeforeEach
    void reset() {
        db.update("DELETE FROM course_resource");
        db.update(
                "INSERT INTO course(id,teacher_id,name,summary,category,status,created_at,updated_at) VALUES(1,1,'fixture','','古文','PUBLISHED',NOW(),NOW()) ON DUPLICATE KEY UPDATE status='PUBLISHED'");
    }

    void seed(long id, String name, String status, int days) throws Exception {
        Files.writeString(storage.resolve(name), "fixture");
        db.update(
                "INSERT INTO course_resource(id,course_id,title,description,original_name,storage_name,mime_type,bytes,sha256,status,created_at,removed_at) VALUES(?,1,'fixture','','fixture.txt',?,'text/plain',7,REPEAT('0',64),?,?,?)",
                id,
                name,
                status,
                Views.now().minusDays(60),
                Views.now().minusDays(days));
    }

    @Test
    void realDatabaseKeepsActiveAndRecentFiles() throws Exception {
        seed(1, "expired.bin", "REMOVED", 31);
        seed(2, "recent.bin", "REMOVED", 1);
        seed(3, "active.bin", "ACTIVE", 31);
        resources.purgeRemovedResources();
        assertFalse(Files.exists(storage.resolve("expired.bin")));
        assertTrue(Files.exists(storage.resolve("recent.bin")));
        assertTrue(Files.exists(storage.resolve("active.bin")));
        assertNull(
                db.queryForObject(
                        "SELECT storage_name FROM course_resource WHERE id=1", String.class));
    }

    @Test
    void backupLockAndSecondInstanceCannotCorruptCleanup() throws Exception {
        seed(1, "competing.bin", "REMOVED", 31);
        try (var channel =
                        FileChannel.open(
                                storage.resolve(StorageMaintenance.LOCK_FILE),
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE);
                var lock = channel.lock(0, 1, false)) {
            resources.purgeRemovedResources();
            assertTrue(Files.exists(storage.resolve("competing.bin")));
            assertNotNull(
                    db.queryForObject(
                            "SELECT storage_name FROM course_resource WHERE id=1", String.class));
        }
        resources.purgeRemovedResources();
        new ResourceService(mock(CourseService.class), files, chapters, storage.toString())
                .purgeRemovedResources();
        assertFalse(Files.exists(storage.resolve("competing.bin")));
        assertNull(
                db.queryForObject(
                        "SELECT storage_name FROM course_resource WHERE id=1", String.class));
    }

    @AfterAll
    void cleanup() throws Exception {
        db.update("DELETE FROM course_resource");
        try (var paths = Files.walk(storage)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
