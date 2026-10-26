package org.hanwen.assignment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.assignment.mapper.*;
import org.hanwen.assignment.model.*;
import org.hanwen.common.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DraftVersionTest {
    @AfterEach
    void clear() {
        Actor.clear();
    }

    @ParameterizedTest
    @CsvSource(
            value = {"NULL,400", "-1,409", "-2,400", "1,409", "0,200"},
            nullValues = "NULL")
    void existingDraftDistinguishesMissingStaleAndCurrentVersions(Integer version, int status) {
        Actor.set(new Actor(3, "STUDENT", 0));
        var tasks = mock(TaskMapper.class);
        var drafts = mock(DraftMapper.class);
        var submissions = mock(SubmissionMapper.class);
        var task = new Task();
        task.id = 1L;
        task.courseId = 11L;
        task.status = "PUBLISHED";
        when(tasks.selectById(1L)).thenReturn(task);
        when(tasks.lock(1)).thenReturn(task);
        var draft = new Draft();
        draft.id = 1L;
        draft.version = 0;
        when(drafts.selectOne(any())).thenReturn(draft);
        var service =
                new AssignmentExtrasService(
                        mock(AssignmentService.class),
                        drafts,
                        tasks,
                        submissions,
                        null,
                        null,
                        null,
                        mock(org.springframework.transaction.PlatformTransactionManager.class));
        if (status == 200) {
            var result =
                    (java.util.Map<?, ?>)
                            service.save(1, new AssignmentExtrasService.Write("答案", version));
            assertEquals(1, result.get("version"));
            assertEquals("答案", result.get("answerText"));
            verify(drafts).updateById(draft);
            verify(drafts, never()).insert(any(Draft.class));
        } else {
            var error =
                    assertThrows(
                            ApiException.class,
                            () ->
                                    service.save(
                                            1, new AssignmentExtrasService.Write("答案", version)));
            assertEquals(status, error.status);
            verify(drafts, never()).updateById(any(Draft.class));
            verify(drafts, never()).insert(any(Draft.class));
        }
    }
}
