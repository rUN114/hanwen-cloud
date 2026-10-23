package org.hanwen.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.hanwen.common.Actor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

class UserAuditTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final UserAudit audit = new UserAudit(db);

    @BeforeEach
    void signInAsAdministrator() {
        Actor.set(new Actor(1L, "ADMIN", 0));
    }

    @AfterEach
    void clearActor() {
        Actor.clear();
    }

    @Test
    void emptyAuditTableHasNumericZeroTotal() {
        when(db.queryForObject("SELECT COUNT(*) FROM user_audit_event", Long.class)).thenReturn(0L);

        Map<?, ?> page = assertInstanceOf(Map.class, audit.list(1, 10));

        assertEquals(0L, page.get("total"));
    }

    @Test
    void missingCountIsReportedAsAQueryFailure() {
        when(db.queryForObject("SELECT COUNT(*) FROM user_audit_event", Long.class))
                .thenReturn(null);

        var error = assertThrows(DataRetrievalFailureException.class, () -> audit.list(1, 10));

        assertEquals("审计记录总数查询未返回计数", error.getMessage());
    }
}
