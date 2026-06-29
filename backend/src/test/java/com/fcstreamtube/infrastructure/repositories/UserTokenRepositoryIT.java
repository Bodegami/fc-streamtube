package com.fcstreamtube.infrastructure.repositories;

import com.fcstreamtube.domain.entities.User;
import com.fcstreamtube.domain.entities.UserToken;
import com.fcstreamtube.domain.entities.UserTokenType;
import com.fcstreamtube.domain.repositories.UserRepository;
import com.fcstreamtube.domain.repositories.UserTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class UserTokenRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserTokenRepository userTokenRepository;

    @Test
    void givenValidToken_whenPersisted_thenUuidIsGeneratedAutomatically() {
        User user = userRepository.save(new User(null, "Frank", "frank-" + UUID.randomUUID() + "@test.com", "hashed", false, null));
        UserToken token = new UserToken(null, user.id(), UUID.randomUUID().toString(),
                UserTokenType.EMAIL_CONFIRMATION, Instant.now().plus(1, ChronoUnit.DAYS), null);

        UserToken saved = userTokenRepository.save(token);

        assertThat(saved.id()).isNotNull();
    }

    @Test
    void givenPersistedToken_whenFindByToken_thenReturnsToken() {
        User user = userRepository.save(new User(null, "Carol", "carol-" + UUID.randomUUID() + "@test.com", "hashed", false, null));
        String opaqueToken = UUID.randomUUID().toString();
        UserToken token = new UserToken(null, user.id(), opaqueToken,
                UserTokenType.EMAIL_CONFIRMATION, Instant.now().plus(1, ChronoUnit.DAYS), null);

        userTokenRepository.save(token);

        Optional<UserToken> found = userTokenRepository.findByToken(opaqueToken);
        assertThat(found).isPresent();
        assertThat(found.get().tokenValue()).isEqualTo(opaqueToken);
    }

    @Test
    void givenNoToken_whenFindByTokenWithUnknownValue_thenReturnsEmpty() {
        Optional<UserToken> found = userTokenRepository.findByToken("nonexistent-token");

        assertThat(found).isEmpty();
    }

    @Test
    void givenPersistedToken_whenDeleteByUserIdAndType_thenTokenIsRemoved() {
        User user = userRepository.save(new User(null, "Eve", "eve-" + UUID.randomUUID() + "@test.com", "hashed", false, null));
        String opaqueToken = UUID.randomUUID().toString();
        userTokenRepository.save(new UserToken(null, user.id(), opaqueToken,
                UserTokenType.EMAIL_CONFIRMATION, Instant.now().plus(1, ChronoUnit.DAYS), null));

        userTokenRepository.deleteByUserIdAndType(user.id(), UserTokenType.EMAIL_CONFIRMATION);

        assertThat(userTokenRepository.findByToken(opaqueToken)).isEmpty();
    }
}
