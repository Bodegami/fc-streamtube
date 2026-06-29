package com.fcstreamtube.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@Import(SecurityConfigIT.MeStubController.class)
class SecurityConfigIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    MockMvc mockMvc;

    @Value("${app.jwt.secret}")
    String jwtSecret;

    @Test
    void givenNoAuthCookie_whenGetApiMe_thenReturns401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void givenValidJwtInCookie_whenGetApiMe_thenReturns200() throws Exception {
        mockMvc.perform(get("/api/me")
                        .cookie(new Cookie(SecurityConstants.ACCESS_TOKEN_COOKIE, buildValidJwt())))
                .andExpect(status().isOk());
    }

    @Test
    void givenExpiredJwtInCookie_whenGetApiMe_thenReturns401() throws Exception {
        mockMvc.perform(get("/api/me")
                        .cookie(new Cookie(SecurityConstants.ACCESS_TOKEN_COOKIE, buildExpiredJwt())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void givenJwtWithWrongSignatureInCookie_whenGetApiMe_thenReturns401() throws Exception {
        mockMvc.perform(get("/api/me")
                        .cookie(new Cookie(SecurityConstants.ACCESS_TOKEN_COOKIE, buildJwtWithWrongKey())))
                .andExpect(status().isUnauthorized());
    }

    private String buildValidJwt() throws Exception {
        return buildJwt(jwtSecret, Instant.now().plusSeconds(3600));
    }

    private String buildExpiredJwt() throws Exception {
        return buildJwt(jwtSecret, Instant.now().minusSeconds(3600));
    }

    private String buildJwtWithWrongKey() throws Exception {
        return buildJwt("wrong-key-that-does-not-match-the-configured-secret!", Instant.now().plusSeconds(3600));
    }

    private String buildJwt(String secret, Instant expiresAt) throws Exception {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test-user-id")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJwt.sign(new MACSigner(key));
        return signedJwt.serialize();
    }

    @RestController
    static class MeStubController {
        @GetMapping("/api/me")
        ResponseEntity<String> me() {
            return ResponseEntity.ok("authenticated");
        }
    }
}
