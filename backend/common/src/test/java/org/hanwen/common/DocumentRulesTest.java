package org.hanwen.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class DocumentRulesTest {
    @Test
    void unsafeMarkupAndUrls() {
        assertThrows(
                ApiException.class,
                () -> DocumentRules.validate("<img src=x onerror=alert(1)>", true));
        assertThrows(
                ApiException.class, () -> DocumentRules.validate("[点击](javascript:alert)", true));
        assertThrows(
                ApiException.class,
                () -> DocumentRules.validate("![图](https://example.com/p.png)", true));
        assertEquals("# 讲义\n**证据**", DocumentRules.validate("# 讲义\n**证据**", true));
        assertEquals(java.util.List.of(12L), DocumentRules.resources("![图](resource:12)"));
        assertThrows(
                ApiException.class, () -> DocumentRules.url("https://user:secret@example.com"));
    }
}
