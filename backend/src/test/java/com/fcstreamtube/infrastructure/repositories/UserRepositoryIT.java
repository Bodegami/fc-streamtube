package com.fcstreamtube.infrastructure.repositories;

import com.fcstreamtube.domain.entities.User;
import com.fcstreamtube.domain.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class UserRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    UserRepository userRepository;

    @Test
    void givenValidUser_whenPersisted_thenUuidIsGeneratedAutomatically() {
        User user = new User(null, "Alice", "alice-" + UUID.randomUUID() + "@test.com", "hashed", false, null);

        User saved = userRepository.save(user);

        assertThat(saved.id()).isNotNull();
    }

    @Test
    void givenValidUser_whenPersisted_thenCreatedAtIsPopulatedByDatabase() {
        User user = new User(null, "Alice", "alice-" + UUID.randomUUID() + "@test.com", "hashed", false, null);

        User saved = userRepository.save(user);

        assertThat(saved.createdAt()).isNotNull();
    }

    @Test
    void givenPersistedUser_whenFindByEmail_thenReturnsUser() {
        String email = "bob-" + UUID.randomUUID() + "@test.com";
        userRepository.save(new User(null, "Bob", email, "hashed", false, null));

        Optional<User> found = userRepository.findByEmail(email);

        assertThat(found).isPresent();
        assertThat(found.get().email()).isEqualTo(email);
    }

    @Test
    void givenNoUser_whenFindByEmailWithUnknownAddress_thenReturnsEmpty() {
        Optional<User> found = userRepository.findByEmail("nobody-" + UUID.randomUUID() + "@test.com");

        assertThat(found).isEmpty();
    }

    @Test
    void givenPersistedUser_whenExistsByEmail_thenReturnsTrue() {
        String email = "dave-" + UUID.randomUUID() + "@test.com";
        userRepository.save(new User(null, "Dave", email, "hashed", false, null));

        assertThat(userRepository.existsByEmail(email)).isTrue();
    }

    @Test
    void givenNoUser_whenExistsByEmailWithUnknownAddress_thenReturnsFalse() {
        assertThat(userRepository.existsByEmail("ghost-" + UUID.randomUUID() + "@test.com")).isFalse();
    }
}
