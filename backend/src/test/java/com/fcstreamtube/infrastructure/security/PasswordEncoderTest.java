package com.fcstreamtube.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTest {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    void givenRawPassword_whenEncoded_thenMatchesViaEncoder() {
        String raw = "mySecretPassword";
        String encoded = encoder.encode(raw);
        assertThat(encoder.matches(raw, encoded)).isTrue();
    }

    @Test
    void givenEncodedPassword_whenInspected_thenHasBcryptPrefix() {
        String encoded = encoder.encode("anyPassword");
        assertThat(encoded).startsWith("{bcrypt}");
    }

    @Test
    void givenWrongPassword_whenMatches_thenReturnsFalse() {
        String encoded = encoder.encode("correctPassword");
        assertThat(encoder.matches("wrongPassword", encoded)).isFalse();
    }
}
