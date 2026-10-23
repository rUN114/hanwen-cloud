package org.hanwen.assignment.client;

import static org.junit.jupiter.api.Assertions.*;

import feign.RequestTemplate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.cloud.openfeign.support.SpringMvcContract;

import java.util.Map;

class CourseClientContractTest {
    @ParameterizedTest
    @CsvSource({
        "course, /internal/courses/1000/validation",
        "chapter, /internal/courses/1000/chapters/2000/validation",
        "quizzes, /internal/courses/1000/grade-quizzes"
    })
    void compiledParameterNamesResolveEveryPathVariable(String methodName, String expectedPath) {
        var metadata =
                new SpringMvcContract()
                        .parseAndValidateMetadata(CourseClient.class).stream()
                                .filter(
                                        m ->
                                                m.configKey()
                                                        .startsWith(
                                                                "CourseClient#" + methodName + "("))
                                .findFirst()
                                .orElseThrow();
        var template =
                new RequestTemplate(metadata.template())
                        .resolve(Map.of("id", "1000", "chapterId", "2000"));

        assertEquals("GET", template.method());
        assertEquals(expectedPath, template.url());
        assertTrue(metadata.indexToName().get(0).contains("id"));
        if (methodName.equals("chapter")) {
            assertTrue(metadata.indexToName().get(1).contains("chapterId"));
        }
    }
}
