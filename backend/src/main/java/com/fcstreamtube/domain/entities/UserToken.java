package com.fcstreamtube.domain.entities;

import java.time.Instant;
import java.util.UUID;

public record UserToken(
        UUID id,
        UUID userId,
        String tokenValue,
        UserTokenType type,
        Instant expiresAt,
        Instant usedAt
) {}
