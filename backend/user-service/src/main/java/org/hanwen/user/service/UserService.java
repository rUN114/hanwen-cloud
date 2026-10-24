package org.hanwen.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.common.*;
import org.hanwen.user.client.RelatedDataClient;
import org.hanwen.user.mapper.UserMapper;
import org.hanwen.user.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class UserService {
    final UserMapper users;
    final JwtCodec jwt;
    final String privateKey;
    final RelatedDataClient.Courses courses;
    final RelatedDataClient.Learning learning;
    final RelatedDataClient.Assignments assignments;
    final Remote remote;
    final UserAudit audit;
    final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
    // Single user-service instance: local attempt limits; use a shared store if horizontally
    // scaled.
    final Map<String, Attempt> attempts = new HashMap<>();
    final String dummyHash;

    record Attempt(int count, long expires) {}

    public record Login(String username, String password) {}

    public record Create(String username, String password, String displayName, String role) {}

    public record Access(String role, String status) {}

    public record Reset(String newPassword) {}

    public UserService(
            UserMapper u,
            JwtCodec j,
            @Value("${hw.private-key-file}") String k,
            RelatedDataClient.Courses c,
            RelatedDataClient.Learning l,
            RelatedDataClient.Assignments a,
            Remote r,
            UserAudit audit) {
        users = u;
        jwt = j;
        privateKey = k;
        courses = c;
        learning = l;
        assignments = a;
        remote = r;
        this.audit = audit;
        dummyHash = bcrypt.encode(UUID.randomUUID().toString());
    }

    public static Object view(User u) {
        return Views.map(
                "id",
                u.id,
                "username",
                u.username,
                "displayName",
                u.displayName,
                "role",
                u.role,
                "status",
                u.status);
    }

    static String username(String s) {
        s = Views.text(s, 50, "用户名").toLowerCase(Locale.ROOT);
        if (!s.matches("[a-z0-9_.-]{3,50}")) throw ApiException.bad("用户名须为3至50位小写字母、数字、下划线、点或短横线");
        return s;
    }

    static String password(String s) {
        if (s == null || s.length() < 8 || s.getBytes(StandardCharsets.UTF_8).length > 72)
            throw ApiException.bad("密码至少8个字符，且UTF-8编码不超过72字节");
        return s;
    }

    public Object login(Login input, String ip) throws Exception {
        String
                name =
                        input.username() == null
                                ? ""
                                : input.username().strip().toLowerCase(Locale.ROOT),
                key = ip + ":" + name;
        long now = System.currentTimeMillis();
        synchronized (attempts) {
            var a = attempts.get(key);
            if (a != null && a.expires() > now && a.count() >= 5)
                throw new ApiException(429, "LOGIN_LIMITED", "登录失败次数过多，请5分钟后重试");
            if (attempts.size() > 10000)
                attempts.entrySet().removeIf(e -> e.getValue().expires() < now);
        }
        User u = users.selectOne(new QueryWrapper<User>().eq("username", name));
        String raw = input.password() == null ? "" : input.password();
        boolean matched =
                raw.getBytes(StandardCharsets.UTF_8).length <= 72
                        && bcrypt.matches(raw, u == null ? dummyHash : u.passwordHash);
        if (u == null || !matched || !u.status.equals("ENABLED")) {
            synchronized (attempts) {
                attempts.compute(
                        key,
                        (_, a) ->
                                new Attempt(
                                        a == null || a.expires() < now ? 1 : a.count() + 1,
                                        now + 300000));
            }
            throw new ApiException(401, "AUTH_INVALID", "账号或密码不正确");
        }
        synchronized (attempts) {
            attempts.remove(key);
        }
        String token = jwt.issue(u.id, u.role, u.authVersion, privateKey);
        return Views.map(
                "accessToken", token, "expiresAt", Views.now().plusMinutes(15), "user", view(u));
    }

    public User required(long id) {
        User u = users.selectById(id);
        if (u == null) throw ApiException.missing();
        return u;
    }

    public Object list(int page, int size, String keyword, String role) {
        Views.role("ADMIN");
        int offset = Views.offset(page, size);
        var q = new QueryWrapper<User>();
        if (keyword != null && !keyword.isBlank())
            q.and(w -> w.like("username", keyword).or().like("display_name", keyword));
        if (role != null && !role.isBlank()) {
            checkRole(role);
            q.eq("role", role);
        }
        long total = users.selectCount(q);
        var items =
                users
                        .selectList(q.orderByDesc("id").last("LIMIT " + size + " OFFSET " + offset))
                        .stream()
                        .map(UserService::view)
                        .toList();
        return Views.page(items, page, size, total);
    }

    static void checkRole(String r) {
        if (!Set.of("ADMIN", "TEACHER", "STUDENT").contains(r == null ? "" : r))
            throw ApiException.bad("角色不合法");
    }

    @Transactional
    public Object create(Create in) {
        Views.role("ADMIN");
        var u = insert(in, false);
        audit.write(
                Actor.current().id(),
                u.id,
                "CREATE_USER",
                "创建" + (u.role.equals("TEACHER") ? "教师" : "学生") + "账号");
        return view(u);
    }

    public record ImportRow(String username, String displayName) {}

    public record ImportUsers(String role, List<ImportRow> rows) {}

    @Transactional
    public Object importUsers(ImportUsers in) {
        Views.role("ADMIN");
        if (!Set.of("STUDENT", "TEACHER").contains(String.valueOf(in.role())))
            throw ApiException.bad("批量开户仅支持教师和学生");
        if (in.rows() == null || in.rows().isEmpty() || in.rows().size() > 50)
            throw ApiException.bad("每批须为1至50人");
        var checked = new ArrayList<ImportRow>();
        var seen = new HashSet<String>();
        for (var r : in.rows()) {
            if (r == null) throw ApiException.bad("名单存在空行");
            String name = username(r.username());
            if (!seen.add(name)) throw ApiException.bad("名单存在重复账号：" + name);
            checked.add(new ImportRow(name, Views.text(r.displayName(), 50, "姓名")));
        }
        if (users.selectCount(new QueryWrapper<User>().in("username", seen)) > 0)
            throw ApiException.conflict("名单中有已存在的账号，请核对后整批重试");
        var result = new ArrayList<>();
        for (var row : checked) {
            // Start with letters so CSV formula protection never changes a generated password.
            String initial = "Hw" + RefreshService.randomToken().substring(0, 20) + "9!";
            var u =
                    insert(
                            new Create(row.username(), initial, row.displayName(), in.role()),
                            false);
            result.add(
                    Views.map(
                            "id",
                            u.id,
                            "username",
                            u.username,
                            "displayName",
                            u.displayName,
                            "initialPassword",
                            initial,
                            "role",
                            u.role));
        }
        audit.write(
                Actor.current().id(),
                null,
                "IMPORT_USERS",
                "批量创建" + result.size() + "个" + (in.role().equals("TEACHER") ? "教师" : "学生") + "账号");
        return Views.map("created", result.size(), "items", result);
    }

    User insert(Create in, boolean bootstrap) {
        checkRole(in.role());
        if (!bootstrap && in.role().equals("ADMIN")) throw ApiException.bad("常规账号创建仅支持教师和学生");
        User u = new User();
        u.username = username(in.username());
        u.displayName = Views.text(in.displayName(), 50, "显示名");
        u.passwordHash = bcrypt.encode(password(in.password()));
        u.role = in.role();
        u.status = "ENABLED";
        u.authVersion = 0;
        u.createdAt = u.updatedAt = Views.now();
        users.insert(u);
        return u;
    }

    @Transactional
    public void bootstrap(String pwd) {
        if (users.selectCount(new QueryWrapper<>()) == 0)
            insert(new Create("admin", pwd, "书院管理员", "ADMIN"), true);
    }

    @Transactional
    public void logout() {
        User u = users.lock(Actor.current().id());
        if (u == null) throw ApiException.missing();
        u.authVersion++;
        u.updatedAt = Views.now();
        users.updateById(u);
        audit.write(u.id, u.id, "LOGOUT", "退出并撤销该账号全部登录");
    }

    @Transactional
    public Object access(long id, Access in) {
        Views.role("ADMIN");
        checkRole(in.role());
        if (!Set.of("ENABLED", "DISABLED").contains(in.status() == null ? "" : in.status()))
            throw ApiException.bad("状态不合法");
        // Lock all enabled administrators in id order before the target, serializing the last-admin
        // check.
        var admins =
                users.selectList(
                        new QueryWrapper<User>()
                                .eq("role", "ADMIN")
                                .eq("status", "ENABLED")
                                .orderByAsc("id")
                                .last("FOR UPDATE"));
        User u = users.lock(id);
        if (u == null) throw ApiException.missing();
        if (u.role.equals("ADMIN")
                && u.status.equals("ENABLED")
                && (!in.role().equals("ADMIN") || !in.status().equals("ENABLED"))
                && admins.size() <= 1) throw ApiException.conflict("不能禁用或降级最后一名启用管理员");
        if (!u.role.equals(in.role())) {
            boolean has =
                    u.role.equals("TEACHER")
                            ? remote.call("role-courses", () -> courses.has(id)).hasData()
                            : u.role.equals("STUDENT")
                                    && (remote.call("role-learning", () -> learning.has(id))
                                                    .hasData()
                                            || remote.call(
                                                            "role-submissions",
                                                            () -> assignments.has(id))
                                                    .hasData());
            if (has) throw ApiException.conflict("账号已有教学记录，不能改变角色");
        }
        u.role = in.role();
        u.status = in.status();
        u.authVersion++;
        u.updatedAt = Views.now();
        users.updateById(u);
        audit.write(
                Actor.current().id(), u.id, "ACCESS_CHANGE", "权限更新为" + u.role + " / " + u.status);
        return view(u);
    }

    @Transactional
    public void reset(long id, Reset in) {
        Views.role("ADMIN");
        User u = users.lock(id);
        if (u == null) throw ApiException.missing();
        u.passwordHash = bcrypt.encode(password(in.newPassword()));
        u.authVersion++;
        u.updatedAt = Views.now();
        users.updateById(u);
        audit.write(Actor.current().id(), u.id, "PASSWORD_RESET", "管理员重置密码并撤销旧登录");
    }

    public record ProfileWrite(String displayName, String email, String biography) {}

    public record PasswordWrite(String currentPassword, String newPassword) {}

    public Object profile() {
        User u = required(Actor.current().id());
        @SuppressWarnings("unchecked")
        var v = (Map<String, Object>) view(u);
        v.put("email", u.email);
        v.put("biography", u.biography);
        return v;
    }

    @Transactional
    public Object profile(ProfileWrite in) {
        User u = users.lock(Actor.current().id());
        u.displayName = Views.text(in.displayName(), 50, "显示名");
        u.email = Views.optional(in.email(), 120, "邮箱");
        if (u.email != null && !u.email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw ApiException.bad("邮箱格式不正确");
        u.biography = Views.optional(in.biography(), 500, "个人简介");
        u.updatedAt = Views.now();
        users.updateById(u);
        return profile();
    }

    @Transactional
    public void password(PasswordWrite in) {
        User u = users.lock(Actor.current().id());
        String current = in.currentPassword();
        if (current == null
                || current.getBytes(StandardCharsets.UTF_8).length > 72
                || !bcrypt.matches(current, u.passwordHash)) throw ApiException.bad("当前密码不正确");
        String next = password(in.newPassword());
        if (bcrypt.matches(next, u.passwordHash)) throw ApiException.bad("新密码不能与当前密码相同");
        u.passwordHash = bcrypt.encode(next);
        u.authVersion++;
        u.updatedAt = Views.now();
        users.updateById(u);
        audit.write(u.id, u.id, "PASSWORD_CHANGE", "本人修改密码并撤销旧登录");
    }

    public Object overview() {
        Views.role("ADMIN");
        var result = new LinkedHashMap<String, Object>();
        for (String role : List.of("ADMIN", "TEACHER", "STUDENT"))
            result.put(role, users.selectCount(new QueryWrapper<User>().eq("role", role)));
        result.put(
                "disabled", users.selectCount(new QueryWrapper<User>().eq("status", "DISABLED")));
        return result;
    }

    public List<Contracts.PublicUser> names(String ids) {
        var parsed =
                Arrays.stream(Views.text(ids, 2200, "用户编号").split(","))
                        .map(
                                x -> {
                                    try {
                                        long v = Long.parseLong(x);
                                        if (v < 1) throw new IllegalArgumentException();
                                        return v;
                                    } catch (Exception e) {
                                        throw ApiException.bad("用户编号不合法");
                                    }
                                })
                        .distinct()
                        .toList();
        if (parsed.size() > 100) throw ApiException.bad("一次最多查询100人");
        return users.selectByIds(parsed).stream()
                .map(u -> new Contracts.PublicUser(u.id, u.displayName))
                .toList();
    }

    public java.util.List<Contracts.StudentAccount> teachers(java.util.List<String> input) {
        Views.role("TEACHER");
        if (input == null || input.isEmpty() || input.size() > 10)
            throw ApiException.bad("每批须为1至10个教师账号");
        var names = input.stream().map(x -> Views.text(x, 50, "教师账号")).distinct().toList();
        var accounts =
                users.selectList(
                        new QueryWrapper<User>()
                                .in("username", names)
                                .eq("role", "TEACHER")
                                .eq("status", "ENABLED"));
        if (accounts.size() != names.size()) throw ApiException.bad("存在不存在、禁用或非教师的账号，请核对");
        return accounts.stream()
                .map(u -> new Contracts.StudentAccount(u.id, u.username, u.displayName, u.status))
                .toList();
    }

    public java.util.List<Contracts.StudentAccount> students(java.util.List<String> input) {
        Views.role("TEACHER");
        if (input == null || input.isEmpty() || input.size() > 100)
            throw ApiException.bad("每批须为1至100个学生账号");
        var names = input.stream().map(x -> Views.text(x, 50, "学生账号")).distinct().toList();
        var accounts =
                users.selectList(
                        new QueryWrapper<User>()
                                .in("username", names)
                                .eq("role", "STUDENT")
                                .eq("status", "ENABLED"));
        if (accounts.size() != names.size()) throw ApiException.bad("名单中有不存在、禁用或非学生的账号，请核对后整批重试");
        return accounts.stream()
                .map(u -> new Contracts.StudentAccount(u.id, u.username, u.displayName, u.status))
                .toList();
    }
}
