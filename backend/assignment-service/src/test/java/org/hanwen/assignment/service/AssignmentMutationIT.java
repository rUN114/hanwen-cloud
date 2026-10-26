package org.hanwen.assignment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.hanwen.assignment.client.CourseClient;
import org.hanwen.assignment.client.LearningClient;
import org.hanwen.assignment.mapper.*;
import org.hanwen.common.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@SpringJUnitConfig(AssignmentMutationIT.Config.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AssignmentMutationIT {
    @Configuration
    @Import(SubmitConcurrencyIT.Config.class)
    static class Config {
        @Bean
        RevisionService revisions(
                AssignmentService a, SubmissionRevisionMapper r, PlatformTransactionManager m) {
            return new RevisionService(a, r, m);
        }

        @Bean
        AssignmentExtrasService extras(
                AssignmentService a,
                DraftMapper d,
                TaskMapper t,
                SubmissionMapper s,
                GradeHistoryMapper h,
                PlatformTransactionManager manager) {
            return new AssignmentExtrasService(
                    a, d, t, s, h, mock(DirectoryClient.class), new Remote(), manager);
        }

        @Bean
        ReminderService reminders(
                AssignmentService a, ReminderMapper r, PlatformTransactionManager m) {
            return new ReminderService(a, r, m);
        }

        @Bean
        AttachmentService attachments(
                AssignmentService a, AttachmentMapper f, PlatformTransactionManager m)
                throws Exception {
            return new AttachmentService(
                    a, f, Files.createTempDirectory("hanwen-attachment-it-").toString(), m);
        }

        @Bean
        BatchTaskService batches(AssignmentService service, PlatformTransactionManager manager) {
            return new BatchTaskService(service, manager);
        }
    }

    @Autowired RevisionService revisions;
    @Autowired AssignmentExtrasService extras;
    @Autowired ReminderService reminders;
    @Autowired AttachmentService attachments;
    @Autowired LearningClient learning;
    @Autowired JdbcTemplate db;
    @Autowired CourseClient courses;
    @Autowired AssignmentService assignment;
    @Autowired BatchTaskService batches;

    @BeforeEach
    void setup() {
        Actor.set(new Actor(1, "TEACHER", 0));
        db.execute("DROP TRIGGER IF EXISTS force_batch_failure");
        db.execute("DROP TRIGGER IF EXISTS force_history_failure");
        db.execute("DROP TRIGGER IF EXISTS force_draft_cleanup_failure");
        db.update("DELETE FROM assignment_grade_history");
        db.update("DELETE FROM assignment_grade");
        db.update("DELETE FROM submission_revision");
        db.update("DELETE FROM assignment_attachment");
        db.update("DELETE FROM assignment_draft");
        db.update("DELETE FROM assignment_reminder");
        db.update("DELETE FROM assignment_submission");
        db.update("DELETE FROM assignment_task");
        for (int id = 1; id <= 2; id++)
            db.update(
                    "INSERT INTO assignment_task(id,course_id,teacher_id,title,description,max_score,status,deadline_at,created_at,updated_at) VALUES(?,11,1,'fixture','',100,'DRAFT',DATE_ADD(NOW(),INTERVAL 1 DAY),NOW(3),NOW(3))",
                    id);
        reset(courses, learning);
        when(learning.exists(11, 3))
                .thenAnswer(
                        _ -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            return new Contracts.Exists(true);
                        });
        when(learning.grades(11))
                .thenAnswer(
                        _ -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            return new Contracts.GradeFeed(List.of(3L), List.of());
                        });
        when(courses.course(11))
                .thenAnswer(
                        _ -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive(),
                                    "Remote permission lookup must precede the local transaction");
                            return new Contracts.CourseValidation(
                                    11, 1, "PUBLISHED", 1, "课程", "OWNER");
                        });
    }

    BatchTaskService.Write request() {
        var first =
                db.queryForObject(
                        "SELECT updated_at FROM assignment_task WHERE id=1",
                        (row, _) -> row.getObject(1, java.time.LocalDateTime.class));
        var second =
                db.queryForObject(
                        "SELECT updated_at FROM assignment_task WHERE id=2",
                        (row, _) -> row.getObject(1, java.time.LocalDateTime.class));
        return new BatchTaskService.Write(
                11L,
                "PUBLISH",
                List.of(
                        new BatchTaskService.Item(
                                1L, "DRAFT", BusinessTime.atOffset(first).toString()),
                        new BatchTaskService.Item(
                                2L, "DRAFT", BusinessTime.atOffset(second).toString())));
    }

    @Test
    void batchValidatesCourseOnceBeforeLockingAndUpdatesEveryTask() {
        batches.apply(request());
        assertEquals(
                2,
                db.queryForObject(
                        "SELECT COUNT(*) FROM assignment_task WHERE status='PUBLISHED'",
                        Integer.class));
        verify(courses, times(1)).course(11);
    }

    @Test
    void creationValidatesCourseBeforeStartingTransaction() {
        assignment.create(new AssignmentService.Create(11L, "新作业", "说明", "100", null, null));
        assertEquals(3, db.queryForObject("SELECT COUNT(*) FROM assignment_task", Integer.class));
        verify(courses, times(1)).course(11);
    }

    @Test
    void mapperWriteGuardRejectsAccidentalAutocommit() {
        var task = new org.hanwen.assignment.model.Task();
        task.id = 1L;
        task.title = "不应写入";
        var error = assertThrows(AssertionError.class, () -> assignment.tasks().updateById(task));
        assertTrue(error.getMessage().contains("requires a real transaction"));
        assertEquals(
                "fixture",
                db.queryForObject("SELECT title FROM assignment_task WHERE id=1", String.class));
    }

    @Test
    void submissionValidatesCourseAndEnrollmentBeforeStartingTransaction() {
        db.update("UPDATE assignment_task SET status='PUBLISHED' WHERE id=1");
        Actor.set(new Actor(3, "STUDENT", 0));
        assignment.submit(1, new AssignmentService.Answer("答案"));
        assertEquals(
                1, db.queryForObject("SELECT COUNT(*) FROM assignment_submission", Integer.class));
        verify(courses, times(1)).course(11);
        verify(learning, times(1)).exists(11, 3);
    }

    @Test
    void answerModificationValidatesRemotelyBeforeStartingTransaction() {
        submission();
        Actor.set(new Actor(3, "STUDENT", 0));
        assignment.modify(1, new AssignmentService.Modify("修正答案", 0));
        assertEquals(
                "修正答案",
                db.queryForObject(
                        "SELECT answer_text FROM assignment_submission WHERE id=1", String.class));
        verify(courses, times(1)).course(11);
        verify(learning, times(1)).exists(11, 3);
    }

    @Test
    void updateAndStatusTransitionsValidateBeforeStartingTransaction() {
        assignment.update(
                1,
                new AssignmentService.Write(
                        "更新题目",
                        "说明",
                        "100",
                        BusinessTime.atOffset(Views.now().plusDays(2)).toString(),
                        null));
        assignment.transition(1, true);
        assignment.transition(1, false);
        assertEquals(
                "CLOSED",
                db.queryForObject("SELECT status FROM assignment_task WHERE id=1", String.class));
        assertEquals(
                "更新题目",
                db.queryForObject("SELECT title FROM assignment_task WHERE id=1", String.class));
        verify(courses, times(3)).course(11);
    }

    @Test
    void copyingChecksBothCoursesBeforeStartingTransaction() {
        when(courses.course(12))
                .thenAnswer(
                        _ -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            return new Contracts.CourseValidation(
                                    12, 1, "PUBLISHED", 1, "目标课程", "OWNER");
                        });
        var result = (Map<?, ?>) reminders.copy(new ReminderService.Copy(11L, 12L));
        assertEquals(2, result.get("copied"));
        assertEquals(
                2,
                db.queryForObject(
                        "SELECT COUNT(*) FROM assignment_task WHERE course_id=12 AND status='DRAFT'",
                        Integer.class));
        verify(courses, times(1)).course(11);
        verify(courses, times(1)).course(12);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void draftCleanupFailureRollsBackSubmissionWrite(boolean editing) {
        if (editing) submission();
        else db.update("UPDATE assignment_task SET status='PUBLISHED' WHERE id=1");
        db.update(
                "INSERT INTO assignment_draft(assignment_id,student_id,answer_text,version,updated_at) VALUES(1,3,'保留草稿',0,NOW(3))");
        db.execute(
                "CREATE TRIGGER force_draft_cleanup_failure BEFORE DELETE ON assignment_draft FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected draft cleanup failure'");
        Actor.set(new Actor(3, "STUDENT", 0));
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> {
                    if (editing) assignment.modify(1, new AssignmentService.Modify("新答案", 0));
                    else assignment.submit(1, new AssignmentService.Answer("新答案"));
                });
        assertEquals(
                editing ? 1 : 0,
                db.queryForObject("SELECT COUNT(*) FROM assignment_submission", Integer.class));
        if (editing) {
            assertEquals(
                    "原答案",
                    db.queryForObject(
                            "SELECT answer_text FROM assignment_submission WHERE id=1",
                            String.class));
            assertEquals(
                    0,
                    db.queryForObject(
                            "SELECT version FROM assignment_submission WHERE id=1", Integer.class));
        }
        assertEquals(
                "保留草稿",
                db.queryForObject("SELECT answer_text FROM assignment_draft", String.class));
    }

    @Test
    void failureOnSecondWriteRollsBackFirstTask() {
        db.execute(
                "CREATE TRIGGER force_batch_failure BEFORE UPDATE ON assignment_task FOR EACH ROW BEGIN IF NEW.id=2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected failure'; END IF; END");
        assertThrows(
                org.springframework.dao.DataAccessException.class, () -> batches.apply(request()));
        assertEquals(
                2,
                db.queryForObject(
                        "SELECT COUNT(*) FROM assignment_task WHERE status='DRAFT'",
                        Integer.class));
        verify(courses, times(1)).course(11);
    }

    void submission() {
        db.update("UPDATE assignment_task SET status='PUBLISHED' WHERE id=1");
        db.update(
                "INSERT INTO assignment_submission(id,assignment_id,student_id,answer_text,status,version,submitted_at,created_at,updated_at) VALUES(1,1,3,'原答案','SUBMITTED',0,NOW(3),NOW(3),NOW(3))");
    }

    @Test
    void gradingKeepsSubmissionAndHistoryAtomic() {
        submission();
        assignment.mark(1, new AssignmentService.Mark("80", null, 0, null));
        verify(courses, times(1)).course(11);
        assertEquals(
                1,
                db.queryForObject(
                        "SELECT version FROM assignment_submission WHERE id=1", Integer.class));
        assertEquals(
                1,
                db.queryForObject("SELECT COUNT(*) FROM assignment_grade_history", Integer.class));
    }

    @Test
    void historyFailureRollsBackGradeAndSubmissionVersion() {
        submission();
        db.execute(
                "CREATE TRIGGER force_history_failure BEFORE INSERT ON assignment_grade_history FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected history failure'");
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> assignment.mark(1, new AssignmentService.Mark("80", null, 0, null)));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM assignment_grade", Integer.class));
        assertEquals(
                0,
                db.queryForObject(
                        "SELECT version FROM assignment_submission WHERE id=1", Integer.class));
    }

    @Test
    void revisionLifecycleKeepsOriginalSubmissionAndValidatesBeforeLocking() {
        submission();
        db.update("UPDATE assignment_submission SET status='GRADED' WHERE id=1");
        var invitation = (Map<?, ?>) revisions.invite(1, new RevisionService.Invite("补充论证", 0));
        long revision = Long.parseLong(invitation.get("id").toString());
        Actor.set(new Actor(3, "STUDENT", 0));
        revisions.submit(1, revision, new RevisionService.Submit("修改后的答案", "增加引文", 0));
        Actor.set(new Actor(1, "TEACHER", 0));
        revisions.review(1, revision, new RevisionService.Review("ACCEPTED", "论据充分", 1));
        assertEquals(
                "ACCEPTED",
                db.queryForObject(
                        "SELECT status FROM submission_revision WHERE id=?",
                        String.class,
                        revision));
        assertEquals(
                "原答案",
                db.queryForObject(
                        "SELECT answer_text FROM assignment_submission WHERE id=1", String.class));
        var cancelled = (Map<?, ?>) revisions.invite(1, new RevisionService.Invite("下一轮修改", 0));
        long next = Long.parseLong(cancelled.get("id").toString());
        revisions.cancel(1, next, 0);
        assertEquals(
                "CANCELLED",
                db.queryForObject(
                        "SELECT status FROM submission_revision WHERE id=?", String.class, next));
    }

    @Test
    void simultaneousFirstDraftSavesKeepOneValueAndReportConflict() throws Exception {
        db.update("UPDATE assignment_task SET status='PUBLISHED'");
        var output =
                concurrent(
                        "STUDENT",
                        3,
                        () -> extras.save(1, new AssignmentExtrasService.Write("新草稿", -1)));
        assertEquals(1, output.stream().filter(Map.class::isInstance).count());
        var error =
                (ApiException)
                        output.stream()
                                .filter(ApiException.class::isInstance)
                                .findFirst()
                                .orElseThrow();
        assertEquals(409, error.status);
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM assignment_draft", Integer.class));
    }

    @Test
    void simultaneousRemindersDeduplicateByStudentAndDay() throws Exception {
        db.update("UPDATE assignment_task SET status='PUBLISHED'");
        var output =
                concurrent(
                        "TEACHER",
                        1,
                        () -> reminders.send(1, new ReminderService.Send(List.of(3L), "请按时提交")));
        assertTrue(output.stream().allMatch(Map.class::isInstance), output.toString());
        assertEquals(
                1, db.queryForObject("SELECT COUNT(*) FROM assignment_reminder", Integer.class));
        assertEquals(
                1,
                output.stream().map(Map.class::cast).mapToInt(m -> (Integer) m.get("sent")).sum());
    }

    @Test
    void attachmentUploadAndRemovalValidateOutsideTransaction() throws Exception {
        db.update("UPDATE assignment_task SET status='PUBLISHED'");
        Actor.set(new Actor(3, "STUDENT", 0));
        var uploaded =
                (Map<?, ?>)
                        attachments.upload(
                                1,
                                new MockMultipartFile(
                                        "file",
                                        "习作.txt",
                                        "text/plain",
                                        "文稿".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        long id = Long.parseLong(uploaded.get("id").toString());
        assertEquals(
                1,
                db.queryForObject(
                        "SELECT COUNT(*) FROM assignment_attachment WHERE status='ACTIVE'",
                        Integer.class));
        attachments.remove(id);
        assertEquals(
                "REMOVED",
                db.queryForObject(
                        "SELECT status FROM assignment_attachment WHERE id=?", String.class, id));
    }

    List<Object> concurrent(String role, long actor, Callable<Object> call) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Object> work =
                    () -> {
                        Actor.set(new Actor(actor, role, 0));
                        try {
                            start.await();
                            return call.call();
                        } catch (ApiException e) {
                            return e;
                        } finally {
                            Actor.clear();
                        }
                    };
            var first = pool.submit(work);
            var second = pool.submit(work);
            start.countDown();
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
    }

    @AfterEach
    void cleanup() {
        db.execute("DROP TRIGGER IF EXISTS force_batch_failure");
        db.execute("DROP TRIGGER IF EXISTS force_history_failure");
        db.execute("DROP TRIGGER IF EXISTS force_draft_cleanup_failure");
        db.update("DELETE FROM assignment_grade_history");
        db.update("DELETE FROM assignment_grade");
        Actor.clear();
    }

    @AfterAll
    void removeTemporaryStorage() throws Exception {
        var root = attachments.storage.toAbsolutePath().normalize();
        assertTrue(root.getFileName().toString().startsWith("hanwen-attachment-it-"));
        try (var paths = Files.walk(root)) {
            for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                Files.delete(path);
        }
    }
}
