CREATE TABLE user_clients (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    client_id TEXT NOT NULL REFERENCES clients (client_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, client_id)
);

CREATE INDEX idx_user_clients_client_id ON user_clients (client_id);
