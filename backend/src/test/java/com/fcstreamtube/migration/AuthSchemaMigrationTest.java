package com.fcstreamtube.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class AuthSchemaMigrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void givenCleanDatabase_whenMigrationsApplied_thenUsersTableExists() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'users'",
            Integer.class
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void givenCleanDatabase_whenMigrationsApplied_thenUserTokensTableExists() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'user_tokens'",
            Integer.class
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void givenTwoUsersWithSameEmail_whenInserted_thenUniqueConstraintViolated() {
        String email = "dup-" + UUID.randomUUID() + "@test.com";
        jdbcTemplate.update(
            "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)",
            "User One", email, "hash1"
        );
        assertThatThrownBy(() ->
            jdbcTemplate.update(
                "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)",
                "User Two", email, "hash2"
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void givenUserInsertedWithoutEmailConfirmed_whenQueried_thenDefaultIsFalse() {
        String email = "default-" + UUID.randomUUID() + "@test.com";
        jdbcTemplate.update(
            "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)",
            "Test User", email, "hash"
        );
        Boolean confirmed = jdbcTemplate.queryForObject(
            "SELECT email_confirmed FROM users WHERE email = ?",
            Boolean.class, email
        );
        assertThat(confirmed).isFalse();
    }
}
