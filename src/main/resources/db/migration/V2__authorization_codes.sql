CREATE TABLE authorization_codes (
    code TEXT PRIMARY KEY,
    client_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    redirect_uri TEXT NOT NULL,
    scopes JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ
);

CREATE INDEX idx_authorization_codes_client_id ON authorization_codes (client_id);
CREATE INDEX idx_authorization_codes_expires_at ON authorization_codes (expires_at);
