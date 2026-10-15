package org.hanwen.learning;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.hanwen.common.*;
import org.hanwen.learning.mapper.ReceiptMapper;
import org.hanwen.learning.model.Receipt;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class ReceiptController {
    final ReceiptMapper receipts;

    public ReceiptController(ReceiptMapper r) {
        receipts = r;
    }

    public record Read(List<String> keys) {}

    void allowed() {
        if (!Set.of("STUDENT", "TEACHER").contains(Actor.current().role()))
            throw ApiException.forbidden();
    }

    @GetMapping("/api/inbox/receipts")
    public Object list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size) {
        allowed();
        int offset = Views.offset(page, size);
        var q = new QueryWrapper<Receipt>().eq("user_id", Actor.current().id());
        long total = receipts.selectCount(q);
        return ApiResponse.ok(
                Views.page(
                        receipts
                                .selectList(
                                        q.orderByDesc("id")
                                                .last("LIMIT " + size + " OFFSET " + offset))
                                .stream()
                                .map(r -> Views.map("eventKey", r.eventKey, "readAt", r.readAt))
                                .toList(),
                        page,
                        size,
                        total));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @PostMapping("/api/inbox/receipts")
    public Object read(@RequestBody Read in) {
        allowed();
        if (in.keys() == null || in.keys().isEmpty() || in.keys().size() > 100)
            throw ApiException.bad("每批已读标记须为1至100条");
        var keys = in.keys().stream().distinct().toList();
        for (var key : keys)
            if (key == null
                    || key.length() > 180
                    || !key.matches(
                            "(NOTICE|REPLY|QUESTION|GRADE|SUBMISSION|DEADLINE|NUDGE):[1-9][0-9]*(:[0-9T:Z.+-]+)?"))
                throw ApiException.bad("消息标识不合法");
        for (var key : keys) {
            if (receipts.selectCount(
                            new QueryWrapper<Receipt>()
                                    .eq("user_id", Actor.current().id())
                                    .eq("event_key", key))
                    > 0) continue;
            Receipt r = new Receipt();
            r.userId = Actor.current().id();
            r.eventKey = key;
            r.readAt = Views.now();
            try {
                receipts.insert(r);
            } catch (DuplicateKeyException ignored) {
            }
        }
        return ApiResponse.ok(null);
    }
}
