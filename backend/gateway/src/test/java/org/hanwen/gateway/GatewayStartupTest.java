package org.hanwen.gateway;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "server.address=127.0.0.1",
            "spring.main.web-application-type=reactive",
            "eureka.client.enabled=false",
            "spring.cloud.discovery.enabled=false",
            "hw.service-key=gateway-startup-test-only"
        })
class GatewayStartupTest {
    private static Path publicKeyFile;

    @Autowired Environment environment;

    @DynamicPropertySource
    static void isolatedPublicKey(DynamicPropertyRegistry properties) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var publicKey = generator.generateKeyPair().getPublic();
        var pem =
                "-----BEGIN PUBLIC KEY-----\n"
                        + Base64.getMimeEncoder(64, new byte[] {'\n'})
                                .encodeToString(publicKey.getEncoded())
                        + "\n-----END PUBLIC KEY-----\n";
        publicKeyFile = Files.createTempFile("hanwen-gateway-test-public-", ".pem");
        Files.writeString(publicKeyFile, pem);
        properties.add("hw.public-key-file", () -> publicKeyFile.toString());
    }

    @AfterAll
    static void removePublicKey() throws Exception {
        if (publicKeyFile != null) Files.delete(publicKeyFile);
    }

    @Test
    void reactiveGatewayStartsAndExposesHealth() throws Exception {
        var response = get("/actuator/health");
        assertEquals(200, response.statusCode());
        var json =
                tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body());
        assertEquals("UP", json.path("status").asText());
    }

    @Test
    void anonymousCourseRequestIsRejectedBeforeRouting() throws Exception {
        assertEquals(401, get("/api/courses").statusCode());
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
