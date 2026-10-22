package org.hanwen.course.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.common.*;
import org.hanwen.course.mapper.WorkMapper;
import org.hanwen.course.model.Work;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.*;

@Service
public class LiteratureService {
    final WorkMapper works;

    public LiteratureService(WorkMapper w) {
        works = w;
    }

    public record Write(
            String title,
            String author,
            String dynasty,
            String genre,
            String content,
            String readingGuide,
            String sourceUrl,
            String sourceNote,
            Integer expectedVersion,
            String translation,
            String annotationsText,
            List<@Nullable Allusion> allusions) {}

    public record Allusion(String term, String explanation, Long workId, String sourceUrl) {}

    public record State(String status, Integer expectedVersion) {}

    public Work required(long id) {
        var w = works.selectById(id);
        if (w == null) throw ApiException.missing();
        return w;
    }

    void owner(Work w) {
        Views.role("TEACHER");
        if (w.teacherId == null || w.teacherId != Actor.current().id())
            throw ApiException.forbidden();
    }

    static Map<String, Object> view(Work w, boolean body) {
        var v =
                Views.map(
                        "id",
                        w.id,
                        "teacherId",
                        w.teacherId,
                        "title",
                        w.title,
                        "author",
                        w.author,
                        "dynasty",
                        w.dynasty,
                        "genre",
                        w.genre,
                        "status",
                        w.status,
                        "version",
                        w.version,
                        "createdAt",
                        w.createdAt,
                        "updatedAt",
                        w.updatedAt);
        if (body) {
            v.put("content", w.content);
            v.put("readingGuide", w.readingGuide);
            v.put("sourceUrl", w.sourceUrl);
            v.put("sourceNote", w.sourceNote);
            v.put("translation", w.translation);
            v.put("annotationsText", w.annotationsText);
            v.put(
                    "allusions",
                    w.allusionsJson == null
                            ? List.of()
                            : tools.jackson.databind.json.JsonMapper.builder()
                                    .build()
                                    .readValue(w.allusionsJson, Allusion[].class));
        }
        return v;
    }

    public Object list(
            int page, int size, String keyword, String dynasty, String genre, boolean mine) {
        int offset = Views.offset(page, size);
        var q = new QueryWrapper<Work>();
        if (mine) {
            Views.role("TEACHER");
            q.eq("teacher_id", Actor.current().id());
        } else q.eq("status", "PUBLISHED");
        if (keyword != null && !keyword.isBlank())
            q.and(x -> x.like("title", keyword).or().like("author", keyword));
        if (dynasty != null && !dynasty.isBlank()) q.eq("dynasty", dynasty);
        if (genre != null && !genre.isBlank()) q.eq("genre", genre);
        long total = works.selectCount(q);
        return Views.page(
                works
                        .selectList(q.orderByDesc("id").last("LIMIT " + size + " OFFSET " + offset))
                        .stream()
                        .map(w -> view(w, false))
                        .toList(),
                page,
                size,
                total);
    }

    public Object detail(long id) {
        var w = required(id);
        if (!w.status.equals("PUBLISHED")) owner(w);
        return view(w, true);
    }

    @Transactional
    public Object write(Long id, Write in) {
        Views.role("TEACHER");
        Work w = id == null ? new Work() : works.lock(id);
        if (w == null) throw ApiException.missing();
        if (id != null) {
            owner(w);
            Names.version(w.version, in.expectedVersion());
            if (!w.status.equals("PUBLISHED")) throw ApiException.conflict("请先重新公开诗文再编辑");
        }
        w.title = Views.text(in.title(), 100, "作品标题");
        w.author = Views.text(in.author(), 50, "作者");
        w.dynasty = Views.text(in.dynasty(), 30, "时代");
        w.genre = Views.text(in.genre(), 30, "体裁");
        w.content = Views.text(in.content(), 20000, "作品正文");
        w.readingGuide = Views.optional(in.readingGuide(), 5000, "阅读提示");
        w.sourceNote = Views.optional(in.sourceNote(), 500, "版本说明");
        w.sourceUrl = Views.optional(in.sourceUrl(), 500, "来源链接");
        if (w.sourceUrl != null) {
            try {
                URI uri = URI.create(w.sourceUrl);
                if (!Set.of("http", "https").contains(uri.getScheme())
                        || uri.getHost() == null
                        || uri.getUserInfo() != null) throw new IllegalArgumentException();
            } catch (Exception e) {
                throw ApiException.bad("来源须为完整的HTTP或HTTPS网址");
            }
        }
        w.translation = Views.optional(in.translation(), 20000, "译文");
        w.annotationsText = Views.optional(in.annotationsText(), 10000, "注释");
        List<@Nullable Allusion> allusions = in.allusions() == null ? List.of() : in.allusions();
        if (allusions.size() > 20) throw ApiException.bad("典故最多20项");
        var checked = new ArrayList<Allusion>();
        for (var x : allusions) {
            if (x == null) throw ApiException.bad("典故不可为空");
            if (x.workId() != null) {
                var target = required(x.workId());
                if (!target.status.equals("PUBLISHED")) throw ApiException.bad("典故关联作品须为公开作品");
            }
            checked.add(
                    new Allusion(
                            Views.text(x.term(), 80, "典故名称"),
                            Views.text(x.explanation(), 2000, "典故释义"),
                            x.workId(),
                            DocumentRules.url(x.sourceUrl())));
        }
        w.allusionsJson =
                tools.jackson.databind.json.JsonMapper.builder()
                        .build()
                        .writeValueAsString(checked);
        w.updatedAt = Views.now();
        if (id == null) {
            w.teacherId = Actor.current().id();
            w.version = 0;
            w.status = "PUBLISHED";
            w.createdAt = w.updatedAt;
            works.insert(w);
        } else {
            w.version++;
            works.updateById(w);
        }
        return view(w, true);
    }

    @Transactional
    public Object state(long id, State in) {
        var w = works.lock(id);
        if (w == null) throw ApiException.missing();
        owner(w);
        Names.version(w.version, in.expectedVersion());
        if (!Set.of("PUBLISHED", "ARCHIVED").contains(in.status() == null ? "" : in.status()))
            throw ApiException.bad("作品状态不合法");
        w.status = in.status();
        w.version++;
        w.updatedAt = Views.now();
        works.updateById(w);
        return view(w, false);
    }

    public List<Contracts.WorkBrief> briefs(String ids) {
        var parsed =
                Arrays.stream(Views.text(ids, 2200, "作品编号").split(","))
                        .map(
                                x -> {
                                    try {
                                        long v = Long.parseLong(x);
                                        if (v < 1) throw new IllegalArgumentException();
                                        return v;
                                    } catch (Exception e) {
                                        throw ApiException.bad("作品编号不合法");
                                    }
                                })
                        .distinct()
                        .toList();
        if (parsed.size() > 100) throw ApiException.bad("一次最多查询100篇");
        return works.selectByIds(parsed).stream()
                .map(
                        w ->
                                new Contracts.WorkBrief(
                                        w.id, w.title, w.author, w.dynasty, w.genre, w.status))
                .toList();
    }

    public Contracts.WorkBrief validation(long id) {
        var w = required(id);
        return new Contracts.WorkBrief(w.id, w.title, w.author, w.dynasty, w.genre, w.status);
    }
}
