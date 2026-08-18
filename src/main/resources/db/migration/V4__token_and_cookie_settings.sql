CREATE TABLE server_settings (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    oauth_session_cookie_name TEXT NOT NULL DEFAULT 'SESSIONID',
    oauth_session_lifetime_seconds INTEGER NOT NULL DEFAULT 28800,
    default_access_token_lifetime_seconds INTEGER NOT NULL DEFAULT 3600,
    default_refresh_token_lifetime_seconds INTEGER NOT NULL DEFAULT 604800,
    session_cookie_secure BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO server_settings (id) VALUES (1);

ALTER TABLE clients
    ADD COLUMN access_token_lifetime_seconds INTEGER,
    ADD COLUMN refresh_token_lifetime_seconds INTEGER,
    ADD COLUMN session_lifetime_seconds INTEGER;
