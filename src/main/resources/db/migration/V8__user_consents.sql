CREATE TABLE user_consents (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    client_id TEXT NOT NULL REFERENCES clients (client_id) ON DELETE CASCADE,
    scopes JSONB NOT NULL DEFAULT '[]',
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, client_id)
);

CREATE INDEX idx_user_consents_client_id ON user_consents (client_id);
