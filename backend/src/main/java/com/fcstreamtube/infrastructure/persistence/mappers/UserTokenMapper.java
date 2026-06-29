package com.fcstreamtube.infrastructure.persistence.mappers;

import com.fcstreamtube.domain.entities.UserToken;
import com.fcstreamtube.domain.entities.UserTokenType;
import com.fcstreamtube.infrastructure.persistence.entities.UserTokenJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class UserTokenMapper {

    public UserTokenJpaEntity toJpa(UserToken token) {
        return new UserTokenJpaEntity(
                token.id(),
                token.userId(),
                token.tokenValue(),
                token.type().name(),
                token.expiresAt(),
                token.usedAt()
        );
    }

    public UserToken toDomain(UserTokenJpaEntity entity) {
        UserTokenType type;
        try {
            type = UserTokenType.valueOf(entity.getType());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Unknown UserTokenType in database: '" + entity.getType() + "'", e);
        }
        return new UserToken(
                entity.getId(),
                entity.getUserId(),
                entity.getToken(),
                type,
                entity.getExpiresAt(),
                entity.getUsedAt()
        );
    }
}
