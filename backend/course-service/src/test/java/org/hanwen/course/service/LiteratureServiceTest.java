package org.hanwen.course.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.hanwen.common.Actor;
import org.hanwen.common.ApiException;
import org.hanwen.course.mapper.WorkMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class LiteratureServiceTest {
    @AfterEach
    void clearActor() {
        Actor.clear();
    }

    @Test
    void nullAllusionFromJsonIsRejectedBeforeDatabaseWrite() {
        var input =
                JsonMapper.builder()
                        .build()
                        .readValue(
                                """
                                {
                                  "title": "静夜思",
                                  "author": "李白",
                                  "dynasty": "唐",
                                  "genre": "诗",
                                  "content": "举头望明月，低头思故乡。",
                                  "allusions": [null]
                                }
                                """,
                                LiteratureService.Write.class);
        assertNull(input.allusions().getFirst());
        var works = mock(WorkMapper.class);
        var service = new LiteratureService(works);
        Actor.set(new Actor(1000L, "TEACHER", 0));

        var error = assertThrows(ApiException.class, () -> service.write(null, input));

        assertEquals(400, error.status);
        assertEquals("典故不可为空", error.getMessage());
        verifyNoInteractions(works);
    }
}
