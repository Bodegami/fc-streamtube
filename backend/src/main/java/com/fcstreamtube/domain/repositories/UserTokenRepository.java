package com.fcstreamtube.domain.repositories;

import com.fcstreamtube.domain.entities.UserToken;
import com.fcstreamtube.domain.entities.UserTokenType;

import java.util.Optional;
import java.util.UUID;

public interface UserTokenRepository {
    UserToken save(UserToken token);
    Optional<UserToken> findByToken(String token);
    void deleteByUserIdAndType(UUID userId, UserTokenType type);
}
