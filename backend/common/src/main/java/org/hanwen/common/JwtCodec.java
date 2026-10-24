package org.hanwen.common;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.time.*;
import java.util.*;

public class JwtCodec {
    private final NimbusJwtDecoder decoder;
    private final RSAPublicKey publicKey;

    public JwtCodec(String path) throws Exception {
        publicKey =
                (RSAPublicKey)
                        KeyFactory.getInstance("RSA")
                                .generatePublic(new X509EncodedKeySpec(pem(path)));
        decoder =
                NimbusJwtDecoder.withPublicKey(publicKey)
                        .signatureAlgorithm(SignatureAlgorithm.RS256)
                        .build();
        var audience =
                new JwtClaimValidator<List<String>>("aud", a -> a.contains("hanwen-services"));
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer("hanwen-user"), audience));
    }

    private static byte[] pem(String path) throws Exception {
        return Base64.getDecoder()
                .decode(
                        Files.readString(Path.of(path))
                                .replaceAll("-----[^-]+-----", "")
                                .replaceAll("\\s", ""));
    }

    public Actor parse(String auth) {
        try {
            if (auth == null || !auth.startsWith("Bearer ")) throw new IllegalArgumentException();
            Jwt j = decoder.decode(auth.substring(7));
            String subject = j.getSubject();
            if (subject == null || !(j.getClaim("ver") instanceof Number version)) {
                throw new IllegalArgumentException("缺少有效的身份编号或版本");
            }
            return new Actor(
                    Long.parseLong(subject), j.getClaimAsString("role"), version.intValue());
        } catch (Exception e) {
            throw new ApiException(401, "AUTH_INVALID", "登录已失效，请重新登录");
        }
    }

    public String issue(long id, String role, int version, String privatePath) throws Exception {
        return issue(id, role, version, privatePath, Instant.now().plusSeconds(900));
    }

    public String issue(long id, String role, int version, String privatePath, Instant deadline)
            throws Exception {
        var key =
                (RSAPrivateKey)
                        KeyFactory.getInstance("RSA")
                                .generatePrivate(new PKCS8EncodedKeySpec(pem(privatePath)));
        var encoder =
                new NimbusJwtEncoder(
                        new ImmutableJWKSet<>(
                                new JWKSet(
                                        new com.nimbusds.jose.jwk.RSAKey.Builder(publicKey)
                                                .privateKey(key)
                                                .build())));
        Instant now = Instant.now();
        // 续期不能突破本次登录的总时限，即使新令牌通常可用十五分钟。
        Instant expiry = deadline.isBefore(now.plusSeconds(900)) ? deadline : now.plusSeconds(900);
        if (!expiry.isAfter(now)) throw new ApiException(401, "SESSION_EXPIRED", "本次入院已到期，请重新登录");
        var claims =
                JwtClaimsSet.builder()
                        .issuer("hanwen-user")
                        .audience(List.of("hanwen-services"))
                        .subject(Long.toString(id))
                        .issuedAt(now)
                        .id(UUID.randomUUID().toString())
                        .expiresAt(expiry)
                        .claim("role", role)
                        .claim("ver", version)
                        .build();
        return encoder.encode(
                        JwtEncoderParameters.from(
                                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }
}
