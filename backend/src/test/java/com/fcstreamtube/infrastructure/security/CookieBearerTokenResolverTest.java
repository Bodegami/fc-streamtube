package com.fcstreamtube.infrastructure.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class CookieBearerTokenResolverTest {

    private final CookieBearerTokenResolver resolver = new CookieBearerTokenResolver();

    @Test
    void givenNoCookies_whenResolve_thenReturnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(resolver.resolve(request)).isNull();
    }

    @Test
    void givenAccessTokenCookie_whenResolve_thenReturnsValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SecurityConstants.ACCESS_TOKEN_COOKIE, "jwt.token.here"));
        assertThat(resolver.resolve(request)).isEqualTo("jwt.token.here");
    }

    @Test
    void givenAccessTokenCookieWithBlankValue_whenResolve_thenReturnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SecurityConstants.ACCESS_TOKEN_COOKIE, "   "));
        assertThat(resolver.resolve(request)).isNull();
    }

    @Test
    void givenOtherCookieOnly_whenResolve_thenReturnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other_cookie", "somevalue"));
        assertThat(resolver.resolve(request)).isNull();
    }

    @Test
    void givenAccessTokenIsNotFirstCookie_whenResolve_thenReturnsValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie("session_id", "abc123"),
                new Cookie(SecurityConstants.ACCESS_TOKEN_COOKIE, "jwt.token.here")
        );
        assertThat(resolver.resolve(request)).isEqualTo("jwt.token.here");
    }
}
