package org.hanwen.common;

import java.net.URI;
import java.util.*;
import java.util.regex.*;

public final class DocumentRules {
    public static String url(String input) {
        String s = Views.optional(input, 500, "来源网址");
        if (s == null) return null;
        try {
            var u = URI.create(s);
            if (!Set.of("http", "https").contains(u.getScheme())
                    || u.getHost() == null
                    || u.getUserInfo() != null) throw new IllegalArgumentException();
        } catch (Exception e) {
            throw ApiException.bad("链接须为完整HTTP或HTTPS网址");
        }
        return s;
    }

    public static String validate(String input, boolean images) {
        String s = Views.optional(input, 20000, "排版讲义");
        if (s == null) return null;
        if (Pattern.compile("<\\s*/?\\s*[A-Za-z][^>]*>").matcher(s).find())
            throw ApiException.bad("讲义不接受HTML，请使用排版按钮");
        var links = Pattern.compile("(!?)\\[([^\\]\\r\\n]*)\\]\\(([^\\s)]+)\\)").matcher(s);
        while (links.find()) {
            String target = links.group(3);
            if (links.group(1).equals("!")) {
                if (!images || !target.matches("resource:[1-9][0-9]{0,18}"))
                    throw ApiException.bad("图片须从本课程资源选择");
            } else url(target);
        }
        return s;
    }

    public static List<Long> resources(String input) {
        if (input == null) return List.of();
        var m =
                Pattern.compile("!\\[[^\\]\\r\\n]*\\]\\(resource:([1-9][0-9]{0,18})\\)")
                        .matcher(input);
        var ids = new HashSet<Long>();
        while (m.find()) {
            try {
                ids.add(Long.parseLong(m.group(1)));
            } catch (Exception e) {
                throw ApiException.bad("资源编号不合法");
            }
        }
        if (ids.size() > 20) throw ApiException.bad("讲义最多20张图片");
        return ids.stream().toList();
    }
}
