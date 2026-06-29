package com.fcstreamtube.domain.entities;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID id,
        String name,
        String email,
        String passwordHash,
        boolean emailConfirmed,
        Instant createdAt
) {}
