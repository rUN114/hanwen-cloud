package org.hanwen.user.service;

import org.hanwen.common.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserAudit {
    private final JdbcTemplate db;

    public UserAudit(JdbcTemplate db) {
        this.db = db;
    }

    public void write(long actor, Long target, String action, String summary) {
        db.update(
                "INSERT INTO"
                        + " user_audit_event(actor_id,target_id,action,summary,trace_id,created_at)"
                        + " VALUES(?,?,?,?,?,?)",
                actor,
                target,
                action,
                summary,
                Trace.id(),
                Views.now());
    }

    public Object list(int page, int size) {
        Views.role("ADMIN");
        int offset = Views.offset(page, size);
        var rows =
                db.query(
                        "SELECT e.*,a.display_name actor_name,t.display_name target_name FROM"
                                + " user_audit_event e LEFT JOIN sys_user a ON a.id=e.actor_id LEFT"
                                + " JOIN sys_user t ON t.id=e.target_id ORDER BY e.id DESC LIMIT ?"
                                + " OFFSET ?",
                        (r, n) ->
                                Views.map(
                                        "id",
                                        r.getLong("id"),
                                        "actorName",
                                        r.getString("actor_name"),
                                        "targetName",
                                        r.getString("target_name"),
                                        "action",
                                        r.getString("action"),
                                        "summary",
                                        r.getString("summary"),
                                        "traceId",
                                        r.getString("trace_id"),
                                        "createdAt",
                                        r.getTimestamp("created_at").toLocalDateTime()),
                        size,
                        offset);
        return Views.page(
                rows,
                page,
                size,
                db.queryForObject("SELECT COUNT(*) FROM user_audit_event", Long.class));
    }
}
