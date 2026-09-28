CREATE TABLE administrator_users (
    id                 UUID PRIMARY KEY,
    username           VARCHAR(128) NOT NULL UNIQUE,
    email              VARCHAR(256) NOT NULL UNIQUE,
    password_hash      VARCHAR(255) NOT NULL,
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    is_locked          BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_count INTEGER      NOT NULL DEFAULT 0,
    last_login_at      TIMESTAMPTZ  NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_admin_users_login_identifier ON administrator_users (lower(username), lower(email));

CREATE TABLE auth_sessions (
    id               UUID PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES administrator_users (id) ON DELETE CASCADE,
    issued_at        TIMESTAMPTZ  NOT NULL,
    expires_at       TIMESTAMPTZ  NOT NULL,
    client_device_id VARCHAR(255) NULL,
    client_ip        INET         NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_auth_sessions_user_id ON auth_sessions (user_id);
CREATE INDEX ix_auth_sessions_expires_at ON auth_sessions (expires_at);

CREATE TABLE admin_auth_audit_log (
    id                BIGSERIAL PRIMARY KEY,
    user_id           UUID         NULL REFERENCES administrator_users (id),
    username_or_email VARCHAR(256) NOT NULL,
    client_device_id  VARCHAR(255) NULL,
    client_ip         INET         NULL,
    success           BOOLEAN      NOT NULL,
    reason_code       VARCHAR(64)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
