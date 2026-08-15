CREATE TABLE clients (
    id UUID PRIMARY KEY,
    client_id TEXT NOT NULL UNIQUE,
    client_secret_hash TEXT NOT NULL,
    client_name TEXT NOT NULL,
    redirect_uris JSONB NOT NULL,
    scopes JSONB NOT NULL,
    grant_types JSONB NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    client_logo TEXT,
    client_description TEXT
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    email TEXT,
    roles JSONB NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clients_client_id ON clients (client_id);
CREATE INDEX idx_users_username ON users (username);
