package com.fcstreamtube.infrastructure.persistence.repositories;

import com.fcstreamtube.infrastructure.persistence.entities.UserTokenJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringUserTokenJpaRepository extends JpaRepository<UserTokenJpaEntity, UUID> {
    Optional<UserTokenJpaEntity> findByToken(String token);
    void deleteByUserIdAndType(UUID userId, String type);
}
