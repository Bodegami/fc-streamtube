package com.fcstreamtube.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthCookieHelper {

    private static final String COOKIE_NAME = SecurityConstants.ACCESS_TOKEN_COOKIE;
    private final Duration tokenMaxAge;

    public AuthCookieHelper(@Value("${app.jwt.expiration-seconds}") long expirationSeconds) {
        this.tokenMaxAge = Duration.ofSeconds(expirationSeconds);
    }

    public ResponseCookie buildSetCookie(String jwtToken) {
        return ResponseCookie.from(COOKIE_NAME, jwtToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(tokenMaxAge)
                .build();
    }

    public ResponseCookie buildClearCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}
