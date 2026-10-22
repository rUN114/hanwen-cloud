package org.hanwen.common;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

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

    @ParameterizedTest
    @ValueSource(strings = {"sub", "ver", "ver-text"})
    void signedTokensWithMissingOrInvalidIdentityClaimsAreRejected(String invalidClaim)
            throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var codec =
                new JwtCodec(
                        pem("claims-public.pem", "PUBLIC KEY", pair.getPublic().getEncoded())
                                .toString());
        var now = Instant.now();
        var claims =
                new JWTClaimsSet.Builder()
                        .issuer("hanwen-user")
                        .audience("hanwen-services")
                        .subject("17")
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(60)))
                        .claim("role", "STUDENT")
                        .claim("ver", 3);
        if (invalidClaim.equals("ver-text")) {
            claims.claim("ver", "invalid-version");
        } else {
            claims.claim(invalidClaim, null);
        }
        var token = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        token.sign(new RSASSASigner((RSAPrivateKey) pair.getPrivate()));

        var error =
                assertThrows(ApiException.class, () -> codec.parse("Bearer " + token.serialize()));

        assertEquals(401, error.status);
        assertEquals("AUTH_INVALID", error.code);
    }
}
