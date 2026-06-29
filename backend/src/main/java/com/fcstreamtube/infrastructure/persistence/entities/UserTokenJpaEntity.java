package com.fcstreamtube.infrastructure.persistence.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_tokens")
public class UserTokenJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserTokenJpaEntity() {}

    public UserTokenJpaEntity(UUID id, UUID userId, String token, String type, Instant expiresAt, Instant usedAt) {
        this.id = id;
        this.userId = userId;
        this.token = token;
        this.type = type;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getToken() { return token; }
    public String getType() { return type; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
}
