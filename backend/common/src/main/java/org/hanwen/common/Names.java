package org.hanwen.common;

import java.util.*;
import java.util.stream.Collectors;

public final class Names {
    public static Map<Long, String> read(
            Collection<Long> ids, DirectoryClient client, Remote remote) {
        var all = ids.stream().filter(Objects::nonNull).distinct().toList();
        var names = new HashMap<Long, String>();
        for (int i = 0; i < all.size(); i += 100) {
            String query =
                    all.subList(i, Math.min(i + 100, all.size())).stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(","));
            for (var u : remote.call("public-names", () -> client.names(query)))
                names.put(u.id(), u.displayName());
        }
        return names;
    }

    public static void version(Integer actual, Integer expected) {
        if (expected == null || expected < 0) throw ApiException.bad("缺少有效的expectedVersion");
        if (!expected.equals(actual))
            throw new ApiException(409, "VERSION_CONFLICT", "内容已更新，请重新查看后再操作");
    }
}
