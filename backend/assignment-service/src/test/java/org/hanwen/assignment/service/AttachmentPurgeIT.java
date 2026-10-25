package org.hanwen.assignment.service;

import static org.junit.jupiter.api.Assertions.*;

import org.hanwen.assignment.mapper.AttachmentMapper;
import org.hanwen.common.Views;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;

@SpringJUnitConfig(AttachmentPurgeIT.Config.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttachmentPurgeIT {
    @Configuration
    @Import(SubmitConcurrencyIT.Config.class)
    static class Config {
        @Bean
        Path uploadDirectory() throws IOException {
            return Files.createTempDirectory("hanwen-attachment-it-");
        }

        @Bean
        AttachmentService attachments(
                AssignmentService assignments,
                AttachmentMapper mapper,
                Path uploadDirectory,
                org.springframework.transaction.PlatformTransactionManager manager)
                throws IOException {
            return new AttachmentService(assignments, mapper, uploadDirectory.toString(), manager);
        }
    }

    @Autowired JdbcTemplate db;
    @Autowired AttachmentService attachments;
    @Autowired Path uploadDirectory;

    @BeforeEach
    void reset() {
        db.update("DELETE FROM assignment_attachment");
        db.update("DELETE FROM assignment_draft");
        db.update("DELETE FROM assignment_submission");
        db.update("DELETE FROM assignment_task");
        db.update(
                "INSERT INTO assignment_task(id,course_id,teacher_id,title,description,max_score,status,created_at,updated_at) VALUES(1,11,1,'清理验收','独立测试',100,'PUBLISHED',NOW(),NOW())");
    }

    void seed(long id, String name, String status, int days) throws IOException {
        Files.writeString(uploadDirectory.resolve(name), "独立测试文件");
        db.update(
                "INSERT INTO assignment_attachment(id,assignment_id,student_id,original_name,mime_type,bytes,sha256,storage_name,status,created_at,removed_at) VALUES(?,1,3,'附件.txt','text/plain',1,REPEAT('0',64),?,?,?,?)",
                id,
                name,
                status,
                Views.now().minusDays(60),
                Views.now().minusDays(days));
    }

    @Test
    void realDatabasePreservesGracePeriodAndActiveFiles() throws Exception {
        seed(1, "expired.bin", "REMOVED", 31);
        seed(2, "recent.bin", "REMOVED", 1);
        seed(3, "active.bin", "ACTIVE", 31);
        attachments.purgeRemovedAttachments();
        assertFalse(Files.exists(uploadDirectory.resolve("expired.bin")));
        assertTrue(Files.exists(uploadDirectory.resolve("recent.bin")));
        assertTrue(Files.exists(uploadDirectory.resolve("active.bin")));
        assertNull(
                db.queryForObject(
                        "SELECT storage_name FROM assignment_attachment WHERE id=1", String.class));
        assertEquals(
                2,
                db.queryForObject(
                        "SELECT COUNT(*) FROM assignment_attachment WHERE storage_name IS NOT NULL",
                        Integer.class));
    }

    @Test
    void missingFileCanBeFinalizedAndRepeatedPurgeIsIdempotent() throws Exception {
        seed(1, "missing.bin", "REMOVED", 31);
        Files.delete(uploadDirectory.resolve("missing.bin"));
        attachments.purgeRemovedAttachments();
        attachments.purgeRemovedAttachments();
        assertNull(
                db.queryForObject(
                        "SELECT storage_name FROM assignment_attachment WHERE id=1", String.class));
        assertEquals(
                1, db.queryForObject("SELECT COUNT(*) FROM assignment_attachment", Integer.class));
    }

    @AfterAll
    void cleanup() throws IOException {
        db.update("DELETE FROM assignment_attachment");
        try (var paths = Files.walk(uploadDirectory)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
