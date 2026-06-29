package com.fcstreamtube.infrastructure.persistence.repositories;

import com.fcstreamtube.domain.entities.User;
import com.fcstreamtube.domain.repositories.UserRepository;
import com.fcstreamtube.infrastructure.persistence.mappers.UserMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final SpringUserJpaRepository jpaRepository;
    private final UserMapper mapper;

    public UserRepositoryAdapter(SpringUserJpaRepository jpaRepository, UserMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public User save(User user) {
        return mapper.toDomain(jpaRepository.save(mapper.toJpa(user)));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }
}
