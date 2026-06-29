package com.fcstreamtube.domain.repositories;

import com.fcstreamtube.domain.entities.User;

import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
