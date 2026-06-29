package com.fcstreamtube.infrastructure.persistence.mappers;

import com.fcstreamtube.domain.entities.User;
import com.fcstreamtube.infrastructure.persistence.entities.UserJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserJpaEntity toJpa(User user) {
        return new UserJpaEntity(user.id(), user.name(), user.email(), user.passwordHash(), user.emailConfirmed());
    }

    public User toDomain(UserJpaEntity entity) {
        return new User(entity.getId(), entity.getName(), entity.getEmail(), entity.getPasswordHash(), entity.isEmailConfirmed(), entity.getCreatedAt());
    }
}
