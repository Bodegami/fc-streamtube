package com.fcstreamtube.infrastructure.persistence.repositories;

import com.fcstreamtube.domain.entities.UserToken;
import com.fcstreamtube.domain.entities.UserTokenType;
import com.fcstreamtube.domain.repositories.UserTokenRepository;
import com.fcstreamtube.infrastructure.persistence.mappers.UserTokenMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public class UserTokenRepositoryAdapter implements UserTokenRepository {

    private final SpringUserTokenJpaRepository jpaRepository;
    private final UserTokenMapper mapper;

    public UserTokenRepositoryAdapter(SpringUserTokenJpaRepository jpaRepository, UserTokenMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public UserToken save(UserToken token) {
        return mapper.toDomain(jpaRepository.save(mapper.toJpa(token)));
    }

    @Override
    public Optional<UserToken> findByToken(String token) {
        return jpaRepository.findByToken(token).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteByUserIdAndType(UUID userId, UserTokenType type) {
        jpaRepository.deleteByUserIdAndType(userId, type.name());
    }
}
