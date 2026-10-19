package org.hanwen.eureka;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "server.address=127.0.0.1",
            "eureka.client.register-with-eureka=false",
            "eureka.client.fetch-registry=false",
            "eureka.client.service-url.defaultZone=http://localhost:0/eureka/",
            "eureka.instance.hostname=localhost",
            "eureka.instance.non-secure-port=0",
            "eureka.server.wait-time-in-ms-when-sync-empty=0"
        })
class EurekaStartupTest {
    @Autowired Environment environment;

    @Test
    void registryStartsWithoutBusinessAuthenticationConfiguration() throws Exception {
        var response = get("/actuator/health");
        assertEquals(200, response.statusCode());
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
        assertEquals("UP", json.path("status").asText());
    }

    @Test
    void dashboardRendersWithThePatchedTemplateEngine() throws Exception {
        var response = get("/");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("System Status"));
    }

    private HttpResponse<String> get(String path) throws Exception {
        var uri =
                URI.create(
                        "http://127.0.0.1:"
                                + environment.getRequiredProperty("local.server.port")
                                + path);
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
