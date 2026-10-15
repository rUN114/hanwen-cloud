package org.hanwen.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.security.*;
import java.util.Base64;

class JwtCodecTest {
    @TempDir Path dir;

    Path pem(String name, String label, byte[] bytes) throws Exception {
        Path path = dir.resolve(name);
        Files.writeString(
                path,
                "-----BEGIN "
                        + label
                        + "-----\n"
                        + Base64.getEncoder().encodeToString(bytes)
                        + "\n-----END "
                        + label
                        + "-----\n");
        return path;
    }

    @Test
    void signedClaimsRoundTripAndForgedClaimsAreRejected() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        JwtCodec codec =
                new JwtCodec(
                        pem("pub.pem", "PUBLIC KEY", pair.getPublic().getEncoded()).toString());
        String token =
                codec.issue(
                        17L,
                        "STUDENT",
                        3,
                        pem("key.pem", "PRIVATE KEY", pair.getPrivate().getEncoded()).toString());
        assertEquals(new Actor(17L, "STUDENT", 3), codec.parse("Bearer " + token));
        String[] parts = token.split("\\.");
        String payload =
                new String(
                                Base64.getUrlDecoder().decode(parts[1]),
                                java.nio.charset.StandardCharsets.UTF_8)
                        .replace("STUDENT", "ADMIN");
        String forged =
                parts[0]
                        + "."
                        + Base64.getUrlEncoder()
                                .withoutPadding()
                                .encodeToString(
                                        payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "."
                        + parts[2];
        assertEquals(
                401,
                assertThrows(ApiException.class, () -> codec.parse("Bearer " + forged)).status);
        assertEquals(401, assertThrows(ApiException.class, () -> codec.parse(null)).status);
    }
}
