-- Application metadata schema: users, model configs, conversations and messages.
-- Portable across MySQL 8 and H2 (MODE=MySQL) so tests run without a MySQL server.
-- Ids are app-generated UUID strings (R2DBC has no portable generated-key API), and timestamps are
-- epoch-millis BIGINTs for consistent, precise ordering.
-- Executed by docker-compose init (MySQL) and, for tests, via spring.sql.init (H2).

CREATE TABLE IF NOT EXISTS app_user (
    id            VARCHAR(64)  PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100),
    -- Role name: 'admin' sees every conversation, 'user' only its own rows. Assigned out of band
    -- (seed SQL or a direct update); self-service registration always inserts 'user'.
    role          VARCHAR(32)  NOT NULL DEFAULT 'user',
    created_at    BIGINT       NOT NULL,
    CONSTRAINT uk_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS model_config (
    id         VARCHAR(64)  PRIMARY KEY,
    user_id    VARCHAR(64)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    provider   VARCHAR(32)  NOT NULL,
    model      VARCHAR(128) NOT NULL,
    api_key    VARCHAR(512),
    base_url   VARCHAR(512),
    is_default BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at BIGINT       NOT NULL
);

CREATE TABLE IF NOT EXISTS conversation (
    id              VARCHAR(64)  PRIMARY KEY,
    user_id         VARCHAR(64)  NOT NULL,
    title           VARCHAR(255) NOT NULL,
    datasource_id   VARCHAR(64),
    model_config_id VARCHAR(64),
    created_at      BIGINT       NOT NULL,
    updated_at      BIGINT       NOT NULL
);

CREATE TABLE IF NOT EXISTS message (
    id              VARCHAR(64) NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    role            VARCHAR(16) NOT NULL,
    content         TEXT,
    sql_text        TEXT,
    chart_json      TEXT,
    rows_json       TEXT,
    created_at      BIGINT      NOT NULL
);
