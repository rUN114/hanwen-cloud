package org.hanwen.user.service;

import org.hanwen.common.*;
import org.hanwen.user.mapper.UserMapper;
import org.hanwen.user.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class RefreshService {
    private final JdbcTemplate db;
    private final UserMapper users;
    private final JwtCodec jwt;
    private final UserAudit audit;
    private final String privateKey;

    public record CookieToken(String token, LocalDateTime expiresAt) {}

    public record Renewed(Object data, CookieToken cookie) {}

    record Stored(long id, long user, int version, LocalDateTime expires) {}

    public RefreshService(
            JdbcTemplate db,
            UserMapper users,
            JwtCodec jwt,
            UserAudit audit,
            @Value("${hw.private-key-file}") String key) {
        this.db = db;
        this.users = users;
        this.jwt = jwt;
        this.audit = audit;
        privateKey = key;
    }

    static String randomToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static ApiException expired() {
        return new ApiException(401, "SESSION_EXPIRED", "登录已失效，草稿保留在当前浏览器，请重新登录");
    }

    void valid(User u, int version) {
        if (u == null || !u.status.equals("ENABLED") || u.authVersion != version) throw expired();
    }

    @Transactional
    public CookieToken open(Actor actor) {
        User u = users.lock(actor.id());
        valid(u, actor.version());
        var now = Views.now();
        String token = randomToken();
        var expires = now.plusHours(8);
        // 只清理到期会话，登录审计另存；每次最多删一千条，避免长时间占用事务。
        db.update("DELETE FROM auth_refresh_session WHERE expires_at < ? LIMIT 1000", now);
        db.update(
                "INSERT INTO"
                        + " auth_refresh_session(user_id,token_hash,auth_version,expires_at,created_at,updated_at)"
                        + " VALUES(?,?,?,?,?,?)",
                u.id,
                hash(token),
                u.authVersion,
                expires,
                now,
                now);
        audit.write(u.id, u.id, "LOGIN", "登录书院");
        return new CookieToken(token, expires);
    }

    @Transactional
    public Renewed renew(String token) throws Exception {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw expired();
        String digest = hash(token);
        var records =
                db.query(
                        "SELECT * FROM auth_refresh_session WHERE token_hash=?",
                        (r, n) ->
                                new Stored(
                                        r.getLong("id"),
                                        r.getLong("user_id"),
                                        r.getInt("auth_version"),
                                        r.getTimestamp("expires_at").toLocalDateTime()),
                        digest);
        if (records.size() != 1) throw expired();
        var old = records.getFirst();
        User u = users.lock(old.user());
        valid(u, old.version());
        // 续期、重置密码和退出都先锁用户；下方按旧摘要更新，确保同一令牌只能续期一次。
        var now = Views.now();
        if (!old.expires().isAfter(now)) throw expired();
        String next = randomToken();
        var accessExpiry =
                old.expires().isBefore(now.plusMinutes(15)) ? old.expires() : now.plusMinutes(15);
        String access =
                jwt.issue(
                        u.id,
                        u.role,
                        u.authVersion,
                        privateKey,
                        accessExpiry.atOffset(java.time.ZoneOffset.ofHours(8)).toInstant());
        int changed =
                db.update(
                        "UPDATE auth_refresh_session SET token_hash=?,updated_at=? WHERE id=? AND"
                                + " token_hash=?",
                        hash(next),
                        now,
                        old.id(),
                        digest);
        if (changed != 1) throw expired();
        return new Renewed(
                Views.map(
                        "accessToken",
                        access,
                        "expiresAt",
                        accessExpiry,
                        "sessionExpiresAt",
                        old.expires(),
                        "user",
                        UserService.view(u)),
                new CookieToken(next, old.expires()));
    }
}
