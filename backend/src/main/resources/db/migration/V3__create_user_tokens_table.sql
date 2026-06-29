CREATE TABLE user_tokens (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL,
    token      VARCHAR(255) NOT NULL,
    type       VARCHAR(32)  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_user_tokens PRIMARY KEY (id),
    CONSTRAINT uq_user_tokens_token UNIQUE (token),
    CONSTRAINT fk_user_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_user_tokens_user_type ON user_tokens (user_id, type);
