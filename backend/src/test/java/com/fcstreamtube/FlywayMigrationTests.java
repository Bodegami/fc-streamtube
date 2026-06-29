package com.fcstreamtube;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class FlywayMigrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void givenBlankDatabase_whenApplicationStarts_thenFlywaySchemaHistoryTableExists() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'flyway_schema_history'",
            Integer.class
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void givenBlankDatabase_whenApplicationStarts_thenV1MigrationIsApplied() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = true",
            Integer.class
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void givenAllMigrations_whenApplicationStarts_thenAllDomainTablesExist() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables " +
            "WHERE table_schema = 'public' AND table_name != 'flyway_schema_history'",
            Integer.class
        );
        assertThat(count).isEqualTo(2); // users + user_tokens (V2, V3)
    }
}
